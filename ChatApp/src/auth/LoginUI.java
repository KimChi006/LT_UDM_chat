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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import java.io.IOException;

public class LoginUI extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JTextField serverHostField;

    public LoginUI() {
        super("UDM Chat - Đăng nhập");
        usernameField = new JTextField(18);
        passwordField = new JPasswordField(18);
        String defaultHost = System.getenv("UDM_CHAT_SERVER_HOST");
        serverHostField = new JTextField(
                defaultHost == null || defaultHost.trim().isEmpty()
                        ? "localhost" : defaultHost.trim(), 18);
        buildInterface();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(440, 520);
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
        JLabel usernameLabel = new JLabel("Số điện thoại");
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

        constraints.gridy = 5;
        JLabel serverLabel = new JLabel("Máy chủ");
        serverLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        serverLabel.setForeground(new Color(0x1F2D3D));
        form.add(serverLabel, constraints);

        constraints.gridy = 6;
        form.add(serverHostField, constraints);

        usernameField.setPreferredSize(new Dimension(300, 48));
        passwordField.setPreferredSize(new Dimension(300, 48));
        serverHostField.setPreferredSize(new Dimension(300, 48));
        ui.AppTheme.styleTextField(usernameField);
        ui.AppTheme.styleTextField(passwordField);
        ui.AppTheme.styleTextField(serverHostField);
        usernameField.setToolTipText("Nhập số điện thoại đã đăng ký");
        serverHostField.setToolTipText(
                "Nhập localhost, địa chỉ IP hoặc tên máy chủ VPN");

        JButton loginButton = createPrimaryButton("Đăng nhập");
        JButton registerButton = createSecondaryButton("Đăng ký");
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
        String phoneNumber = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String serverHost = getServerHost();
        if (phoneNumber.isEmpty() || password.isEmpty() || serverHost.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Nhập số điện thoại, mật khẩu và địa chỉ máy chủ.",
                    "Đăng nhập",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JsonObject response;
        try {
            response = new AccountClient(serverHost)
                    .authenticate(phoneNumber, password);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không kết nối được máy chủ " + serverHost
                            + ":8080.\nKiểm tra máy chủ đang chạy, địa chỉ IP "
                            + "và tường lửa.",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!"OK".equals(AccountClient.value(response, "status"))) {
            JOptionPane.showMessageDialog(this,
                    AccountClient.value(response, "message"),
                    "Đăng nhập", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String canonicalUsername = AccountClient.value(response, "username");
        if (canonicalUsername == null || canonicalUsername.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Máy chủ trả về tài khoản không hợp lệ.",
                    "Lỗi máy chủ", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String canonicalPhone = AccountClient.value(response, "phoneNumber");
        User user = new User(canonicalUsername, canonicalPhone, password, "ONLINE");
        UserSession.getInstance().login(user);
        StatusManager.getInstance().setStatus(user.getIdentity(), "ONLINE");
        new ChatHomeUI(user, serverHost).setVisible(true);
        dispose();
    }

    public String getServerHost() {
        return serverHostField.getText().trim();
    }

    public JsonObject registerAccount(String username, String phoneNumber,
                                      String password)
            throws IOException {
        return new AccountClient(getServerHost())
                .register(username, phoneNumber, password);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginUI loginUI = new LoginUI();
            loginUI.setVisible(true);
        });
    }
}