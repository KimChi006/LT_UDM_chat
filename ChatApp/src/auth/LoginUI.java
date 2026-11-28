package auth;

import chat.ChatHomeUI;
import model.User;
import status.StatusManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginUI extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;

    public LoginUI() {
        super("Chat App");
        usernameField = new JTextField(18);
        passwordField = new JPasswordField(18);
        buildInterface();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(430, 390);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(34, 42, 28, 42));

        JLabel title = new JLabel("UDM Chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(0x0068FF));

        JLabel subtitle = new JLabel("Chào mừng bạn trở lại");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(new Color(0x7A8796));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(6));
        heading.add(subtitle);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 0, 7, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        form.add(heading, constraints);

        constraints.gridy = 1;
        JLabel usernameLabel = new JLabel("Tài khoản");
        usernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        usernameLabel.setForeground(new Color(0x1F2D3D));
        form.add(usernameLabel, constraints);

        constraints.gridy = 2;
        form.add(usernameField, constraints);

        constraints.gridy = 3;
        JLabel passwordLabel = new JLabel("Mật khẩu");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        passwordLabel.setForeground(new Color(0x1F2D3D));
        form.add(passwordLabel, constraints);

        constraints.gridy = 4;
        form.add(passwordField, constraints);

        usernameField.setPreferredSize(new Dimension(300, 42));
        passwordField.setPreferredSize(new Dimension(300, 42));
        ui.AppTheme.styleTextField(usernameField);
        ui.AppTheme.styleTextField(passwordField);

        JButton loginButton = createPrimaryButton("Login");
        JButton registerButton = createSecondaryButton("Create account");
        loginButton.addActionListener(event -> login());
        registerButton.addActionListener(event ->
                new RegisterUI(this).setVisible(true));

        JPanel actions = new JPanel();
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        actions.add(loginButton);
        actions.add(javax.swing.Box.createHorizontalStrut(10));
        actions.add(registerButton);

        root.add(form, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        add(root);
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        styleButton(button, new Color(0x0068FF), Color.WHITE);
        return button;
    }

    private JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        styleButton(button, new Color(238, 242, 248), new Color(35, 55, 90));
        return button;
    }

    private void styleButton(JButton button, Color background, Color foreground) {
        button.setPreferredSize(new Dimension(145, 42));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setBorder(new ui.AppTheme.RoundedBorder(background, 10));
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter username and password.", "Login",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        User user = authenticate(username, password);
        if (user == null) {
            JOptionPane.showMessageDialog(this,
                    "Invalid username or password.", "Login",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        user.setStatus("ONLINE");
        UserSession.getInstance().login(user);
        StatusManager.getInstance().setStatus(username, "ONLINE");
        new ChatHomeUI(user).setVisible(true);
        dispose();
    }

    public boolean registerAccount(String username, String password) {
        List<User> users = loadUsers();
        for (User user : users) {
            if (username.equalsIgnoreCase(user.getUsername())) {
                return false;
            }
        }
        users.add(new User(username, password, "OFFLINE"));
        return saveUsers(users);
    }

    private User authenticate(String username, String password) {
        for (User user : loadUsers()) {
            if (username.equalsIgnoreCase(user.getUsername())
                    && password.equals(user.getPassword())) {
                return user;
            }
        }
        return null;
    }

    private Path getUserStoragePath() {
        Path current = Paths.get(System.getProperty("user.dir"));
        Path chatApp = current.resolve("ChatApp").resolve("data").resolve("users.json");
        if (Files.exists(chatApp)) {
            return chatApp;
        }
        return current.resolve("data").resolve("users.json");
    }

    private List<User> loadUsers() {
        List<User> users = new ArrayList<User>();
        Path path = getUserStoragePath();
        if (!Files.exists(path)) {
            return users;
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                return users;
            }
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1).trim();
            }
            if (json.isEmpty()) {
                return users;
            }
            Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
            Matcher objectMatcher = objectPattern.matcher(json);
            while (objectMatcher.find()) {
                Map<String, String> fields = parseJsonObject(objectMatcher.group());
                String username = fields.get("username");
                String password = fields.get("password");
                String status = fields.get("status");
                if (username != null) {
                    users.add(new User(username, password == null ? "" : password,
                            status == null ? "OFFLINE" : status));
                }
            }
        } catch (IOException exception) {
            System.err.println("Cannot read users: " + exception.getMessage());
        }
        return users;
    }

    private boolean saveUsers(List<User> users) {
        Path path = getUserStoragePath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8)) {
                writer.write("[\n");
                for (int index = 0; index < users.size(); index++) {
                    User user = users.get(index);
                    writer.write("  {\"username\":\"" + escapeJson(user.getUsername())
                            + "\",\"password\":\"" + escapeJson(user.getPassword())
                            + "\",\"status\":\"" + escapeJson(user.getStatus()) + "\"}");
                    writer.write(index == users.size() - 1 ? "\n" : ",\n");
                }
                writer.write("]\n");
            }
            return true;
        } catch (IOException exception) {
            System.err.println("Cannot save users: " + exception.getMessage());
            return false;
        }
    }

    private Map<String, String> parseJsonObject(String object) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        Pattern pattern = Pattern.compile(
                "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher matcher = pattern.matcher(object);
        while (matcher.find()) {
            fields.put(unescapeJson(matcher.group(1)),
                    unescapeJson(matcher.group(2)));
        }
        return fields;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginUI loginUI = new LoginUI();
            loginUI.setVisible(true);
        });
    }
}