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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import ui.AppTheme;

public class RegisterUI extends JDialog {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;

    public RegisterUI(LoginUI owner) {
        super(owner, "Create account", true);
        usernameField = new JTextField(18);
        passwordField = new JPasswordField(18);
        confirmPasswordField = new JPasswordField(18);
        buildInterface();
        setSize(430, 330);
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
        JLabel subtitle = new JLabel("Tham gia không gian trò chuyện UDM");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(AppTheme.MUTED);
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 10, 8, 10);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        usernameField.setPreferredSize(new Dimension(220, 40));
        passwordField.setPreferredSize(new Dimension(220, 40));
        confirmPasswordField.setPreferredSize(new Dimension(220, 40));
        AppTheme.styleTextField(usernameField);
        AppTheme.styleTextField(passwordField);
        AppTheme.styleTextField(confirmPasswordField);

        addLabel(form, "Username", "1f464.png", 0, constraints);
        constraints.gridx = 1;
        form.add(usernameField, constraints);

        constraints.gridy = 1;
        constraints.gridx = 0;
        addLabel(form, "Password", "1f512.png", 1, constraints);
        constraints.gridx = 1;
        form.add(passwordField, constraints);

        constraints.gridy = 2;
        constraints.gridx = 0;
        addLabel(form, "Confirm", "1f512.png", 2, constraints);
        constraints.gridx = 1;
        form.add(confirmPasswordField, constraints);

        JButton registerButton = createPrimaryButton("Register");
        registerButton.addActionListener(event -> register());
        JPanel actions = new JPanel();
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createEmptyBorder(4, 20, 16, 20));
        actions.add(registerButton);

        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    private void addLabel(JPanel panel, String text, String iconFilename, int row,
                          GridBagConstraints constraints) {
        constraints.gridy = row;
        constraints.gridx = 0;
        panel.add(UIIcons.createLabel(text, iconFilename), constraints);
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        AppTheme.styleButton(button, true);
        button.setPreferredSize(new Dimension(160, 42));
        return button;
    }

    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Username and password are required.", "Register",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this,
                    "Passwords do not match.", "Register",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        LoginUI owner = (LoginUI) getOwner();
        if (!owner.registerAccount(username, password)) {
            JOptionPane.showMessageDialog(this,
                    "Username already exists.", "Register",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Registration successful.", "Register",
                JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RegisterUI(null).setVisible(true));
    }
}