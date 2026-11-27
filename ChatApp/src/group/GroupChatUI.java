package group;

import chat.ClientSocket;
import chat.MessageListener;
import history.HistoryManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GroupChatUI extends JFrame implements MessageListener.PacketHandler {

    private final String username;
    private final Group group;

    private ClientSocket clientSocket;
    private MessageListener messageListener;
    private final HistoryManager historyManager = HistoryManager.getInstance();

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel statusLabel;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public GroupChatUI(String username, Group group) {
        this.username = username;
        this.group = group;

        initializeUI();
        loadHistory();
        connectToServer();
    }

    private void initializeUI() {
        setTitle("Group Chat - " + group.getGroupName());
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Group header
        JPanel headerPanel = new JPanel(new BorderLayout());

        JLabel titleLabel = new JLabel(
                group.getGroupName(),
                SwingConstants.CENTER
        );
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));

        statusLabel = new JLabel("Disconnected", SwingConstants.CENTER);

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);
        headerPanel.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );

        add(headerPanel, BorderLayout.NORTH);

        // Chat display
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));

        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Members panel
        JTextArea membersArea = new JTextArea();
        membersArea.setEditable(false);
        membersArea.setFont(new Font("Arial", Font.PLAIN, 12));

        membersArea.append("Members (" + group.getMemberCount() + ")\n");

        for (String member : group.getMembers()) {
            membersArea.append("- " + member + "\n");
        }

        JScrollPane membersScroll = new JScrollPane(membersArea);
        membersScroll.setPreferredSize(new Dimension(150, 0));

        add(membersScroll, BorderLayout.EAST);

        // Message input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));

        messageField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.setBorder(
                BorderFactory.createEmptyBorder(5, 10, 10, 10)
        );

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
            setConnectedUI(false);
            return;
        }

        String loginPacket = "{\"type\":\"LOGIN\",\"username\":\""
                + escapeJson(username) + "\"}";

        clientSocket.sendPacket(loginPacket);

        messageListener = new MessageListener(clientSocket, this);
        messageListener.start();

        statusLabel.setText("Connected");
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

        String packet = "{\"type\":\"GROUP_MESSAGE\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"groupId\":\"" + escapeJson(group.getGroupId()) + "\","
                + "\"content\":\"" + escapeJson(content) + "\"}";

        clientSocket.sendPacket(packet);

        String time = LocalDateTime.now().format(TIME_FORMAT);

        appendMessage(username + " [" + time + "]: " + content);
        historyManager.saveGroupMessage(username, group.getGroupId(), content, time);

        messageField.setText("");
    }

    @Override
    public void onPacketReceived(String packet) {
        String type = extractJsonValue(packet, "type");
        String sender = extractJsonValue(packet, "sender");
        String groupId = extractJsonValue(packet, "groupId");
        String content = extractJsonValue(packet, "content");

        if ("GROUP_MESSAGE".equals(type)
                && groupId != null
                && groupId.equals(group.getGroupId())
                && sender != null
                && content != null
                && !sender.equals(username)) {

            String time = LocalDateTime.now().format(TIME_FORMAT);

            String displayMessage =
                    sender + " [" + time + "]: " + content;

            historyManager.saveGroupMessage(sender, group.getGroupId(), content, time);

            SwingUtilities.invokeLater(
                    () -> appendMessage(displayMessage)
            );
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Disconnected");
            appendMessage("[System] Disconnected from server.");
            setConnectedUI(false);
        });
    }

    private void setConnectedUI(boolean connected) {
        sendButton.setEnabled(connected);
        messageField.setEnabled(connected);
    }

    private void loadHistory() {
        String text = historyManager.formatHistory(
                historyManager.getGroupHistory(group.getGroupId())
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
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);

        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());

        if (colonIndex == -1) {
            return null;
        }

        int start = json.indexOf('"', colonIndex + 1);

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
}