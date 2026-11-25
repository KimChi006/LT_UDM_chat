package status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StatusManager {

    private static final StatusManager INSTANCE = new StatusManager();

    private final Map<String, String> statuses =
            new ConcurrentHashMap<String, String>();
    private final Set<StatusListener> listeners =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<StatusListener, Boolean>());

    private StatusManager() {
    }

    public static StatusManager getInstance() {
        return INSTANCE;
    }

    public void setStatus(String username, String status) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        String normalizedStatus = normalizeStatus(status);
        statuses.put(username.trim(), normalizedStatus);
        for (StatusListener listener : listeners) {
            listener.onStatusChanged(username.trim(), normalizedStatus);
        }
    }

    public String getStatus(String username) {
        if (username == null) {
            return "OFFLINE";
        }
        return statuses.containsKey(username)
                ? statuses.get(username) : "OFFLINE";
    }

    public Map<String, String> getStatuses() {
        return new LinkedHashMap<String, String>(statuses);
    }

    public List<String> getOnlineUsers() {
        List<String> onlineUsers = new ArrayList<String>();
        for (Map.Entry<String, String> entry : statuses.entrySet()) {
            if ("ONLINE".equals(entry.getValue())) {
                onlineUsers.add(entry.getKey());
            }
        }
        Collections.sort(onlineUsers);
        return onlineUsers;
    }

    public void updateFromPacket(String packet) {
        if (packet == null || packet.trim().isEmpty()) {
            return;
        }
        Map<String, String> fields = parsePacket(packet);
        if (!"STATUS".equalsIgnoreCase(fields.get("type"))) {
            return;
        }
        setStatus(fields.get("username"), fields.get("status"));
    }

    public void addListener(StatusListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(StatusListener listener) {
        listeners.remove(listener);
    }

    public void clear() {
        statuses.clear();
    }

    private String normalizeStatus(String status) {
        if ("ONLINE".equalsIgnoreCase(status)) {
            return "ONLINE";
        }
        return "OFFLINE";
    }

    private Map<String, String> parsePacket(String packet) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        Pattern pattern = Pattern.compile(
                "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher matcher = pattern.matcher(packet);
        while (matcher.find()) {
            fields.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
        return fields;
    }

    private String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    public interface StatusListener {
        void onStatusChanged(String username, String status);
    }
}