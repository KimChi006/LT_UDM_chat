package chat;

import group.Group;
import history.HistoryManager;
import storage.GroupStorage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ChatInbox implements MessageListener.PacketHandler {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String username;
    private final HistoryManager historyManager;
    private final GroupStorage groupStorage;
    private final Listener listener;

    public ChatInbox(String username, HistoryManager historyManager,
                     GroupStorage groupStorage, Listener listener) {
        if (username == null || username.trim().isEmpty()
                || historyManager == null || groupStorage == null || listener == null) {
            throw new IllegalArgumentException("Chat inbox dependencies are required.");
        }
        this.username = username;
        this.historyManager = historyManager;
        this.groupStorage = groupStorage;
        this.listener = listener;
    }

    @Override
    public void onPacketReceived(String packet) {
        Map<String, String> fields = ChatPacketParser.parse(packet);
        String type = fields.get("type");
        String sender = fields.get("sender");
        String content = fields.get("content");
        String time = LocalDateTime.now().format(TIME_FORMAT);

        if ("MESSAGE".equals(type)
                && username.equals(fields.get("receiver"))
                && sender != null
                && content != null) {
            historyManager.savePrivateMessage(sender, username, content, time);
            listener.onPrivateMessage(sender);
            return;
        }

        if (!"GROUP_MESSAGE".equals(type)
                || sender == null
                || sender.equalsIgnoreCase(username)
                || content == null) {
            return;
        }

        String groupId = fields.get("groupId");
        if (groupId == null) {
            return;
        }

        List<Group> groups = groupStorage.loadAll();
        if (groups == null) {
            return;
        }
        for (Group group : groups) {
            if (group.getGroupId().equals(groupId) && group.isMember(username)) {
                historyManager.saveGroupMessage(sender, groupId, content, time);
                listener.onGroupMessage(group.getGroupName());
                return;
            }
        }
    }

    @Override
    public void onDisconnected() {
        listener.onDisconnected();
    }

    public interface Listener {
        void onPrivateMessage(String sender);
        void onGroupMessage(String groupName);
        void onDisconnected();
    }
}
