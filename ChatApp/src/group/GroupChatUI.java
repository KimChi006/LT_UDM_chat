package group;

import chat.ClientSocket;
import chat.MessageListener;
import emoji.EmojiPickerPopup;
import emoji.EmojiTextRenderer;
import history.HistoryManager;
import media.AudioRecorder;
import media.FileInfo;
import media.MediaAttachmentView;
import media.MediaManager;
import model.PacketType;
import status.StatusManager;
import ui.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class GroupChatUI extends JPanel implements MessageListener.PacketHandler,
        StatusManager.StatusListener {

    private final String username;
    private final Group group;
    private final String serverHost;
    private final Map<String, String> displayNames;

    private ClientSocket clientSocket;
    private MessageListener messageListener;
    private MediaManager mediaManager;
    private final HistoryManager historyManager = HistoryManager.getInstance();

    private JTextPane chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton attachButton;
    private JButton mediaButton;
    private JButton emojiButton;
    private JButton recordButton;
    private EmojiPickerPopup emojiPicker;
    private JLabel statusLabel;
    private JPanel memberListPanel;
    private AudioRecorder audioRecorder;
    private JFrame standaloneFrame;
    private Window focusWindow;
    private boolean conversationClosed;
    private final WindowAdapter focusListener = new WindowAdapter() {
        @Override
        public void windowGainedFocus(WindowEvent event) {
            markHistoryAsRead();
        }
    };
    private final Map<String, JLabel> receiptLabels = new HashMap<String, JLabel>();

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public GroupChatUI(String username, Group group) {
        this(username, group, "localhost", java.util.Collections.emptyMap(), true);
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
            mediaManager.sendMedia(null, group.getGroupId(), recording);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Lỗi ghi âm", JOptionPane.ERROR_MESSAGE);
        }
    }

    public GroupChatUI(String username, Group group, String serverHost) {
        this(username, group, serverHost, java.util.Collections.emptyMap(), true);
    }

    public GroupChatUI(String username, Group group, String serverHost,
                       Map<String, String> displayNames) {
        this(username, group, serverHost, displayNames, true);
    }

    public static GroupChatUI createEmbedded(String username, Group group,
                                             String serverHost,
                                             Map<String, String> displayNames) {
        return new GroupChatUI(username, group, serverHost, displayNames, false);
    }

    private GroupChatUI(String username, Group group, String serverHost,
                        Map<String, String> displayNames, boolean standalone) {
        super(new BorderLayout());
        this.username = username;
        this.group = group;
        this.serverHost = serverHost;
        this.displayNames = new HashMap<String, String>(displayNames == null
                ? java.util.Collections.<String, String>emptyMap() : displayNames);
        model.User currentUser = auth.UserSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            this.displayNames.put(currentUser.getIdentity(), currentUser.getUsername());
        }

        StatusManager.getInstance().addListener(this);
        initializeUI();
        loadHistory();
        connectToServer();
        if (standalone) {
            showStandaloneWindow();
        }
    }

    private void initializeUI() {
        setBackground(AppTheme.BACKGROUND);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(AppTheme.SURFACE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
        ));

        JLabel titleLabel = new JLabel(group.getGroupName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(AppTheme.TEXT);

        statusLabel = new JLabel("Mất kết nối");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(AppTheme.MUTED);

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 3));
        heading.setOpaque(false);
        heading.add(titleLabel);
        heading.add(statusLabel);
        headerPanel.add(heading, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        chatArea = new JTextPane();
        AppTheme.styleChatPane(chatArea);

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getViewport().setBackground(AppTheme.BACKGROUND);

        JPanel membersContent = new JPanel(new BorderLayout());
        membersContent.setBackground(AppTheme.SURFACE);
        JLabel membersHeading = new JLabel("Thành viên · " + group.getMemberCount());
        membersHeading.setFont(new Font("Segoe UI", Font.BOLD, 14));
        membersHeading.setForeground(AppTheme.TEXT);
        membersHeading.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 12));
        membersContent.add(membersHeading, BorderLayout.NORTH);
        memberListPanel = new JPanel();
        memberListPanel.setBackground(AppTheme.SURFACE);
        memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
        membersContent.add(new JScrollPane(memberListPanel), BorderLayout.CENTER);
        refreshGroupMembers();

        membersContent.setPreferredSize(new Dimension(210, 0));
        membersContent.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, AppTheme.BORDER));

        add(membersContent, BorderLayout.WEST);

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

        JPanel conversationPanel = new JPanel(new BorderLayout());
        conversationPanel.setBackground(AppTheme.BACKGROUND);
        conversationPanel.add(chatScroll, BorderLayout.CENTER);
        conversationPanel.add(inputPanel, BorderLayout.SOUTH);
        add(conversationPanel, BorderLayout.CENTER);

        sendButton.addActionListener(e -> sendMessage());
        attachButton.addActionListener(e -> chooseAndSendMedia());
        emojiButton.addActionListener(e -> emojiPicker.showAt(emojiButton));
        recordButton.addActionListener(e -> toggleRecording());
        mediaButton.addActionListener(e ->
                MediaManager.chooseAndOpenMedia(this, getGroupMedia()));
        messageField.addActionListener(e -> sendMessage());

        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                updateFocusWindow();
            }
        });

    }

    private void showStandaloneWindow() {
        standaloneFrame = new JFrame("Trò chuyện nhóm - " + group.getGroupName());
        standaloneFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        standaloneFrame.setSize(980, 680);
        standaloneFrame.setMinimumSize(new Dimension(760, 520));
        standaloneFrame.setLocationRelativeTo(null);
        standaloneFrame.setContentPane(this);
        standaloneFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeConversation();
            }
        });
        standaloneFrame.setVisible(true);
    }

    private void updateFocusWindow() {
        Window newWindow = SwingUtilities.getWindowAncestor(this);
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
            setConnectedUI(false);
            return;
        }

        String loginPacket = "{\"type\":\"LOGIN\",\"username\":\""
                + escapeJson(username) + "\"}";

        clientSocket.sendPacket(loginPacket);

        mediaManager = new MediaManager(clientSocket, username, new MediaManager.MediaListener() {
            @Override
            public void onMediaReceived(FileInfo info) {
                historyManager.saveMedia(info);
                SwingUtilities.invokeLater(() -> showMedia(info));
            }

            @Override
            public void onMediaSent(FileInfo info) {
                historyManager.saveMedia(info);
                SwingUtilities.invokeLater(() -> showMedia(info));
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

        statusLabel.setText("Đã kết nối");
        appendMessage("Đã kết nối máy chủ.");
    }

    private void chooseAndSendMedia() {
        java.io.File file = MediaManager.chooseFile(this);
        if (file != null && mediaManager != null) {
            mediaManager.sendMedia(null, group.getGroupId(), file);
        }
    }

    private void showMedia(FileInfo info) {
        appendMediaMessage(info);
        updateMediaButton();
    }

    private List<FileInfo> getGroupMedia() {
        List<FileInfo> files = new ArrayList<FileInfo>();
        for (chat.Message message : historyManager.getGroupHistory(group.getGroupId())) {
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
        int count = getGroupMedia().size();
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
        String packet = "{\"type\":\"GROUP_MESSAGE\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"senderName\":\"" + escapeJson(displayName(username)) + "\","
                + "\"groupId\":\"" + escapeJson(group.getGroupId()) + "\","
                + "\"content\":\"" + escapeJson(content) + "\","
                + "\"messageId\":\"" + messageId + "\"}";

        clientSocket.sendPacket(packet);

        String time = LocalDateTime.now().format(TIME_FORMAT);

        appendGroupMessage(username, displayName(username), content, time);
        historyManager.saveGroupMessage(username, displayName(username),
                group.getGroupId(), content, time, messageId);
        appendReceipt(messageId, "Đã gửi");

        if (clearInput) {
            messageField.setText("");
        }
    }

    @Override
    public void onPacketReceived(String packet) {
        Map<String, String> fields = chat.ChatPacketParser.parse(packet);
        String packetType = fields.get("type");
        if (packetType != null && packetType.startsWith("CALL_")) {
            return;
        }
        if (mediaManager != null && mediaManager.handlePacket(packet, group.getGroupId())) {
            return;
        }

        String type = fields.get("type");
        String sender = fields.get("sender");
        String groupId = fields.get("groupId");
        String content = fields.get("content");
        String senderName = fields.get("senderName");
        String packetReceiver = fields.get("receiver");
        String messageId = fields.get("messageId");

        if (("DELIVERED".equals(type) || "READ".equals(type))
                && username.equals(packetReceiver) && messageId != null) {
            String receipt = "READ".equals(type) ? "Đã đọc" : "Đã nhận";
            SwingUtilities.invokeLater(() -> updateReceipt(messageId, receipt));
            return;
        }

        if ("GROUP_MESSAGE".equals(type)
                && groupId != null
                && groupId.equals(group.getGroupId())
                && sender != null
                && content != null
                && !sender.equals(username)) {

            String time = LocalDateTime.now().format(TIME_FORMAT);

            String visibleName = senderName == null
                    ? displayName(sender) : senderName;
            historyManager.saveGroupMessage(sender, visibleName,
                    group.getGroupId(), content, time, messageId);

            SwingUtilities.invokeLater(
                    () -> {
                        appendGroupMessage(sender, visibleName, content, time);
                        if (messageId != null) {
                            sendReceipt("DELIVERED", sender, messageId);
                            if (isActive()) {
                                if (sendReceipt("READ", sender, messageId)) {
                                    historyManager.updateDeliveryStatus(
                                            messageId, "Đã đọc");
                                }
                            }
                        }
                    }
            );
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Mất kết nối");
            appendMessage("Đã mất kết nối máy chủ.");
            setConnectedUI(false);
        });
    }

    private void setConnectedUI(boolean connected) {
        sendButton.setEnabled(connected);
        messageField.setEnabled(connected);
        attachButton.setEnabled(connected);
        emojiButton.setEnabled(connected);
        recordButton.setEnabled(connected);
    }

    private void loadHistory() {
        for (chat.Message message : historyManager.getGroupHistory(group.getGroupId())) {
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
            String senderName = message.getSenderName() == null
                    ? displayName(message.getSender()) : message.getSenderName();
            String content = message.getContent();
            if (message.getFilepath() != null
                    && !message.getFilepath().trim().isEmpty()) {
                content = "Tệp: " + content;
            }
            appendGroupMessage(message.getSender(), senderName,
                    content, message.getTime());
            if (username.equalsIgnoreCase(message.getSender())
                    && message.getMessageId() != null
                    && message.getDeliveryStatus() != null) {
                appendReceipt(message.getMessageId(), message.getDeliveryStatus());
            }
        }
        updateMediaButton();
    }

    private void appendMessage(String message) {
        EmojiTextRenderer.append(chatArea, message + "\n");
    }

    private void appendGroupMessage(String sender, String senderName,
                                    String content, String time) {
        boolean outgoing = sender != null && sender.equalsIgnoreCase(username);
        Color bubbleColor = outgoing ? new Color(0xDCEBFF) : AppTheme.SURFACE;

        JTextPane text = new JTextPane();
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder());
        text.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        EmojiTextRenderer.append(text, content == null ? "" : content);
        Dimension naturalSize = text.getPreferredSize();
        int textWidth = Math.max(90, Math.min(380, naturalSize.width));
        text.setSize(new Dimension(textWidth, 100000));
        text.setPreferredSize(new Dimension(textWidth,
                Math.max(22, text.getPreferredSize().height)));

        MessageBubble bubble = new MessageBubble(bubbleColor);
        bubble.setLayout(new BorderLayout(0, 4));
        if (!outgoing && senderName != null && !senderName.trim().isEmpty()) {
            JLabel name = new JLabel(senderName);
            name.setFont(new Font("Segoe UI", Font.BOLD, 11));
            name.setForeground(AppTheme.PRIMARY);
            bubble.add(name, BorderLayout.NORTH);
        }
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
                bubble.getPreferredSize().height + 8));
        appendComponent(row, outgoing
                ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
    }

    private String formatTime(String time) {
        return time == null || time.length() < 16 ? "" : time.substring(11, 16);
    }

    private void appendComponent(Component component, int alignment) {
        StyledDocument document = chatArea.getStyledDocument();
        int offset = document.getLength();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setComponent(attributes, component);
        SimpleAttributeSet paragraph = new SimpleAttributeSet();
        StyleConstants.setAlignment(paragraph, alignment);
        document.setParagraphAttributes(offset, 1, paragraph, false);
        try {
            document.insertString(offset, "\uFFFC", attributes);
            document.insertString(document.getLength(), "\n", null);
            chatArea.setCaretPosition(document.getLength());
        } catch (BadLocationException exception) {
            System.err.println("Cannot show chat component: " + exception.getMessage());
        }
    }

    private void appendMediaMessage(FileInfo info) {
        appendMessage(displayName(info.getSender()) + " ["
                + info.getTime() + "]:");
        StyledDocument document = chatArea.getStyledDocument();
        int offset = document.getLength();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setComponent(attributes, new MediaAttachmentView(this, info));
        SimpleAttributeSet paragraph = new SimpleAttributeSet();
        StyleConstants.setAlignment(paragraph,
                username.equalsIgnoreCase(info.getSender())
                        ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        document.setParagraphAttributes(offset, 1, paragraph, false);
        try {
            document.insertString(offset, "\uFFFC", attributes);
            document.insertString(document.getLength(), "\n", null);
            chatArea.setCaretPosition(document.getLength());
        } catch (BadLocationException exception) {
            System.err.println("Cannot show media message: " + exception.getMessage());
        }
    }

    private void disconnect() {
        if (conversationClosed) {
            return;
        }
        conversationClosed = true;
        if (focusWindow != null) {
            focusWindow.removeWindowFocusListener(focusListener);
            focusWindow = null;
        }
        StatusManager.getInstance().removeListener(this);
        if (audioRecorder != null) {
            audioRecorder.cancel();
            audioRecorder = null;
        }
        if (mediaManager != null) {
            mediaManager.shutdown();
        }
        if (messageListener != null) {
            messageListener.stop();
        } else if (clientSocket != null) {
            clientSocket.close();
        }
    }

    public void closeConversation() {
        disconnect();
    }

    public String getGroupId() {
        return group.getGroupId();
    }

    private boolean isActive() {
        Window window = SwingUtilities.getWindowAncestor(this);
        return isShowing() && window != null && window.isActive();
    }

    private void markHistoryAsRead() {
        for (chat.Message message : historyManager.getGroupHistory(group.getGroupId())) {
            if (!username.equalsIgnoreCase(message.getSender())
                    && message.getMessageId() != null
                    && !"Đã đọc".equals(message.getDeliveryStatus())
                    && sendReceipt("READ", message.getSender(), message.getMessageId())) {
                historyManager.updateDeliveryStatus(message.getMessageId(), "Đã đọc");
            }
        }
    }

    private boolean sendReceipt(String type, String receiver, String messageId) {
        if (clientSocket == null || !clientSocket.isConnected()) {
            return false;
        }
        clientSocket.sendPacket("{\"type\":\"" + type + "\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"receiver\":\"" + escapeJson(receiver) + "\","
                + "\"messageId\":\"" + escapeJson(messageId) + "\"}");
        return true;
    }

    private void appendReceipt(String messageId, String status) {
        JLabel receipt = new JLabel(status, SwingConstants.RIGHT);
        receipt.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        receipt.setForeground(AppTheme.MUTED);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(receipt, BorderLayout.EAST);
        receiptLabels.put(messageId, receipt);
        appendComponent(row, StyleConstants.ALIGN_RIGHT);
    }

    private void updateReceipt(String messageId, String status) {
        JLabel receipt = receiptLabels.get(messageId);
        if (receipt == null) {
            return;
        }
        receipt.setText(status);
        historyManager.updateDeliveryStatus(messageId, status);
    }

    private String displayName(String identity) {
        String name = displayNames.get(identity);
        return name == null ? identity : name;
    }

    private static final class MessageBubble extends JPanel {

        private final Color bubbleColor;

        private MessageBubble(Color bubbleColor) {
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

    @Override
    public void onStatusChanged(String identity, String status) {
        if (group.isMember(identity)) {
            SwingUtilities.invokeLater(this::refreshGroupMembers);
        }
    }

    private void refreshGroupMembers() {
        if (memberListPanel == null) {
            return;
        }
        memberListPanel.removeAll();
        memberListPanel.add(createSectionLabel("NGƯỜI TẠO"));
        memberListPanel.add(createMemberRow(group.getCreator(), true));
        memberListPanel.add(createSectionLabel("THÀNH VIÊN"));
        for (String member : group.getMembers()) {
            if (!member.equals(group.getCreator())) {
                memberListPanel.add(createMemberRow(member, false));
            }
        }
        memberListPanel.revalidate();
        memberListPanel.repaint();
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(AppTheme.MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(12, 12, 5, 8));
        return label;
    }

    private JPanel createMemberRow(String identity, boolean creator) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));
        boolean online = "ONLINE".equals(StatusManager.getInstance().getStatus(identity));
        JLabel indicator = new JLabel("●");
        indicator.setForeground(online ? new Color(0x22C55E) : new Color(0x8A929E));
        JLabel name = new JLabel(displayName(identity)
                + (creator ? " · Trưởng nhóm" : ""));
        name.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        name.setForeground(AppTheme.TEXT);
        row.add(indicator, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        return row;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}