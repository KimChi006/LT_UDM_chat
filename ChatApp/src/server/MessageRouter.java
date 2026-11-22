package server;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes packets to a connected client or to every connected client.
 */
public class MessageRouter {

    private final ConcurrentMap<String, ClientHandler> clients;

    public MessageRouter() {
        this.clients = new ConcurrentHashMap<String, ClientHandler>();
    }

    public MessageRouter(ConcurrentMap<String, ClientHandler> clients) {
        this.clients = clients != null ? clients : new ConcurrentHashMap<String, ClientHandler>();
    }

    public void registerClient(String username, ClientHandler clientHandler) {
        if (username == null || username.trim().isEmpty() || clientHandler == null) {
            return;
        }
        clients.put(username.trim(), clientHandler);
    }

    public void unregisterClient(String username) {
        if (username != null) {
            clients.remove(username);
        }
    }

    public boolean isClientConnected(String username) {
        return username != null && clients.containsKey(username);
    }

    public Map<String, ClientHandler> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    public int getConnectedClientCount() {
        return clients.size();
    }

    public void route(String packet) {
        if (packet == null || packet.trim().isEmpty()) {
            return;
        }

        Map<String, String> fields = parsePacket(packet);
        String receiver = value(fields, "receiver");
        String type = value(fields, "type");

        if ("GROUP_MESSAGE".equalsIgnoreCase(type) || "GROUP".equalsIgnoreCase(type)
                || receiver == null || receiver.trim().isEmpty()) {
            broadcast(packet);
            return;
        }

        ClientHandler receiverHandler = clients.get(receiver);
        if (receiverHandler != null) {
            receiverHandler.sendPacket(packet);
        }
    }

    public void routePacket(String packet) {
        route(packet);
    }

    public void routePacket(Map<String, String> packet) {
        if (packet != null) {
            route(toJson(packet));
        }
    }

    public void routeMessage(String sender, String receiver, String content) {
        String packet = "{\"type\":\"MESSAGE\",\"sender\":\"" + escape(sender)
                + "\",\"receiver\":\"" + escape(receiver)
                + "\",\"content\":\"" + escape(content) + "\"}";
        route(packet);
    }

    public void routeToClient(String username, String packet) {
        if (username == null) {
            return;
        }
        ClientHandler clientHandler = clients.get(username);
        if (clientHandler != null) {
            clientHandler.sendPacket(packet);
        }
    }

    public void broadcast(String packet) {
        if (packet == null) {
            return;
        }
        for (ClientHandler clientHandler : clients.values()) {
            if (clientHandler != null) {
                clientHandler.sendPacket(packet);
            }
        }
    }

    public void broadcast(String packet, List<String> excludedUsernames) {
        if (packet == null) {
            return;
        }
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (excludedUsernames == null || !excludedUsernames.contains(entry.getKey())) {
                entry.getValue().sendPacket(packet);
            }
        }
    }

    public void forwardToClient(String username, String packet) {
        routeToClient(username, packet);
    }

    private static String value(Map<String, String> fields, String key) {
        return fields.get(key);
    }

    private static Map<String, String> parsePacket(String packet) {
        Map<String, String> fields = new ConcurrentHashMap<String, String>();
        if (packet == null) {
            return fields;
        }
        Pattern pattern = Pattern.compile(
                "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher matcher = pattern.matcher(packet);
        while (matcher.find()) {
            fields.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
        return fields;
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static String toJson(Map<String, String> packet) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : packet.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(escape(entry.getKey())).append("\":\"");
            json.append(escape(entry.getValue())).append('"');
            first = false;
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}