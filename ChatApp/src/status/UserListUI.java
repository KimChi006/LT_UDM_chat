package status;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Map;

public class UserListUI extends JPanel implements StatusManager.StatusListener {

    private final DefaultListModel<String> listModel;
    private final JList<String> userList;

    public UserListUI() {
        listModel = new DefaultListModel<String>();
        userList = new JList<String>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userList.setCellRenderer(new StatusCellRenderer());

        setLayout(new BorderLayout());
        add(new JScrollPane(userList), BorderLayout.CENTER);
        StatusManager.getInstance().addListener(this);
        refresh();
    }

    public JList<String> getUserList() {
        return userList;
    }

    public void refresh() {
        final Map<String, String> statuses =
                StatusManager.getInstance().getStatuses();
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (Map.Entry<String, String> entry : statuses.entrySet()) {
                listModel.addElement(entry.getKey());
            }
        });
    }

    public void removeStatusListener() {
        StatusManager.getInstance().removeListener(this);
    }

    @Override
    public void onStatusChanged(String username, String status) {
        refresh();
    }

    private class StatusCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, hasFocus);
            String username = String.valueOf(value);
            String status = StatusManager.getInstance().getStatus(username);
            label.setText(username + "  ["
                    + ("ONLINE".equals(status) ? "Đang hoạt động" : "Ngoại tuyến")
                    + "]");
            label.setForeground("ONLINE".equals(status)
                    ? new Color(36, 145, 87)
                    : new Color(120, 120, 120));
            return label;
        }
    }
}