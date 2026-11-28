package group;

import chat.ClientSocket;
import chat.MessageListener;
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

public class GroupChatUI extends JFrame implements MessageListener.PacketHandler {

    private final String username;
    private final Group group;

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
    private EmojiPickerPopup emojiPicker;
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
        setSize(980, 680);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(AppTheme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(AppTheme.SURFACE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
        ));

        JLabel titleLabel = new JLabel(group.getGroupName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(AppTheme.TEXT);

        statusLabel = new JLabel("Disconnected");
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
        add(chatScroll, BorderLayout.CENTER);

        JTextArea membersArea = new JTextArea();
        membersArea.setEditable(false);
        membersArea.setLineWrap(true);
        membersArea.setWrapStyleWord(true);
        membersArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        membersArea.setForeground(AppTheme.TEXT);
        membersArea.setBackground(AppTheme.SURFACE);
        membersArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        membersArea.append("TẠO BỞI\n" + group.getCreator() + "\n\n");
        membersArea.append("THÀNH VIÊN (" + group.getMemberCount() + ")\n");

        for (String member : group.getMembers()) {
            membersArea.append("• " + member
                    + (member.equals(group.getCreator()) ? "  ·  Trưởng nhóm" : "")
                    + "\n");
        }

        JScrollPane membersScroll = new JScrollPane(membersArea);
        membersScroll.setPreferredSize(new Dimension(210, 0));
        membersScroll.setBorder(BorderFactory.createMatteBorder(
                0, 1, 0, 0, AppTheme.BORDER));
        membersScroll.getViewport().setBackground(AppTheme.SURFACE);

        add(membersScroll, BorderLayout.EAST);

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
                MediaManager.chooseAndOpenMedia(this, getGroupMedia()));
        messageField.addActionListener(e -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
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

        statusLabel.setText("Connected");
        appendMessage("[System] Connected to server.");
    }

    private void chooseAndSendMedia() {
        java.io.File file = MediaManager.chooseFile(this);
        if (file != null && mediaManager != null) {
            mediaManager.sendMedia(null, group.getGroupId(), file);
        }
    }

    private void showMedia(FileInfo info, String action) {
        appendMessage(info.getSender() + action + " " + info);
        updateMediaButton();
    }

    private List<FileInfo> getGroupMedia() {
        List<FileInfo> files = new ArrayList<FileInfo>();
        for (chat.Message message : historyManager.getGroupHistory(group.getGroupId())) {
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
        int count = getGroupMedia().size();
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

        String packet = "{\"type\":\"GROUP_MESSAGE\","
                + "\"sender\":\"" + escapeJson(username) + "\","
                + "\"groupId\":\"" + escapeJson(group.getGroupId()) + "\","
                + "\"content\":\"" + escapeJson(content) + "\"}";

        clientSocket.sendPacket(packet);

        String time = LocalDateTime.now().format(TIME_FORMAT);

        appendMessage(username + " [" + time + "]: " + content);
        historyManager.saveGroupMessage(username, group.getGroupId(), content, time);

        if (clearInput) {
            messageField.setText("");
        }
    }

    @Override
    public void onPacketReceived(String packet) {
        if (mediaManager != null && mediaManager.handlePacket(packet, group.getGroupId())) {
            return;
        }

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
        attachButton.setEnabled(connected);
        emojiButton.setEnabled(connected);
    }

    private void loadHistory() {
        String text = historyManager.formatHistory(
                historyManager.getGroupHistory(group.getGroupId())
        );

        if (!text.isEmpty()) {
            EmojiTextRenderer.append(chatArea, text);
        }
        updateMediaButton();
    }

    private void appendMessage(String message) {
        EmojiTextRenderer.append(chatArea, message + "\n");
    }

    private void disconnect() {
        if (mediaManager != null) {
            mediaManager.shutdown();
        }
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