package chat;

import auth.AccountClient;
import auth.LoginUI;
import auth.PhoneNumber;
import auth.UserSession;
import com.google.gson.JsonObject;
import history.HistoryManager;
import group.Group;
import group.GroupChatUI;
import group.GroupManager;
import model.User;
import status.StatusManager;
import storage.GroupStorage;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ChatHomeUI extends JFrame implements StatusManager.StatusListener {

    private final User currentUser;
    private final GroupStorage groupStorage = new GroupStorage();
    private final AccountClient accountClient;
    private final String serverHost;
    private final HistoryManager historyManager = HistoryManager.getInstance();
    private final DefaultListModel<User> peopleModel = new DefaultListModel<User>();
    private final DefaultListModel<Group> groupsModel = new DefaultListModel<Group>();
    private final JList<User> peopleList = new JList<User>(peopleModel);
    private final JList<Group> groupsList = new JList<Group>(groupsModel);
    private final JPanel listCards = new JPanel(new CardLayout());
    private final JPanel detailsBody = new JPanel();
    private final JPanel detailsCards = new JPanel(new CardLayout());
    private final JPanel conversationBody = new JPanel(new BorderLayout());
    private final JTextField searchField = new JTextField();
    private final JToggleButton peopleButton = new JToggleButton("Bạn bè");
    private final JToggleButton groupsButton = new JToggleButton("Nhóm");
    private final JButton createGroupButton = new JButton("+ Tạo nhóm");
    private final JButton addFriendButton = new JButton("+ Kết bạn");
    private final JButton friendRequestsButton = new JButton("Lời mời (0)");
    private final JLabel inboxStatus = new JLabel("Chưa kết nối máy chủ");

    private User selectedUser;
    private Group selectedGroup;
    private ChatUI embeddedChat;
    private GroupChatUI embeddedGroupChat;
    private volatile ClientSocket inboxSocket;
    private MessageListener inboxListener;
    private boolean switchingConversation;
    private boolean closingHome;

    public ChatHomeUI(User user) {
        this(user, "localhost");
    }

    public ChatHomeUI(User user, String serverHost) {
        super("UDM Chat");
        if (user == null || user.getUsername() == null
                || serverHost == null || serverHost.trim().isEmpty()) {
            throw new IllegalArgumentException("A signed-in user is required.");
        }
        currentUser = user;
        this.serverHost = serverHost.trim();
        accountClient = new AccountClient(this.serverHost);
        StatusManager.getInstance().addListener(this);
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

        JPanel workspace = new JPanel(new BorderLayout());
        JPanel conversationList = createSidebar();
        conversationList.setPreferredSize(new Dimension(310, 0));
        workspace.add(conversationList, BorderLayout.WEST);
        workspace.add(createDetailsPanel(), BorderLayout.CENTER);
        root.add(createNavigationPanel(), BorderLayout.WEST);
        root.add(workspace, BorderLayout.CENTER);

        createGroupButton.addActionListener(event -> createGroup());
        addFriendButton.addActionListener(event -> addFriend());
        friendRequestsButton.addActionListener(event -> showFriendRequests());
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

        groupsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupsList.setFixedCellHeight(84);
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
                closeEmbeddedChat();
                disconnectInbox();
                StatusManager.getInstance().removeListener(ChatHomeUI.this);
                StatusManager.getInstance().setStatus(currentUser.getIdentity(), "OFFLINE");
                UserSession.getInstance().logout();
            }
        });

        showWelcome();
    }

    private JPanel createNavigationPanel() {
        JPanel navigationPanel = new JPanel();
        navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
        navigationPanel.setBackground(AppTheme.SURFACE);
        navigationPanel.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, AppTheme.BORDER));
        navigationPanel.setPreferredSize(new Dimension(104, 0));

        JLabel brand = new JLabel("UDM", SwingConstants.CENTER);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brand.setForeground(AppTheme.PRIMARY);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        brand.setBorder(BorderFactory.createEmptyBorder(18, 8, 20, 8));
        navigationPanel.add(brand);

        ButtonGroup navigation = new ButtonGroup();
        navigation.add(peopleButton);
        navigation.add(groupsButton);
        styleToggle(peopleButton, true);
        styleToggle(groupsButton, false);
        peopleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        groupsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        peopleButton.setMaximumSize(new Dimension(92, 40));
        groupsButton.setMaximumSize(new Dimension(92, 40));
        navigationPanel.add(peopleButton);
        navigationPanel.add(Box.createVerticalStrut(6));
        navigationPanel.add(groupsButton);
        navigationPanel.add(Box.createVerticalStrut(18));

        for (JButton button : new JButton[]{
                addFriendButton, friendRequestsButton, createGroupButton}) {
            AppTheme.styleButton(button, false);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new Dimension(96, 42));
            navigationPanel.add(button);
            navigationPanel.add(Box.createVerticalStrut(6));
        }

        navigationPanel.add(Box.createVerticalGlue());
        JButton logoutButton = new JButton("Đăng xuất");
        AppTheme.styleButton(logoutButton, false);
        logoutButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutButton.setMaximumSize(new Dimension(96, 42));
        logoutButton.setToolTipText("Đăng xuất");
        logoutButton.addActionListener(event -> logout());
        navigationPanel.add(logoutButton);
        navigationPanel.add(Box.createVerticalStrut(12));
        return navigationPanel;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 0));
        sidebar.setBackground(AppTheme.SURFACE);
        sidebar.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, AppTheme.BORDER));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBackground(AppTheme.SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder(18, 16, 12, 16));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Trò chuyện");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
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

        header.add(titleBlock, BorderLayout.CENTER);

        JPanel searchPanel = new JPanel(new BorderLayout(0, 10));
        searchPanel.setBackground(AppTheme.SURFACE);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        AppTheme.styleTextField(searchField);
        searchField.setToolTipText("Tìm theo tên hiển thị, số điện thoại hoặc tên nhóm");
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(AppTheme.SURFACE);
        top.add(header, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.SOUTH);

        peopleList.setBackground(AppTheme.SURFACE);
        groupsList.setBackground(AppTheme.SURFACE);
        listCards.add(new JScrollPane(peopleList), "people");
        listCards.add(new JScrollPane(groupsList), "groups");

        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(listCards, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder());
        detailsBody.setOpaque(false);
        detailsBody.setLayout(new BoxLayout(detailsBody, BoxLayout.Y_AXIS));
        conversationBody.setBackground(AppTheme.CHAT_BACKGROUND);
        detailsCards.add(detailsBody, "details");
        detailsCards.add(conversationBody, "conversation");
        panel.add(detailsCards, BorderLayout.CENTER);
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
        refreshData();
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
        List<Group> groups = groupStorage.loadAll();
        if (groups == null) {
            JOptionPane.showMessageDialog(this,
                    "Không thể đọc dữ liệu nhóm trong ChatApp/data/groups.json.",
                    "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JsonObject directory;
        JsonObject friendData;
        try {
            directory = accountClient.listUsers(currentUser.getIdentity(),
                    currentUser.getPassword());
            friendData = accountClient.getFriendData(currentUser.getIdentity(),
                    currentUser.getPassword());
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        if (!"OK".equals(AccountClient.value(directory, "status"))
                || !"OK".equals(AccountClient.value(friendData, "status"))) {
            showServerError(firstMessage(directory, friendData));
            return;
        }

        List<User> users;
        List<String> friends;
        List<String> requests;
        try {
            users = AccountClient.userList(directory);
            friends = AccountClient.stringList(friendData, "friends");
            requests = AccountClient.stringList(friendData, "requests");
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }

        friendRequestsButton.setText("Lời mời (" + requests.size() + ")");
        Collections.sort(groups, Comparator.comparing(
                group -> group.getGroupName() == null ? "" : group.getGroupName(),
                String.CASE_INSENSITIVE_ORDER));
        allUsers.clear();
        allPeople.clear();
        allGroups.clear();
        for (User user : users) {
            if (user.getIdentity().equalsIgnoreCase(currentUser.getIdentity())) {
                continue;
            }
            allUsers.add(user);
            if (containsIgnoreCase(friends, user.getIdentity())
                    || containsIgnoreCase(friends, user.getUsername())) {
                allPeople.add(user);
            }
        }
        for (Group group : groups) {
            if (group.isMember(currentUser.getIdentity())
                    || group.isMember(currentUser.getUsername())) {
                allGroups.add(group);
            }
        }
        filterLists();
    }

    private final List<User> allUsers = new ArrayList<User>();
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
            if (user.getUsername().toLowerCase(Locale.ROOT).contains(query)
                    || (user.getPhoneNumber() != null
                    && user.getPhoneNumber().contains(query))) {
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
        if (embeddedChat == null && embeddedGroupChat == null) {
            showWelcome();
        }
    }

    private void showWelcome() {
        closeActiveConversation();
        closeEmbeddedChat();
        ((CardLayout) detailsCards.getLayout()).show(detailsCards, "details");
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
        openPrivateChat(user);
    }

    private void showGroupDetails(Group group) {
        selectedGroup = group;
        selectedUser = null;
        openGroupChat(group);
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

    private void addFriend() {
        String phoneNumberInput = JOptionPane.showInputDialog(this,
                "Nhập số điện thoại muốn kết bạn:", "Kết bạn",
                JOptionPane.PLAIN_MESSAGE);
        if (phoneNumberInput == null) {
            return;
        }
        String phoneNumber = PhoneNumber.normalize(phoneNumberInput);
        if (phoneNumber == null) {
            JOptionPane.showMessageDialog(this, "Số điện thoại không hợp lệ.",
                    "Kết bạn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JsonObject directory;
        JsonObject friendData;
        try {
            directory = accountClient.listUsers(currentUser.getIdentity(),
                    currentUser.getPassword());
            friendData = accountClient.getFriendData(currentUser.getIdentity(),
                    currentUser.getPassword());
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        if (!"OK".equals(AccountClient.value(directory, "status"))
                || !"OK".equals(AccountClient.value(friendData, "status"))) {
            showServerError(firstMessage(directory, friendData));
            return;
        }

        List<User> users;
        List<String> friends;
        try {
            users = AccountClient.userList(directory);
            friends = AccountClient.stringList(friendData, "friends");
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        User requestedUser = null;
        for (User user : users) {
            if (phoneNumber.equals(user.getPhoneNumber())) {
                requestedUser = user;
                break;
            }
        }
        if (requestedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy tài khoản này trên máy chủ.",
                    "Kết bạn", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (requestedUser.getIdentity().equalsIgnoreCase(currentUser.getIdentity())) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự kết bạn.",
                    "Kết bạn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (containsIgnoreCase(friends, requestedUser.getIdentity())) {
            JOptionPane.showMessageDialog(this, "Hai bạn đã là bạn bè.",
                    "Kết bạn", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JsonObject response;
        try {
            response = accountClient.sendFriendRequest(
                    currentUser.getIdentity(), currentUser.getPassword(),
                    requestedUser.getIdentity());
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        boolean success = "OK".equals(AccountClient.value(response, "status"));
        JOptionPane.showMessageDialog(this,
                AccountClient.value(response, "message"),
                success ? "Đã gửi lời mời" : "Kết bạn",
                success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void showFriendRequests() {
        JsonObject friendData;
        try {
            friendData = accountClient.getFriendData(currentUser.getIdentity(),
                    currentUser.getPassword());
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        if (!"OK".equals(AccountClient.value(friendData, "status"))) {
            showServerError(AccountClient.value(friendData, "message"));
            return;
        }
        List<String> requests;
        try {
            requests = AccountClient.stringList(friendData, "requests");
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        friendRequestsButton.setText("Lời mời (" + requests.size() + ")");
        if (requests.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn chưa có lời mời kết bạn nào.",
                    "Lời mời kết bạn", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Object[] requestChoices = requests.stream()
                .map(identity -> displayNameForIdentity(identity) + " (" + identity + ")")
                .toArray();
        Object selected = JOptionPane.showInputDialog(this,
                "Chọn người gửi lời mời:", "Lời mời kết bạn",
                JOptionPane.QUESTION_MESSAGE, null, requestChoices,
                requestChoices[0]);
        if (selected == null) {
            return;
        }
        int selectedIndex = java.util.Arrays.asList(requestChoices).indexOf(selected);
        if (selectedIndex < 0 || selectedIndex >= requests.size()) {
            return;
        }
        String requester = requests.get(selectedIndex);
        Object[] choices = {"Chấp nhận", "Từ chối", "Để sau"};
        int answer = JOptionPane.showOptionDialog(this,
                "Chấp nhận lời mời từ " + displayNameForIdentity(requester) + "?",
                "Lời mời kết bạn", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (answer == 2 || answer == JOptionPane.CLOSED_OPTION) {
            return;
        }

        JsonObject response;
        try {
            response = accountClient.respondFriendRequest(
                    currentUser.getIdentity(), currentUser.getPassword(),
                    requester, answer == 0);
        } catch (IOException exception) {
            showServerError(exception.getMessage());
            return;
        }
        if (!"OK".equals(AccountClient.value(response, "status"))) {
            showServerError(AccountClient.value(response, "message"));
            return;
        }
        refreshData();
    }

    private void showServerError(String details) {
        String message = details == null || details.trim().isEmpty()
                ? "Không thể hoàn tất thao tác trên máy chủ."
                : details;
        JOptionPane.showMessageDialog(this,
                message + "\nMáy chủ: " + serverHost + ":8080",
                "Lỗi máy chủ", JOptionPane.ERROR_MESSAGE);
    }

    private String firstMessage(JsonObject first, JsonObject second) {
        String message = AccountClient.value(first, "message");
        return message == null || message.isEmpty()
                ? AccountClient.value(second, "message") : message;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String item : values) {
            if (item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String displayNameForIdentity(String identity) {
        if (currentUser.getIdentity().equalsIgnoreCase(identity)) {
            return currentUser.getUsername();
        }
        for (User user : allUsers) {
            if (user.getIdentity().equalsIgnoreCase(identity)) {
                return user.getUsername();
            }
        }
        return identity;
    }

    private java.util.Map<String, String> getDisplayNames() {
        java.util.Map<String, String> names = new java.util.HashMap<String, String>();
        names.put(currentUser.getIdentity(), currentUser.getUsername());
        for (User user : allUsers) {
            names.put(user.getIdentity(), user.getUsername());
        }
        return names;
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
                    "Cần có ít nhất một người bạn trước khi tạo nhóm.",
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
        Group group = manager.createGroup(name, currentUser.getIdentity());
        for (User member : selectedMembers) {
            manager.addMemberToGroup(group.getGroupId(), member.getIdentity());
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
            openGroupChat(savedGroup);
        } else {
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
        if (embeddedChat != null
                && embeddedChat.getReceiver().equals(user.getIdentity())) {
            return;
        }
        switchingConversation = true;
        try {
            closeActiveConversation();
            closeEmbeddedChat();
            disconnectInbox();
            ChatUI chat = new ChatUI(currentUser, user, serverHost);
            embeddedChat = chat;
            conversationBody.removeAll();
            conversationBody.add(chat, BorderLayout.CENTER);
            ((CardLayout) detailsCards.getLayout()).show(detailsCards, "conversation");
            conversationBody.revalidate();
            conversationBody.repaint();
        } finally {
            switchingConversation = false;
        }
    }

    private void openGroupChat(Group group) {
        if (embeddedGroupChat != null
                && embeddedGroupChat.getGroupId().equals(group.getGroupId())) {
            return;
        }
        switchingConversation = true;
        try {
            closeEmbeddedChat();
            closeActiveConversation();
            disconnectInbox();
            GroupChatUI chat = GroupChatUI.createEmbedded(
                    currentUser.getIdentity(), group, serverHost, getDisplayNames());
            embeddedGroupChat = chat;
            conversationBody.removeAll();
            conversationBody.add(chat, BorderLayout.CENTER);
            ((CardLayout) detailsCards.getLayout()).show(detailsCards, "conversation");
            conversationBody.revalidate();
            conversationBody.repaint();
        } finally {
            switchingConversation = false;
        }
    }

    private void connectInbox() {
        if (closingHome || inboxSocket != null) {
            return;
        }

        inboxStatus.setText("Đang kết nối máy chủ...");
        ClientSocket socket = new ClientSocket(serverHost, 8080);
        if (!socket.connect()) {
            inboxStatus.setText("Không kết nối được máy chủ");
            return;
        }

        inboxSocket = socket;
        socket.sendPacket("{\"type\":\"LOGIN\",\"username\":\""
                + currentUser.getIdentity().replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"}");

        ChatInbox inbox = new ChatInbox(currentUser.getIdentity(),
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
                    public void onReceipt(String type, String receiver, String messageId) {
                        socket.sendPacket("{\"type\":\"" + type + "\","
                                + "\"sender\":\"" + currentUser.getIdentity() + "\","
                                + "\"receiver\":\"" + receiver + "\","
                                + "\"messageId\":\"" + messageId + "\"}");
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
        if (embeddedGroupChat != null) {
            GroupChatUI chat = embeddedGroupChat;
            embeddedGroupChat = null;
            chat.closeConversation();
            conversationBody.remove(chat);
            if (!switchingConversation && !closingHome) {
                connectInbox();
            }
        }
    }

    private void closeEmbeddedChat() {
        if (embeddedChat == null) {
            return;
        }
        ChatUI chat = embeddedChat;
        embeddedChat = null;
        chat.closeConversation();
        conversationBody.removeAll();
        ((CardLayout) detailsCards.getLayout()).show(detailsCards, "details");
        if (!switchingConversation && !closingHome) {
            connectInbox();
        }
    }

    private void logout() {
        closingHome = true;
        closeActiveConversation();
        closeEmbeddedChat();
        disconnectInbox();
        StatusManager.getInstance().removeListener(this);
        StatusManager.getInstance().setStatus(currentUser.getIdentity(), "OFFLINE");
        UserSession.getInstance().logout();
        new LoginUI().setVisible(true);
        dispose();
    }

    @Override
    public void onStatusChanged(String identity, String status) {
        SwingUtilities.invokeLater(() -> {
            peopleList.repaint();
            groupsList.repaint();
        });
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
            boolean online = "ONLINE".equals(
                    StatusManager.getInstance().getStatus(user.getIdentity()));
            JLabel hint = new JLabel((online ? "● Đang hoạt động" : "● Ngoại tuyến")
                    + (user.getPhoneNumber() == null ? "" : " · " + user.getPhoneNumber()));
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hint.setForeground(online ? new Color(0x22C55E) : new Color(0x8A929E));
            labels.add(name);
            labels.add(Box.createVerticalStrut(4));
            labels.add(hint);
            add(labels, BorderLayout.CENTER);
            return this;
        }
    }

    private class GroupRenderer extends JPanel
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

            JPanel adminLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            adminLine.setOpaque(false);
            JLabel adminCaption = new JLabel("Quản trị:");
            adminCaption.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            adminCaption.setForeground(AppTheme.MUTED);
            adminLine.add(adminCaption);
            adminLine.add(presenceLabel(group.getCreator()));

            JPanel membersLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            membersLine.setOpaque(false);
            JLabel membersCaption = new JLabel("Thành viên:");
            membersCaption.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            membersCaption.setForeground(AppTheme.MUTED);
            membersLine.add(membersCaption);
            int visibleCount = 0;
            int hiddenCount = 0;
            for (String member : group.getMembers()) {
                if (group.getCreator() != null
                        && member.equalsIgnoreCase(group.getCreator())) {
                    continue;
                }
                if (visibleCount < 2) {
                    membersLine.add(presenceLabel(member));
                    visibleCount++;
                } else {
                    hiddenCount++;
                }
            }
            if (hiddenCount > 0) {
                JLabel moreMembers = new JLabel("+" + hiddenCount);
                moreMembers.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                moreMembers.setForeground(AppTheme.MUTED);
                membersLine.add(moreMembers);
            } else if (visibleCount == 0) {
                JLabel noMembers = new JLabel("Chưa có");
                noMembers.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                noMembers.setForeground(AppTheme.MUTED);
                membersLine.add(noMembers);
            }

            labels.add(name);
            labels.add(Box.createVerticalStrut(2));
            labels.add(adminLine);
            labels.add(membersLine);
            add(labels, BorderLayout.CENTER);
            return this;
        }

        private JLabel presenceLabel(String identity) {
            boolean online = "ONLINE".equals(
                    StatusManager.getInstance().getStatus(identity));
            JLabel label = new JLabel("● "
                    + (identity == null ? "Không rõ" : displayNameForIdentity(identity)));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            label.setForeground(online
                    ? new Color(0x22C55E) : new Color(0x8A929E));
            return label;
        }
    }

    public static class AvatarIcon implements Icon {

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
