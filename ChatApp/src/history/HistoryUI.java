package history;

import chat.Message;
import group.Group;
import storage.GroupStorage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple viewer for saved chats.
 *
 * The chat windows show history while chatting; this window is useful when
 * the user only wants to review old messages, including after restarting
 * the application. It never opens a socket.
 */
public class HistoryUI extends JFrame {

    private final HistoryManager historyManager = HistoryManager.getInstance();

    private final JComboBox<String> modeCombo =
            new JComboBox<String>(new String[]{"Private", "Group"});
    private final JLabel firstLabel = new JLabel("User A");
    private final JTextField firstField = new JTextField(14);
    private final JLabel secondLabel = new JLabel("User B");
    private final JTextField secondField = new JTextField(18);
    private final JTextArea historyArea = new JTextArea();
    private final JButton groupsButton = new JButton("Groups...");

    public HistoryUI() {
        super("Chat history");
        buildInterface();
        setSize(650, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void buildInterface() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.gridy = 0;
        top.add(new JLabel("Mode:"), constraints);

        constraints.gridx = 1;
        top.add(modeCombo, constraints);

        constraints.gridx = 2;
        top.add(firstLabel, constraints);

        constraints.gridx = 3;
        top.add(firstField, constraints);

        constraints.gridx = 4;
        top.add(secondLabel, constraints);

        constraints.gridx = 5;
        top.add(secondField, constraints);

        historyArea.setEditable(false);
        historyArea.setFont(new Font("Arial", Font.PLAIN, 14));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);

        JPanel buttons = new JPanel();
        JButton loadButton = new JButton("Load history");
        loadButton.addActionListener(event -> loadHistory());
        groupsButton.addActionListener(event -> chooseGroup());
        groupsButton.setVisible(false);
        buttons.add(loadButton);
        buttons.add(groupsButton);

        modeCombo.addActionListener(event -> updateMode());

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(historyArea), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void updateMode() {
        boolean groupMode = "Group".equals(modeCombo.getSelectedItem());
        firstLabel.setText(groupMode ? "Ignore" : "User A");
        firstField.setEnabled(!groupMode);
        secondLabel.setText(groupMode ? "Group ID" : "User B");
        groupsButton.setVisible(groupMode);
        historyArea.setText("");
    }

    /** Reads group ids from groups.json so the user does not have to type them. */
    private void chooseGroup() {
        List<Group> groups = new GroupStorage().loadAll();
        if (groups == null || groups.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No groups are saved yet.");
            return;
        }

        List<String> ids = new ArrayList<String>();
        for (Group group : groups) {
            ids.add(group.getGroupId());
        }

        String[] options = ids.toArray(new String[ids.size()]);
        String chosen = (String) JOptionPane.showInputDialog(
                this, "Choose a group:", "Saved groups",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]
        );
        if (chosen != null) {
            secondField.setText(chosen);
        }
    }

    private void loadHistory() {
        String first = firstField.getText().trim();
        String second = secondField.getText().trim();

        if (second.isEmpty() || (!"Group".equals(modeCombo.getSelectedItem()) && first.isEmpty())) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in the user names or the group id.",
                    "History", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Message> messages;
        if ("Group".equals(modeCombo.getSelectedItem())) {
            messages = historyManager.getGroupHistory(second);
        } else {
            messages = historyManager.getPrivateHistory(first, second);
        }

        String text = historyManager.formatHistory(messages);
        historyArea.setText(text.isEmpty() ? "(no messages)" : text);
        historyArea.setCaretPosition(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HistoryUI().setVisible(true));
    }
}
