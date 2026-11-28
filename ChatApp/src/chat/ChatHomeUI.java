package chat;

import auth.LoginUI;
import auth.UserSession;
import history.HistoryManager;
import group.Group;
import group.GroupChatUI;
import group.GroupManager;
import model.User;
import status.StatusManager;
import storage.GroupStorage;
import storage.UserStorage;
import ui.AppTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ChatHomeUI extends JFrame {

    private final User currentUser;
    private final UserStorage userStorage = new UserStorage();
    private final GroupStorage groupStorage = new GroupStorage();
    private final HistoryManager historyManager = HistoryManager.getInstance();
    private final DefaultListModel<User> peopleModel = new DefaultListModel<User>();
    private final DefaultListModel<Group> groupsModel = new DefaultListModel<Group>();

    private final JList<User> peopleList = new JList<User>(peopleModel);
    private final JList<Group> groupsList = new JList<Group>(groupsModel);
    private final JPanel listCards = new JPanel(new CardLayout());
    private final JPanel detailsBody = new JPanel();
    private final JTextField searchField = new JTextField();
    private final JToggleButton peopleButton = new JToggleButton("Mọi người");
    private final JToggleButton groupsButton = new JToggleButton("Nhóm");
    private final JButton createGroupButton = new JButton("+ Tạo nhóm");
    private final JLabel inboxStatus = new JLabel("Chưa kết nối máy chủ");

    private User selectedUser;
    private Group selectedGroup;
    private JFrame activeConversation;
    private volatile ClientSocket inboxSocket;
    private MessageListener inboxListener;
    private boolean switchingConversation;
    private boolean closingHome;

    public ChatHomeUI(User user) {
        super("UDM Chat");
        if (user == null || user.getUsername() == null) {
            throw new IllegalArgumentException("A signed-in user is required.");
        }
        currentUser = user;
        initializeUI();
        refreshData();
        connectInbox();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);
        setContentPane(root);

        JPanel sidebar = createSidebar();
        sidebar.setPreferredSize(new Dimension(350, 0));
        root.add(sidebar, BorderLayout.WEST);
        root.add(createDetailsPanel(), BorderLayout.CENTER);

        createGroupButton.addActionListener(event -> createGroup());
        peopleButton.addActionListener(event -> showPeople());
        groupsButton.addActionListener(event -> showGroups());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filterLists();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filterLists();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filterLists();
            }
        });

        peopleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peopleList.setFixedCellHeight(70);
        peopleList.setCellRenderer(new PersonRenderer());
        peopleList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && peopleList.getSelectedValue() != null) {
                selectedGroup = null;
                showPersonDetails(peopleList.getSelectedValue());
            }
        });
        peopleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedConversation();
                }
            }
        });

        groupsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupsList.setFixedCellHeight(70);
        groupsList.setCellRenderer(new GroupRenderer());
        groupsList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && groupsList.getSelectedValue() != null) {
                selectedUser = null;
                showGroupDetails(groupsList.getSelectedValue());
            }
        });
        groupsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedConversation();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closingHome = true;
                closeActiveConversation();
                disconnectInbox();
                StatusManager.getInstance().setStatus(currentUser.getUsername(), "OFFLINE");
                UserSession.getInstance().logout();
            }
        });

        showWelcome();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setBackground(AppTheme.SURFACE);
        sidebar.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, AppTheme.BORDER));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 16));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Tin nhắn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 21));
        title.setForeground(AppTheme.TEXT);
        JLabel account = new JLabel("Đăng nhập: " + currentUser.getUsername());
        account.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        account.setForeground(AppTheme.MUTED);
        inboxStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        inboxStatus.setForeground(AppTheme.MUTED);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(account);
        titleBlock.add(Box.createVerticalStrut(3));
        titleBlock.add(inboxStatus);

        AppTheme.styleButton(createGroupButton, true);
        header.add(titleBlock, BorderLayout.CENTER);
        header.add(createGroupButton, BorderLayout.SOUTH);

        JPanel searchPanel = new JPanel(new BorderLayout(0, 10));
        searchPanel.setBackground(AppTheme.SURFACE);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        AppTheme.styleTextField(searchField);
        searchField.setToolTipText("Tìm tên người hoặc nhóm");
        searchPanel.add(searchField, BorderLayout.CENTER);

        ButtonGroup navigation = new ButtonGroup();
        navigation.add(peopleButton);
        navigation.add(groupsButton);
        styleToggle(peopleButton, true);
        styleToggle(groupsButton, false);
        JPanel tabs = new JPanel(new BorderLayout(6, 0));
        tabs.setBackground(AppTheme.SURFACE);
        tabs.add(peopleButton, BorderLayout.WEST);
        tabs.add(groupsButton, BorderLayout.CENTER);
        searchPanel.add(tabs, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(AppTheme.SURFACE);
        top.add(header, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.SOUTH);

        peopleList.setBackground(AppTheme.SURFACE);
        groupsList.setBackground(AppTheme.SURFACE);
        listCards.add(new JScrollPane(peopleList), "people");
        listCards.add(new JScrollPane(groupsList), "groups");

        JButton logoutButton = new JButton("Đăng xuất");
        AppTheme.styleButton(logoutButton, false);
        logoutButton.addActionListener(event -> logout());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppTheme.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        footer.add(logoutButton, BorderLayout.EAST);

        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(listCards, BorderLayout.CENTER);
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        detailsBody.setOpaque(false);
        detailsBody.setLayout(new BoxLayout(detailsBody, BoxLayout.Y_AXIS));
        panel.add(detailsBody, BorderLayout.CENTER);
        return panel;
    }

    private void styleToggle(JToggleButton button, boolean selected) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(selected ? AppTheme.PRIMARY : AppTheme.MUTED);
        button.setBackground(selected ? AppTheme.PRIMARY_LIGHT : AppTheme.SURFACE);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    private void showPeople() {
        styleToggle(peopleButton, true);
        styleToggle(groupsButton, false);
        ((CardLayout) listCards.getLayout()).show(listCards, "people");
        peopleList.clearSelection();
        selectedUser = null;
        showWelcome();
    }

    private void showGroups() {
        styleToggle(peopleButton, false);
        styleToggle(groupsButton, true);
        ((CardLayout) listCards.getLayout()).show(listCards, "groups");
        groupsList.clearSelection();
        selectedGroup = null;
        showWelcome();
    }

    private void refreshData() {
        List<User> users = userStorage.loadAll();
        List<Group> groups = groupStorage.loadAll();
        if (users == null || groups == null) {
            JOptionPane.showMessageDialog(this,
                    "Không thể đọc dữ liệu tài khoản hoặc nhóm. Kiểm tra các file JSON trong ChatApp/data.",
                    "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Collections.sort(users, Comparator.comparing(
                User::getUsername, String.CASE_INSENSITIVE_ORDER));
        Collections.sort(groups, Comparator.comparing(
                group -> group.getGroupName() == null ? "" : group.getGroupName(),
                String.CASE_INSENSITIVE_ORDER));
        allPeople.clear();
        allGroups.clear();
        for (User user : users) {
            if (!user.getUsername().equalsIgnoreCase(currentUser.getUsername())) {
                allPeople.add(user);
            }
        }
        for (Group group : groups) {
            if (group.isMember(currentUser.getUsername())) {
                allGroups.add(group);
            }
        }
        filterLists();
    }

    private final List<User> allPeople = new ArrayList<User>();
    private final List<Group> allGroups = new ArrayList<Group>();

    private void filterLists() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        peopleList.clearSelection();
        groupsList.clearSelection();
        selectedUser = null;
        selectedGroup = null;
        peopleModel.clear();
        for (User user : allPeople) {
            if (user.getUsername().toLowerCase(Locale.ROOT).contains(query)) {
                peopleModel.addElement(user);
            }
        }
        groupsModel.clear();
        for (Group group : allGroups) {
            String name = group.getGroupName() == null ? "" : group.getGroupName();
            String creator = group.getCreator() == null ? "" : group.getCreator();
            if (name.toLowerCase(Locale.ROOT).contains(query)
                    || creator.toLowerCase(Locale.ROOT).contains(query)) {
                groupsModel.addElement(group);
            }
        }
        showWelcome();
    }

    private void showWelcome() {
        detailsBody.removeAll();
        detailsBody.add(Box.createVerticalGlue());
        JLabel icon = new JLabel(new AvatarIcon("UDM", 86));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailsBody.add(icon);
        detailsBody.add(Box.createVerticalStrut(20));
        JLabel title = detailLabel("Xin chào, " + currentUser.getUsername(), 24, true);
        detailsBody.add(title);
        detailsBody.add(Box.createVerticalStrut(8));
        JLabel subtitle = detailLabel("Chọn một người hoặc nhóm để bắt đầu trò chuyện.", 14, false);
        detailsBody.add(subtitle);
        detailsBody.add(Box.createVerticalGlue());
        refreshDetails();
    }

    private void showPersonDetails(User user) {
        selectedUser = user;
        selectedGroup = null;
        detailsBody.removeAll();
        detailsBody.add(Box.createVerticalGlue());
        JLabel avatar = new JLabel(new AvatarIcon(user.getUsername(), 86));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailsBody.add(avatar);
        detailsBody.add(Box.createVerticalStrut(18));
        detailsBody.add(detailLabel(user.getUsername(), 23, true));
        detailsBody.add(Box.createVerticalStrut(7));
        detailsBody.add(detailLabel("Tài khoản UDM Chat", 14, false));
        detailsBody.add(Box.createVerticalStrut(24));

        JButton startButton = new JButton("Nhắn tin");
        AppTheme.styleButton(startButton, true);
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.addActionListener(event -> openPrivateChat(user));
        detailsBody.add(startButton);
        detailsBody.add(Box.createVerticalGlue());
        refreshDetails();
    }

    private void showGroupDetails(Group group) {
        selectedGroup = group;
        selectedUser = null;
        detailsBody.removeAll();
        detailsBody.add(Box.createVerticalGlue());
        JLabel avatar = new JLabel(new AvatarIcon(group.getGroupName(), 86));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailsBody.add(avatar);
        detailsBody.add(Box.createVerticalStrut(16));
        detailsBody.add(detailLabel(group.getGroupName(), 23, true));
        detailsBody.add(Box.createVerticalStrut(6));
        detailsBody.add(detailLabel("Người tạo: " + group.getCreator(), 14, false));
        detailsBody.add(Box.createVerticalStrut(16));

        JTextArea members = new JTextArea();
        members.setEditable(false);
        members.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        members.setForeground(AppTheme.TEXT);
        members.setBackground(AppTheme.SURFACE);
        members.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        members.append("Thành viên (" + group.getMemberCount() + ")\n");
        for (String member : group.getMembers()) {
            members.append("- " + member
                    + (member.equals(group.getCreator()) ? "  (trưởng nhóm)" : "")
                    + "\n");
        }
        JScrollPane memberScroll = new JScrollPane(members);
        memberScroll.setAlignmentX(Component.CENTER_ALIGNMENT);
        memberScroll.setPreferredSize(new Dimension(360, 170));
        memberScroll.setMaximumSize(new Dimension(420, 190));
        memberScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        detailsBody.add(memberScroll);
        detailsBody.add(Box.createVerticalStrut(18));

        JButton openButton = new JButton("Mở nhóm chat");
        AppTheme.styleButton(openButton, true);
        openButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        openButton.addActionListener(event -> openGroupChat(group));
        detailsBody.add(openButton);
        detailsBody.add(Box.createVerticalGlue());
        refreshDetails();
    }

    private JLabel detailLabel(String text, int size, boolean bold) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(bold ? AppTheme.TEXT : AppTheme.MUTED);
        return label;
    }

    private void refreshDetails() {
        detailsBody.revalidate();
        detailsBody.repaint();
    }

    private void createGroup() {
        String name = JOptionPane.showInputDialog(this,
                "Nhập tên nhóm:", "Tạo nhóm mới", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên nhóm không được để trống.",
                    "Tạo nhóm", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (allPeople.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Cần có ít nhất một tài khoản khác trước khi tạo nhóm.",
                    "Tạo nhóm", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JList<User> members = new JList<User>(allPeople.toArray(new User[0]));
        members.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        members.setFixedCellHeight(62);
        members.setCellRenderer(new PersonRenderer());
        JScrollPane memberScroll = new JScrollPane(members);
        memberScroll.setPreferredSize(new Dimension(340, 230));
        int answer = JOptionPane.showConfirmDialog(this, memberScroll,
                "Chọn thành viên cho " + name, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        List<User> selectedMembers = members.getSelectedValuesList();
        if (selectedMembers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Hãy chọn ít nhất một thành viên khác người tạo.",
                    "Tạo nhóm", JOptionPane.WARNING_MESSAGE);
            return;
        }

        GroupManager manager = new GroupManager();
        Group group = manager.createGroup(name, currentUser.getUsername());
        for (User member : selectedMembers) {
            manager.addMemberToGroup(group.getGroupId(), member.getUsername());
        }
        if (!groupStorage.saveGroup(group)) {
            JOptionPane.showMessageDialog(this,
                    "Không thể lưu nhóm vào ChatApp/data/groups.json.",
                    "Lỗi lưu nhóm", JOptionPane.ERROR_MESSAGE);
            return;
        }

        searchField.setText("");
        refreshData();
        showGroups();
        Group savedGroup = null;
        for (Group candidate : allGroups) {
            if (candidate.getGroupId().equals(group.getGroupId())) {
                savedGroup = candidate;
                break;
            }
        }
        if (savedGroup != null) {
            groupsList.setSelectedValue(savedGroup, true);
            showGroupDetails(savedGroup);
            openGroupChat(savedGroup);
        } else {
            showGroupDetails(group);
            openGroupChat(group);
        }
    }

    private void openSelectedConversation() {
        if (selectedUser != null) {
            openPrivateChat(selectedUser);
        } else if (selectedGroup != null) {
            openGroupChat(selectedGroup);
        } else if (peopleList.getSelectedValue() != null) {
            openPrivateChat(peopleList.getSelectedValue());
        } else if (groupsList.getSelectedValue() != null) {
            openGroupChat(groupsList.getSelectedValue());
        }
    }

    private void openPrivateChat(User user) {
        switchingConversation = true;
        try {
            closeActiveConversation();
            disconnectInbox();
            ChatUI chat = new ChatUI(currentUser.getUsername(), user.getUsername());
            setActiveConversation(chat);
        } finally {
            switchingConversation = false;
        }
    }

    private void openGroupChat(Group group) {
        switchingConversation = true;
        try {
            closeActiveConversation();
            disconnectInbox();
            GroupChatUI chat = new GroupChatUI(currentUser.getUsername(), group);
            setActiveConversation(chat);
        } finally {
            switchingConversation = false;
        }
    }

    private void setActiveConversation(JFrame conversation) {
        activeConversation = conversation;
        conversation.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (activeConversation == conversation) {
                    activeConversation = null;
                }
                if (!switchingConversation && !closingHome) {
                    connectInbox();
                }
            }
        });
    }

    private void connectInbox() {
        if (closingHome || inboxSocket != null) {
            return;
        }

        inboxStatus.setText("Đang kết nối máy chủ...");
        ClientSocket socket = new ClientSocket("localhost", 8080);
        if (!socket.connect()) {
            inboxStatus.setText("Không kết nối được máy chủ");
            return;
        }

        inboxSocket = socket;
        socket.sendPacket("{\"type\":\"LOGIN\",\"username\":\""
                + currentUser.getUsername().replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"}");

        ChatInbox inbox = new ChatInbox(currentUser.getUsername(),
                historyManager, groupStorage, new ChatInbox.Listener() {
                    @Override
                    public void onPrivateMessage(String sender) {
                        SwingUtilities.invokeLater(() ->
                                inboxStatus.setText("Tin nhắn mới từ " + sender));
                    }

                    @Override
                    public void onGroupMessage(String groupName) {
                        SwingUtilities.invokeLater(() ->
                                inboxStatus.setText("Tin nhắn nhóm mới: " + groupName));
                    }

                    @Override
                    public void onDisconnected() {
                        if (inboxSocket == socket) {
                            SwingUtilities.invokeLater(() ->
                                    inboxStatus.setText("Mất kết nối máy chủ"));
                        }
                    }
                });
        MessageListener listener = new MessageListener(socket, inbox);
        inboxListener = listener;
        listener.start();
        inboxStatus.setText("Đang trực tuyến");
    }

    private void disconnectInbox() {
        MessageListener listener = inboxListener;
        ClientSocket socket = inboxSocket;
        inboxListener = null;
        inboxSocket = null;
        if (listener != null) {
            listener.stop();
        } else if (socket != null) {
            socket.close();
        }
    }

    private void closeActiveConversation() {
        if (activeConversation != null) {
            JFrame conversation = activeConversation;
            activeConversation = null;
            conversation.dispose();
        }
    }

    private void logout() {
        closingHome = true;
        closeActiveConversation();
        disconnectInbox();
        StatusManager.getInstance().setStatus(currentUser.getUsername(), "OFFLINE");
        UserSession.getInstance().logout();
        new LoginUI().setVisible(true);
        dispose();
    }

    private static class PersonRenderer extends JPanel
            implements javax.swing.ListCellRenderer<User> {

        PersonRenderer() {
            super(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends User> list,
                                                       User user, int index,
                                                       boolean selected, boolean focus) {
            removeAll();
            setOpaque(true);
            setBackground(selected ? AppTheme.PRIMARY_LIGHT : AppTheme.SURFACE);
            add(new JLabel(new AvatarIcon(user.getUsername(), 42)), BorderLayout.WEST);
            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(user.getUsername());
            name.setFont(new Font("Segoe UI", Font.BOLD, 14));
            name.setForeground(AppTheme.TEXT);
            JLabel hint = new JLabel("Tài khoản UDM Chat");
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hint.setForeground(AppTheme.MUTED);
            labels.add(name);
            labels.add(Box.createVerticalStrut(4));
            labels.add(hint);
            add(labels, BorderLayout.CENTER);
            return this;
        }
    }

    private static class GroupRenderer extends JPanel
            implements javax.swing.ListCellRenderer<Group> {

        GroupRenderer() {
            super(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Group> list,
                                                       Group group, int index,
                                                       boolean selected, boolean focus) {
            removeAll();
            setOpaque(true);
            setBackground(selected ? AppTheme.PRIMARY_LIGHT : AppTheme.SURFACE);
            add(new JLabel(new AvatarIcon(group.getGroupName(), 42)), BorderLayout.WEST);
            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(group.getGroupName());
            name.setFont(new Font("Segoe UI", Font.BOLD, 14));
            name.setForeground(AppTheme.TEXT);
            JLabel hint = new JLabel("Tạo bởi " + group.getCreator()
                    + " · " + group.getMemberCount() + " thành viên");
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hint.setForeground(AppTheme.MUTED);
            labels.add(name);
            labels.add(Box.createVerticalStrut(4));
            labels.add(hint);
            add(labels, BorderLayout.CENTER);
            return this;
        }
    }

    private static class AvatarIcon implements Icon {

        private final String label;
        private final int size;
        private final Color color;

        AvatarIcon(String label, int size) {
            this.label = label == null || label.trim().isEmpty()
                    ? "?" : label.trim().substring(0, 1).toUpperCase();
            this.size = size;
            Color[] colors = {
                    new Color(0x3987E8), new Color(0x27A586),
                    new Color(0x8B6BD6), new Color(0xE08B4F),
                    new Color(0xD65D78)
            };
            this.color = colors[Math.floorMod(label == null ? 0 : label.hashCode(), colors.length)];
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(x, y, size, size);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(14, size / 3)));
            int textWidth = g.getFontMetrics().stringWidth(label);
            int textY = y + (size - g.getFontMetrics().getHeight()) / 2
                    + g.getFontMetrics().getAscent();
            g.drawString(label, x + (size - textWidth) / 2, textY);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
