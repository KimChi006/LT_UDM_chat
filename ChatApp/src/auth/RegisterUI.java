package auth;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import ui.AppTheme;
import com.google.gson.JsonObject;
import java.io.IOException;

public class RegisterUI extends JDialog {

    private final JTextField usernameField;
    private final JTextField phoneNumberField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;

    public RegisterUI(LoginUI owner) {
        super(owner, "Đăng ký tài khoản", true);
        usernameField = new JTextField(18);
        phoneNumberField = new JTextField(18);
        passwordField = new JPasswordField(18);
        confirmPasswordField = new JPasswordField(18);
        buildInterface();
        setSize(480, 560);
        setLocationRelativeTo(owner);
    }

    private void buildInterface() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(18, 20, 8, 20));
        JLabel title = new JLabel("Tạo tài khoản");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(AppTheme.TEXT);
        JLabel subtitle = new JLabel("Tên hiển thị có thể trùng với người khác");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(AppTheme.MUTED);
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(12, 28, 8, 28));
        usernameField.setPreferredSize(new Dimension(360, 48));
        phoneNumberField.setPreferredSize(new Dimension(360, 48));
        passwordField.setPreferredSize(new Dimension(360, 48));
        confirmPasswordField.setPreferredSize(new Dimension(360, 48));
        AppTheme.styleTextField(usernameField);
        AppTheme.styleTextField(phoneNumberField);
        AppTheme.styleTextField(passwordField);
        AppTheme.styleTextField(confirmPasswordField);
        phoneNumberField.setToolTipText("Nhập đúng 10 chữ số");

        addField(form, "Tên hiển thị", "1f464.png", usernameField);
        addField(form, "Số điện thoại", "1f4de.png", phoneNumberField);
        addField(form, "Mật khẩu", "1f512.png", passwordField);
        addField(form, "Nhập lại mật khẩu", "1f512.png", confirmPasswordField);

        JButton registerButton = createPrimaryButton("Tạo tài khoản");
        registerButton.addActionListener(event -> register());
        JPanel actions = new JPanel();
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createEmptyBorder(4, 20, 16, 20));
        actions.add(registerButton);

        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    private void addField(JPanel panel, String text, String iconFilename,
                          JTextField field) {
        JLabel label = UIIcons.createLabel(text, iconFilename);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(AppTheme.TEXT);

        JPanel fieldGroup = new JPanel(new BorderLayout(0, 6));
        fieldGroup.setOpaque(false);
        fieldGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldGroup.add(label, BorderLayout.NORTH);
        fieldGroup.add(field, BorderLayout.CENTER);
        fieldGroup.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        panel.add(fieldGroup);
        panel.add(Box.createVerticalStrut(8));
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        AppTheme.styleButton(button, true);
        button.setPreferredSize(new Dimension(160, 42));
        return button;
    }

    private void register() {
        String username = usernameField.getText().trim();
        String phoneNumber = phoneNumberField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        if (username.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Tên hiển thị, số điện thoại và mật khẩu không được để trống.",
                    "Đăng ký",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this,
                    "Mật khẩu nhập lại không khớp.", "Đăng ký",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        LoginUI owner = getOwner() instanceof LoginUI ? (LoginUI) getOwner() : null;
        if (owner == null || owner.getServerHost().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Nhập địa chỉ máy chủ trên cửa sổ đăng nhập trước.",
                    "Đăng ký", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            JsonObject response = owner.registerAccount(username, phoneNumber, password);
            if (!"OK".equals(AccountClient.value(response, "status"))) {
                JOptionPane.showMessageDialog(this,
                        AccountClient.value(response, "message"),
                        "Đăng ký", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this,
                    "Đăng ký thành công. Hãy đăng nhập bằng số điện thoại.",
                    "Đăng ký", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Không kết nối được máy chủ " + owner.getServerHost()
                            + ":8080.\nKiểm tra server đang chạy, địa chỉ IP "
                            + "và tường lửa.",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RegisterUI(null).setVisible(true));
    }
}