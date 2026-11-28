package chat;

import emoji.EmojiPickerPopup;
import emoji.EmojiTextRenderer;
import history.HistoryManager;
import media.FileInfo;
import media.MediaManager;
import model.PacketType;
import ui.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatUI extends JPanel implements MessageListener.PacketHandler {

    private final String username;
    private final String receiver;

    private ClientSocket clientSocket;
    private MessageListener messageListener;
    private MediaManager mediaManager;
    private final HistoryManager historyManager = HistoryManager.getInstance();

    private JPanel messagesPanel;
    private JScrollPane messageScrollPane;
    private JTextField messageField;
    private JButton sendButton;
    private JButton attachButton;
    private JButton mediaButton;
    private JButton emojiButton;
    private EmojiPickerPopup emojiPicker;
    private boolean conversationClosed;

    public ChatUI(String username, String receiver) {
        this.username = username;
        this.receiver = receiver;

        initializeUI();
        loadHistory();
        connectToServer();
    }

    private void initializeUI() {
        setBackground(AppTheme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
        ));
        JLabel title = new JLabel(receiver);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(AppTheme.TEXT);
        JLabel subtitle = new JLabel("Cuộc trò chuyện riêng");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(AppTheme.MUTED);
        JLabel avatar = new JLabel(new chat.ChatHomeUI.AvatarIcon(receiver, 42));
        avatar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        header.add(avatar, BorderLayout.WEST);
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 3));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);
        header.add(titlePanel, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(AppTheme.CHAT_BACKGROUND);
        messagesPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        messageScrollPane = new JScrollPane(messagesPanel);
        messageScrollPane.setBorder(BorderFactory.createEmptyBorder());
        messageScrollPane.getViewport().setBackground(AppTheme.CHAT_BACKGROUND);
        messageScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(messageScrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBackground(AppTheme.SURFACE);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        messageField = new JTextField();
        AppTheme.styleTextField(messageField);
        messageField.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        sendButton = new JButton("Gửi");
        attachButton = new JButton("Tệp");
        mediaButton = new JButton("Media (0)");
        emojiButton = new JButton("Emoji");
        attachButton.setEnabled(false);
        mediaButton.setEnabled(false);
        AppTheme.styleButton(sendButton, true);
        AppTheme.styleButton(attachButton, false);
        AppTheme.styleButton(mediaButton, false);
        AppTheme.styleButton(emojiButton, false);
        emojiPicker = new EmojiPickerPopup((symbol, sticker) -> {
            if (sticker) {
                sendSticker(symbol);
            } else {
                insertEmoji(symbol);
            }
        });

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actionsPanel.setOpaque(false);
        actionsPanel.add(mediaButton);
        actionsPanel.add(attachButton);
        actionsPanel.add(emojiButton);
        actionsPanel.add(sendButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(actionsPanel, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        attachButton.addActionListener(e -> chooseAndSendMedia());
        emojiButton.addActionListener(e -> emojiPicker.showAt(emojiButton));
        mediaButton.addActionListener(e ->
                MediaManager.chooseAndOpenMedia(this, getConversationMedia()));

        messageField.addActionListener(e -> sendMessage());

    }

    private void connectToServer() {
        clientSocket = new ClientSocket("localhost", 8080);

        if (!clientSocket.connect()) {
            appendMessage("[System] Cannot connect to server.");
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
            attachButton.setEnabled(false);
            emojiButton.setEnabled(false);
            return;
        }

        // Send LOGIN packet
        String loginPacket = "{\"type\":\"LOGIN\",\"username\":\""
                + escapeJson(username) + "\"}";

        clientSocket.sendPacket(loginPacket);

        mediaManager = new MediaManager(clientSocket, username, new MediaManager.MediaListener() {
            @Override
            public void onMediaReceived(FileInfo info) {
                historyManager.saveMedia(info);
                SwingUtilities.invokeLater(() -> showMedia(info, " received"));
            }

            @Override
            public void onMediaSent(FileInfo info) {
                historyManager.saveMedia(info);
                SwingUtilities.invokeLater(() -> showMedia(info, " sent"));
            }

            @Override
            public void onMediaError(String message) {
                SwingUtilities.invokeLater(() -> appendMessage("[Media error] " + message));
            }
        });

        messageListener = new MessageListener(clientSocket, this);
        messageListener.start();
        attachButton.setEnabled(true);
        emojiButton.setEnabled(true);

        appendMessage("[System] Connected to server.");
    }

    private void chooseAndSendMedia() {
        java.io.File file = MediaManager.chooseFile(this);
        if (file != null && mediaManager != null) {
            mediaManager.sendMedia(receiver, null, file);
        }
    }

    private void showMedia(FileInfo info, String action) {
        String verb = action.contains("sent") ? "Đã gửi: " : "Đã nhận: ";
        appendChatMessage(info.getSender(), verb + info.getFilename(), info.getTime());
        updateMediaButton();
    }

    private List<FileInfo> getConversationMedia() {
        List<FileInfo> files = new ArrayList<FileInfo>();
        for (Message message : historyManager.getPrivateHistory(username, receiver)) {
            PacketType type = MediaManager.toMediaType(message.getType());
            if (type != null && message.getFilepath() != null
                    && !message.getFilepath().trim().isEmpty()) {
                files.add(new FileInfo(type, message.getContent(), 0,
                        message.getFilepath(), message.getSender(),
                        message.getReceiver(), message.getGroupId(), message.getTime()));
            }
        }
        return files;
    }

    private void updateMediaButton() {
        int count = getConversationMedia().size();
        mediaButton.setText("Media (" + count + ")");
        mediaButton.setEnabled(count > 0);
    }

    private void sendMessage() {
        sendContent(messageField.getText(), true);
    }

    private void sendSticker(String sticker) {
        sendContent(sticker, false);
    }

    private void insertEmoji(String emoji) {
        messageField.replaceSelection(emoji);
        messageField.requestFocusInWindow();
    }

    private void sendContent(String rawContent, boolean clearInput) {
        String content = rawContent == null ? "" : rawContent.trim();
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

        appendChatMessage(username, content, time);
        historyManager.savePrivateMessage(username, receiver, content, time);

        if (clearInput) {
            messageField.setText("");
        }
    }

    @Override
    public void onPacketReceived(String packet) {
        if (mediaManager != null && mediaManager.handlePacket(packet)) {
            return;
        }

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

            historyManager.savePrivateMessage(sender, username, content, time);

            SwingUtilities.invokeLater(() -> appendChatMessage(sender, content, time));
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            appendMessage("[System] Disconnected from server.");
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
            attachButton.setEnabled(false);
            emojiButton.setEnabled(false);
        });
    }

    private void loadHistory() {
        for (Message message : historyManager.getPrivateHistory(username, receiver)) {
            String content = message.getContent();
            if (message.getFilepath() != null
                    && !message.getFilepath().trim().isEmpty()) {
                content = "Tệp: " + content;
            }
            appendChatMessage(message.getSender(), content, message.getTime());
        }
        updateMediaButton();
    }

    private void appendMessage(String message) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        row.setOpaque(false);
        JLabel label = new JLabel(message);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(AppTheme.MUTED);
        row.add(label);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        messagesPanel.add(row);
        refreshMessages();
    }

    private void appendChatMessage(String sender, String content, String time) {
        boolean outgoing = sender != null && sender.equalsIgnoreCase(username);
        Color bubbleColor = outgoing
                ? new Color(0xDCEBFF) : AppTheme.SURFACE;

        JTextPane text = new JTextPane();
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder());
        text.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        EmojiTextRenderer.append(text, content == null ? "" : content);

        Dimension naturalSize = text.getPreferredSize();
        int textWidth = Math.max(90, Math.min(380, naturalSize.width));
        text.setSize(new Dimension(textWidth, 100000));
        int textHeight = Math.max(22, text.getPreferredSize().height);
        text.setPreferredSize(new Dimension(textWidth, textHeight));

        MessageBubble bubble = new MessageBubble(bubbleColor);
        bubble.setLayout(new BorderLayout(0, 4));
        bubble.add(text, BorderLayout.CENTER);
        JLabel timestamp = new JLabel(formatTime(time), SwingConstants.RIGHT);
        timestamp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timestamp.setForeground(AppTheme.MUTED);
        bubble.add(timestamp, BorderLayout.SOUTH);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.add(bubble, outgoing ? BorderLayout.EAST : BorderLayout.WEST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Math.max(42, bubble.getPreferredSize().height + 6)));
        messagesPanel.add(row);
        refreshMessages();
    }

    private String formatTime(String time) {
        if (time == null || time.length() < 16) {
            return "";
        }
        return time.substring(11, 16);
    }

    private void refreshMessages() {
        messagesPanel.revalidate();
        messagesPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar scrollbar = messageScrollPane.getVerticalScrollBar();
            scrollbar.setValue(scrollbar.getMaximum());
        });
    }

    public void closeConversation() {
        if (conversationClosed) {
            return;
        }
        conversationClosed = true;
        if (mediaManager != null) {
            mediaManager.shutdown();
        }
        if (messageListener != null) {
            messageListener.stop();
        } else if (clientSocket != null) {
            clientSocket.close();
        }
    }

    public String getReceiver() {
        return receiver;
    }

    private static class MessageBubble extends JPanel {
        private final Color bubbleColor;

        MessageBubble(Color bubbleColor) {
            this.bubbleColor = bubbleColor;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 13, 7, 13));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(bubbleColor);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g.dispose();
            super.paintComponent(graphics);
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

            ChatUI chat = new ChatUI(username.trim(), receiver.trim());
            JFrame frame = new JFrame("Chat - " + username.trim()
                    + " with " + receiver.trim());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(chat);
            frame.setSize(900, 660);
            frame.setMinimumSize(new Dimension(700, 500));
            frame.setLocationRelativeTo(null);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    chat.closeConversation();
                }
            });
            frame.setVisible(true);
        });
    }
}