package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles one socket connection on its own server thread.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final MessageRouter messageRouter;
    private final ServerMain serverMain;
    private final AccountService accountService;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean running;
    private String username;

    public ClientHandler(Socket socket, MessageRouter messageRouter) {
        this(socket, messageRouter, null);
    }

    public ClientHandler(Socket socket, ServerMain serverMain) {
        this(socket, serverMain != null ? serverMain.getMessageRouter() : null, serverMain);
    }

    public ClientHandler(Socket socket, MessageRouter messageRouter, ServerMain serverMain) {
        this.socket = socket;
        this.messageRouter = messageRouter != null ? messageRouter : new MessageRouter();
        this.serverMain = serverMain;
        this.accountService = serverMain == null
                ? new AccountService() : serverMain.getAccountService();
    }

    @Override
    public void run() {
        running = true;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String packet;
            while (running && (packet = reader.readLine()) != null) {
                handlePacket(packet);
            }
        } catch (IOException exception) {
            if (running) {
                System.err.println("Client connection error: " + exception.getMessage());
            }
        } finally {
            close();
        }
    }

    public void handlePacket(String packet) {
        if (packet == null || packet.trim().isEmpty()) {
            return;
        }

        com.google.gson.JsonObject accountResponse = accountService.handle(packet);
        if (accountResponse != null) {
            sendPacket(accountResponse.toString());
            return;
        }

        Map<String, String> fields = parsePacket(packet);
        String type = value(fields, "type");
        String sender = value(fields, "sender");

        if ("LOGIN".equalsIgnoreCase(type) || "REGISTER".equalsIgnoreCase(type)) {
            username = firstNonBlank(value(fields, "username"), sender);
            if (username != null) {
                username = username.trim();
                messageRouter.registerClient(username, this);
                if (serverMain != null) {
                    serverMain.registerClient(username, this);
                }
                for (String onlineUser : messageRouter.getClients().keySet()) {
                    if (!username.equals(onlineUser)) {
                        sendPacket("{\"type\":\"STATUS\",\"username\":\""
                                + escape(onlineUser) + "\",\"status\":\"ONLINE\"}");
                    }
                }
                sendPacket("{\"type\":\"STATUS\",\"username\":\"" + escape(username)
                        + "\",\"status\":\"ONLINE\"}");
                messageRouter.broadcast("{\"type\":\"STATUS\",\"username\":\""
                        + escape(username) + "\",\"status\":\"ONLINE\"}");
            }
            return;
        }

        if (username == null) {
            username = sender;
        }

        if (username != null && !username.trim().isEmpty()
                && !messageRouter.isClientConnected(username)) {
            messageRouter.registerClient(username, this);
        }
        messageRouter.route(packet);
    }

    public void sendPacket(String packet) {
        if (writer != null && packet != null) {
            writer.println(packet);
        }
    }

    public void sendPacket(Map<String, String> packet) {
        if (packet != null) {
            sendPacket(toJson(packet));
        }
    }

    public String getUsername() {
        return username;
    }

    public boolean isRunning() {
        return running;
    }

    public void close() {
        running = false;
        if (username != null) {
            boolean wasRegistered = messageRouter.unregisterClient(username, this);
            if (serverMain != null) {
                wasRegistered |= serverMain.unregisterClient(username, this);
            }
            if (wasRegistered) {
                messageRouter.broadcast("{\"type\":\"STATUS\",\"username\":\""
                        + escape(username) + "\",\"status\":\"OFFLINE\"}");
            }
        }
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException exception) {
            System.err.println("Error closing client connection: " + exception.getMessage());
        }
    }

    public void stop() {
        close();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }

    private static String value(Map<String, String> fields, String key) {
        return fields.get(key);
    }

    private static Map<String, String> parsePacket(String packet) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
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
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}