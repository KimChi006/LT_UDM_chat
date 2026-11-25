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
        setSize(380, 260);
        setLocationRelativeTo(owner);
    }

    private void buildInterface() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 10, 8, 10);
        constraints.fill = GridBagConstraints.HORIZONTAL;

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
        registerButton.setIcon(UIIcons.loadIcon("2795.png"));
        registerButton.addActionListener(event -> register());
        JPanel actions = new JPanel();
        actions.setBackground(Color.WHITE);
        actions.add(registerButton);

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
        button.setPreferredSize(new Dimension(140, 38));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(58, 108, 217));
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
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