package history;

import group.Group;
import group.GroupChatUI;
import group.GroupManager;
import storage.GroupStorage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small helper window used to test TV4 group history.
 *
 * It only wires together classes that already exist: GroupManager creates the
 * group, GroupStorage saves it, and GroupChatUI opens it. It does not replace
 * any TV2 screen and does not change the server protocol.
 */
public class GroupTestUI extends JFrame {

    private final JTextField nameField = new JTextField(18);
    private final JTextField creatorField = new JTextField(14);
    private final JTextField membersField = new JTextField(24);
    private final JLabel groupIdLabel = new JLabel("Group ID: (not created yet)");

    private final GroupManager groupManager = new GroupManager();
    private final GroupStorage groupStorage = new GroupStorage();

    public GroupTestUI() {
        super("Create group - TV4 test");
        buildInterface();
        setSize(560, 260);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void buildInterface() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel("Group name:"), constraints);

        constraints.gridx = 1;
        form.add(nameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        form.add(new JLabel("Creator:"), constraints);

        constraints.gridx = 1;
        form.add(creatorField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        form.add(new JLabel("Members:"), constraints);

        constraints.gridx = 1;
        form.add(membersField, constraints);

        constraints.gridx = 1;
        constraints.gridy = 3;
        form.add(new JLabel("Separate usernames with commas"), constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        form.add(groupIdLabel, constraints);

        JButton createButton = new JButton("Create and open chat");
        createButton.addActionListener(event -> createGroup());

        JButton openButton = new JButton("Open saved group");
        openButton.addActionListener(event -> openSavedGroup());

        JPanel buttons = new JPanel();
        buttons.add(createButton);
        buttons.add(openButton);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void createGroup() {
        String groupName = nameField.getText().trim();
        String creator = creatorField.getText().trim();

        if (groupName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Group name cannot be empty.",
                    "Create group", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (creator.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Creator username cannot be empty.",
                    "Create group", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> members = parseMembers(membersField.getText());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter at least one member (usernames separated by commas).",
                    "Create group", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // The creator is added by GroupManager/Group, so it must not be added twice.
        boolean hasOtherMember = false;
        for (String member : members) {
            if (!member.equalsIgnoreCase(creator)) {
                hasOtherMember = true;
                break;
            }
        }
        if (!hasOtherMember) {
            JOptionPane.showMessageDialog(this,
                    "Enter at least one member besides the creator.",
                    "Create group", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Group group = groupManager.createGroup(groupName, creator);
        if (group == null) {
            JOptionPane.showMessageDialog(this, "Cannot create group.",
                    "Create group", JOptionPane.ERROR_MESSAGE);
            return;
        }

        for (String member : members) {
            if (!member.equalsIgnoreCase(creator)) {
                groupManager.addMemberToGroup(group.getGroupId(), member);
            }
        }

        // Saving through TV4 storage lets the second client and HistoryUI find this group.
        if (!groupStorage.saveGroup(group)) {
            JOptionPane.showMessageDialog(this,
                    "Group was created but groups.json could not be saved.\n"
                            + "The second client will need the Group ID typed manually.",
                    "Create group", JOptionPane.WARNING_MESSAGE);
        }

        groupIdLabel.setText("Group ID: " + group.getGroupId());
        JOptionPane.showMessageDialog(this,
                "Group created.\nGroup ID: " + group.getGroupId()
                        + "\nMembers: " + group.getMembers(),
                "Create group", JOptionPane.INFORMATION_MESSAGE);

        new GroupChatUI(creator, group);
    }

    private void openSavedGroup() {
        List<Group> groups = groupStorage.loadAll();
        if (groups == null || groups.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved groups yet. Create one first.",
                    "Open group", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String username = JOptionPane.showInputDialog(this,
                "Your username:", "Open group", JOptionPane.QUESTION_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            return;
        }

        Map<String, Group> choices = new LinkedHashMap<String, Group>();
        for (Group group : groups) {
            choices.put(group.getGroupId() + " - " + group.getGroupName(), group);
        }

        String[] options = choices.keySet().toArray(new String[choices.size()]);
        Object selected = JOptionPane.showInputDialog(this,
                "Choose a group:", "Open group", JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (selected == null) {
            return;
        }

        Group chosen = choices.get(String.valueOf(selected));
        if (chosen != null) {
            new GroupChatUI(username.trim(), chosen);
        }
    }

    /** Package-private so the console test can check parsing and de-duplication. */
    static List<String> parseMembers(String text) {
        List<String> result = new ArrayList<String>();
        if (text == null) {
            return result;
        }

        String[] parts = text.split(",");
        for (String part : parts) {
            String member = part.trim();
            if (member.isEmpty() || containsIgnoreCase(result, member)) {
                continue;
            }
            result.add(member);
        }
        return result;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GroupTestUI().setVisible(true));
    }
}
