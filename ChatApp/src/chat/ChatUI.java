package chat;

import history.HistoryManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatUI extends JFrame implements MessageListener.PacketHandler {

    private final String username;
    private final String receiver;

    private ClientSocket clientSocket;
    private MessageListener messageListener;
    private final HistoryManager historyManager = HistoryManager.getInstance();

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;

    public ChatUI(String username, String receiver) {
        this.username = username;
        this.receiver = receiver;

        initializeUI();
        loadHistory();
        connectToServer();
    }

    private void initializeUI() {
        setTitle("Chat - " + username + " with " + receiver);
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Header
        JLabel header = new JLabel(
                "Chat with " + receiver,
                SwingConstants.CENTER
        );
        header.setFont(new Font("Arial", Font.BOLD, 18));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(header, BorderLayout.NORTH);

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));

        messageField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());

        messageField.addActionListener(e -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        setVisible(true);
    }

    private void connectToServer() {
        clientSocket = new ClientSocket("localhost", 8080);

        if (!clientSocket.connect()) {
            appendMessage("[System] Cannot connect to server.");
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
            return;
        }

        // Send LOGIN packet
        String loginPacket = "{\"type\":\"LOGIN\",\"username\":\""
                + escapeJson(username) + "\"}";

        clientSocket.sendPacket(loginPacket);

        messageListener = new MessageListener(clientSocket, this);
        messageListener.start();

        appendMessage("[System] Connected to server.");
    }

    private void sendMessage() {
        String content = messageField.getText().trim();

        if (content.isEmpty()) {
            return;
        }

        if (clientSocket == null || !clientSocket.isConnected()) {
            appendMessage("[System] Not connected to server.");
            return;
        }

        String packet = "{\"type\":\"MESSAGE\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"receiver\":\"" + escapeJson(receiver) + "\","
                + "\"content\":\"" + escapeJson(content) + "\"}";

        clientSocket.sendPacket(packet);

        String time = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );

        appendMessage(username + " [" + time + "]: " + content);
        historyManager.savePrivateMessage(username, receiver, content, time);

        messageField.setText("");
    }

    @Override
    public void onPacketReceived(String packet) {
        String type = extractJsonValue(packet, "type");
        String sender = extractJsonValue(packet, "sender");
        String content = extractJsonValue(packet, "content");
        String packetReceiver = extractJsonValue(packet, "receiver");

        // Only display relevant chat messages
        if ("MESSAGE".equals(type)
                && username.equals(packetReceiver)
                && sender != null
                && sender.equals(receiver)
                && content != null) {

            String time = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );

            String displayMessage = sender + " [" + time + "]: " + content;

            historyManager.savePrivateMessage(sender, username, content, time);

            SwingUtilities.invokeLater(() -> appendMessage(displayMessage));
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            appendMessage("[System] Disconnected from server.");
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
        });
    }

    private void loadHistory() {
        String text = historyManager.formatHistory(
                historyManager.getPrivateHistory(username, receiver)
        );

        if (!text.isEmpty()) {
            chatArea.append(text);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    private void appendMessage(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void disconnect() {
        if (messageListener != null) {
            messageListener.stop();
        } else if (clientSocket != null) {
            clientSocket.close();
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);

        if (keyIndex == -1) {
            // Also handle JSON with spaces before colon
            searchKey = "\"" + key + "\"";
            keyIndex = json.indexOf(searchKey);

            if (keyIndex == -1) {
                return null;
            }

            int colonIndex = json.indexOf(':', keyIndex + searchKey.length());

            if (colonIndex == -1) {
                return null;
            }

            keyIndex = colonIndex;
        }

        int start = json.indexOf('"', keyIndex + searchKey.length());

        if (start == -1) {
            start = json.indexOf('"', keyIndex + 1);
        }

        if (start == -1) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    default:
                        result.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }

        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(
                    null, "Enter your username:"
            );

            if (username == null || username.trim().isEmpty()) {
                return;
            }

            String receiver = JOptionPane.showInputDialog(
                    null, "Chat with which user?"
            );

            if (receiver == null || receiver.trim().isEmpty()) {
                return;
            }

            new ChatUI(username.trim(), receiver.trim());
        });
    }
}