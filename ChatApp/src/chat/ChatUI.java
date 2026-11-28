package chat;

import emoji.EmojiPickerPopup;
import emoji.EmojiTextRenderer;
import history.HistoryManager;
import media.FileInfo;
import media.AudioRecorder;
import media.MediaAttachmentView;
import media.MediaManager;
import model.PacketType;
import model.User;
import ui.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatUI extends JPanel implements MessageListener.PacketHandler {

    private final String username;
    private final String receiver;
    private final String receiverDisplayName;
    private final String serverHost;

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
    private JButton recordButton;
    private AudioRecorder audioRecorder;
    private EmojiPickerPopup emojiPicker;
    private boolean conversationClosed;
    private java.awt.Window focusWindow;
    private final WindowAdapter focusListener = new WindowAdapter() {
        @Override
        public void windowGainedFocus(WindowEvent event) {
            markHistoryAsRead();
        }
    };
    private final Map<String, JLabel> messageStatusLabels =
            new HashMap<String, JLabel>();

    public ChatUI(String username, String receiver) {
        this(username, receiver, receiver, "localhost");
    }

    public ChatUI(String username, String receiver, String serverHost) {
        this(username, receiver, receiver, serverHost);
    }

    public ChatUI(User currentUser, User receiver, String serverHost) {
        this(currentUser.getIdentity(), receiver.getIdentity(),
                receiver.getUsername(), serverHost);
    }

    private ChatUI(String username, String receiver, String receiverDisplayName,
                   String serverHost) {
        this.username = username;
        this.receiver = receiver;
        this.receiverDisplayName = receiverDisplayName;
        this.serverHost = serverHost;

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
        JLabel title = new JLabel(receiverDisplayName);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(AppTheme.TEXT);
        JLabel subtitle = new JLabel("Cuộc trò chuyện riêng");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(AppTheme.MUTED);
        JLabel avatar = new JLabel(
                new chat.ChatHomeUI.AvatarIcon(receiverDisplayName, 42));
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
        mediaButton = new JButton("Tệp đã gửi (0)");
        emojiButton = new JButton("Biểu tượng");
        recordButton = new JButton("Ghi âm");
        attachButton.setEnabled(false);
        mediaButton.setEnabled(false);
        AppTheme.styleButton(sendButton, true);
        AppTheme.styleButton(attachButton, false);
        AppTheme.styleButton(mediaButton, false);
        AppTheme.styleButton(emojiButton, false);
        AppTheme.styleButton(recordButton, false);
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
        actionsPanel.add(recordButton);
        actionsPanel.add(sendButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(actionsPanel, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        attachButton.addActionListener(e -> chooseAndSendMedia());
        emojiButton.addActionListener(e -> emojiPicker.showAt(emojiButton));
        recordButton.addActionListener(e -> toggleRecording());
        mediaButton.addActionListener(e ->
                MediaManager.chooseAndOpenMedia(this, getConversationMedia()));

        messageField.addActionListener(e -> sendMessage());
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) != 0) {
                updateFocusWindow();
            }
        });

    }

    private void updateFocusWindow() {
        java.awt.Window newWindow = SwingUtilities.getWindowAncestor(this);
        if (focusWindow == newWindow) {
            return;
        }
        if (focusWindow != null) {
            focusWindow.removeWindowFocusListener(focusListener);
        }
        focusWindow = newWindow;
        if (focusWindow != null) {
            focusWindow.addWindowFocusListener(focusListener);
        }
    }

    private void connectToServer() {
        clientSocket = new ClientSocket(serverHost, 8080);

        if (!clientSocket.connect()) {
            appendMessage("Không thể kết nối máy chủ.");
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
                SwingUtilities.invokeLater(() -> showMedia(info, false));
            }

            @Override
            public void onMediaSent(FileInfo info) {
                historyManager.saveMedia(info);
                SwingUtilities.invokeLater(() -> showMedia(info, true));
            }

            @Override
            public void onMediaError(String message) {
                SwingUtilities.invokeLater(() -> appendMessage("Lỗi gửi tệp: " + message));
            }
        });

        messageListener = new MessageListener(clientSocket, this);
        messageListener.start();
        markHistoryAsRead();
        attachButton.setEnabled(true);
        emojiButton.setEnabled(true);
        recordButton.setEnabled(true);
        appendMessage("Đã kết nối máy chủ.");
    }

    private void chooseAndSendMedia() {
        java.io.File file = MediaManager.chooseFile(this);
        if (file != null && mediaManager != null) {
            mediaManager.sendMedia(receiver, null, file);
        }
    }

    private void toggleRecording() {
        if (mediaManager == null) {
            return;
        }
        if (audioRecorder == null) {
            AudioRecorder recorder = new AudioRecorder();
            try {
                recorder.start();
                audioRecorder = recorder;
                recordButton.setText("Dừng ghi");
                appendMessage("Đang ghi âm...");
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(this,
                        "Không thể bắt đầu ghi âm. Hãy kiểm tra micro.\n"
                                + exception.getMessage(),
                        "Lỗi ghi âm", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        AudioRecorder recorder = audioRecorder;
        audioRecorder = null;
        recordButton.setText("Ghi âm");
        try {
            java.io.File recording = recorder.stopAndSave();
            mediaManager.sendMedia(receiver, null, recording);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Lỗi ghi âm", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showMedia(FileInfo info, boolean sent) {
        appendMediaMessage(info);
        updateMediaButton();
    }

    private List<FileInfo> getConversationMedia() {
        List<FileInfo> files = new ArrayList<FileInfo>();
        for (Message message : historyManager.getPrivateHistory(username, receiver)) {
            PacketType type = MediaManager.toMediaType(message.getType());
            if (type != null && message.getFilepath() != null
                    && !message.getFilepath().trim().isEmpty()) {
                java.io.File file = new java.io.File(message.getFilepath());
                FileInfo info = new FileInfo(type, message.getContent(),
                        file.isFile() ? file.length() : 0,
                        message.getFilepath(), message.getSender(),
                        message.getReceiver(), message.getGroupId(), message.getTime());
                info.setTransferId(message.getTransferId());
                files.add(info);
            }
        }
        return files;
    }

    private void updateMediaButton() {
        int count = getConversationMedia().size();
        mediaButton.setText("Tệp đã gửi (" + count + ")");
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
            appendMessage("Chưa kết nối máy chủ.");
            return;
        }

        String messageId = UUID.randomUUID().toString();
        String packet = "{\"type\":\"MESSAGE\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"receiver\":\"" + escapeJson(receiver) + "\","
                + "\"content\":\"" + escapeJson(content) + "\","
                + "\"messageId\":\"" + messageId + "\"}";

        clientSocket.sendPacket(packet);

        String time = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );

        appendChatMessage(username, content, time, messageId, "Đã gửi");
        historyManager.savePrivateMessage(username, receiver, content, time,
                messageId, "Đã gửi");

        if (clearInput) {
            messageField.setText("");
        }
    }

    @Override
    public void onPacketReceived(String packet) {
        if (mediaManager != null && mediaManager.handlePacket(packet)) {
            return;
        }

        Map<String, String> fields = ChatPacketParser.parse(packet);
        String type = fields.get("type");
        if (type != null && type.startsWith("CALL_")) {
            return;
        }
        String sender = fields.get("sender");
        String content = fields.get("content");
        String packetReceiver = fields.get("receiver");
        String messageId = fields.get("messageId");

        if (("DELIVERED".equals(type) || "READ".equals(type))
                && username.equals(packetReceiver) && messageId != null) {
            String status = "READ".equals(type) ? "Đã đọc" : "Đã nhận";
            SwingUtilities.invokeLater(() -> updateMessageStatus(messageId, status));
            return;
        }

        if ("MESSAGE".equals(type)
                && username.equals(packetReceiver)
                && sender != null
                && sender.equals(receiver)
                && content != null) {

            String time = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );

            historyManager.savePrivateMessage(sender, username, content, time,
                    messageId, null);

            SwingUtilities.invokeLater(() -> {
                appendChatMessage(sender, content, time, messageId, null);
                if (messageId != null) {
                    sendReceipt("DELIVERED", sender, messageId);
                    if (isConversationActive()) {
                        if (sendReceipt("READ", sender, messageId)) {
                            historyManager.updateDeliveryStatus(messageId, "Đã đọc");
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            appendMessage("Đã mất kết nối máy chủ.");
            sendButton.setEnabled(false);
            messageField.setEnabled(false);
            attachButton.setEnabled(false);
            emojiButton.setEnabled(false);
            recordButton.setEnabled(false);
        });
    }

    private void loadHistory() {
        for (Message message : historyManager.getPrivateHistory(username, receiver)) {
            if (message.getFilepath() != null
                    && !message.getFilepath().trim().isEmpty()) {
                PacketType type = MediaManager.toMediaType(message.getType());
                if (type != null) {
                    java.io.File file = new java.io.File(message.getFilepath());
                    FileInfo info = new FileInfo(type, message.getContent(),
                            file.isFile() ? file.length() : 0,
                            message.getFilepath(), message.getSender(),
                            message.getReceiver(), message.getGroupId(), message.getTime());
                    info.setTransferId(message.getTransferId());
                    appendMediaMessage(info);
                    continue;
                }
            }
            String content = message.getContent();
            appendChatMessage(message.getSender(), content, message.getTime(),
                    message.getMessageId(), message.getDeliveryStatus());
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

    private void appendChatMessage(String sender, String content, String time,
                                   String messageId, String deliveryStatus) {
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

        if (outgoing && messageId != null) {
            JLabel status = new JLabel(deliveryStatus == null ? "Đã gửi" : deliveryStatus,
                    SwingConstants.RIGHT);
            status.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            status.setForeground(AppTheme.MUTED);
            bubble.add(status, BorderLayout.NORTH);
            messageStatusLabels.put(messageId, status);
        }

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.add(bubble, outgoing ? BorderLayout.EAST : BorderLayout.WEST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Math.max(42, bubble.getPreferredSize().height + 6)));
        messagesPanel.add(row);
        refreshMessages();
    }

    private void appendMediaMessage(FileInfo info) {
        boolean outgoing = info.getSender() != null
                && info.getSender().equalsIgnoreCase(username);
        Color bubbleColor = outgoing
                ? new Color(0xDCEBFF) : AppTheme.SURFACE;
        MessageBubble bubble = new MessageBubble(bubbleColor);
        bubble.setLayout(new BorderLayout(0, 4));
        bubble.add(new MediaAttachmentView(this, info), BorderLayout.CENTER);
        JLabel timestamp = new JLabel(formatTime(info.getTime()), SwingConstants.RIGHT);
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

    private void markHistoryAsRead() {
        for (Message message : historyManager.getPrivateHistory(username, receiver)) {
            if (message.getMessageId() != null
                    && !username.equalsIgnoreCase(message.getSender())
                    && !"Đã đọc".equals(message.getDeliveryStatus())
                    && sendReceipt("READ", message.getSender(), message.getMessageId())) {
                historyManager.updateDeliveryStatus(message.getMessageId(), "Đã đọc");
            }
        }
    }

    private boolean isConversationActive() {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        return isShowing() && window != null && window.isActive();
    }

    private boolean sendReceipt(String type, String sender, String messageId) {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return false;
        }
        clientSocket.sendPacket("{\"type\":\"" + type + "\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"receiver\":\"" + escapeJson(sender) + "\","
                + "\"messageId\":\"" + escapeJson(messageId) + "\"}");
        return true;
    }

    private void updateMessageStatus(String messageId, String status) {
        JLabel label = messageStatusLabels.get(messageId);
        if (label != null) {
            label.setText(status);
        }
        historyManager.updateDeliveryStatus(messageId, status);
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
        if (audioRecorder != null) {
            audioRecorder.cancel();
            audioRecorder = null;
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(
                    null, "Nhập tên hiển thị:"
            );

            if (username == null || username.trim().isEmpty()) {
                return;
            }

            String receiver = JOptionPane.showInputDialog(
                    null, "Trò chuyện với ai?"
            );

            if (receiver == null || receiver.trim().isEmpty()) {
                return;
            }

            ChatUI chat = new ChatUI(username.trim(), receiver.trim());
            JFrame frame = new JFrame("Trò chuyện - " + username.trim()
                    + " với " + receiver.trim());
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