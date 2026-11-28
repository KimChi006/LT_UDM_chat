package auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AccountClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 4000;
    private static final int READ_TIMEOUT_MILLIS = 5000;

    private final String host;
    private final int port;

    public AccountClient(String host) {
        this(host, 8080);
    }

    public AccountClient(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Cần nhập địa chỉ máy chủ.");
        }
        this.host = host.trim();
        this.port = port;
    }

    public JsonObject register(String username, String password) throws IOException {
        return register(username, null, password);
    }

    public JsonObject register(String username, String phoneNumber, String password)
            throws IOException {
        JsonObject packet = requestPacket("REGISTER", username, password, null, null);
        if (phoneNumber != null) {
            packet.addProperty("phoneNumber", phoneNumber);
        }
        return send(packet);
    }

    public JsonObject authenticate(String username, String password) throws IOException {
        return request("AUTHENTICATE", username, password, null, null);
    }

    public JsonObject listUsers(String username, String password) throws IOException {
        return request("USER_LIST", username, password, null, null);
    }

    public JsonObject getFriendData(String username, String password) throws IOException {
        return request("FRIEND_DATA", username, password, null, null);
    }

    public JsonObject sendFriendRequest(String username, String password,
                                        String target) throws IOException {
        return request("SEND_FRIEND_REQUEST", username, password, target, null);
    }

    public JsonObject respondFriendRequest(String username, String password,
                                           String target, boolean accept)
            throws IOException {
        return request("RESPOND_FRIEND_REQUEST", username, password, target,
                accept ? "ACCEPT" : "DECLINE");
    }

    public JsonObject request(String type, String username, String password,
                              String target, String action) throws IOException {
        return send(requestPacket(type, username, password, target, action));
    }

    private JsonObject requestPacket(String type, String username, String password,
                                     String target, String action) {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", type);
        if (username != null) {
            packet.addProperty("username", username);
        }
        if (password != null) {
            packet.addProperty("password", password);
        }
        if (target != null) {
            packet.addProperty("target", target);
        }
        if (action != null) {
            packet.addProperty("action", action);
        }

        return packet;
    }

    private JsonObject send(JsonObject packet) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port),
                    CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.println(packet.toString());
                if (writer.checkError()) {
                    throw new IOException("Không thể gửi yêu cầu đến máy chủ.");
                }
                String response = reader.readLine();
                if (response == null) {
                    throw new IOException("Máy chủ đóng kết nối mà không phản hồi.");
                }
                try {
                    JsonElement parsed = JsonParser.parseString(response);
                    if (parsed.isJsonObject()) {
                        return parsed.getAsJsonObject();
                    }
                } catch (JsonParseException exception) {
                    throw new IOException("Máy chủ trả về dữ liệu không hợp lệ.", exception);
                }
                throw new IOException("Máy chủ trả về phản hồi không hợp lệ.");
            }
        }
    }

    public static List<User> userList(JsonObject response) throws IOException {
        JsonElement element = response == null ? null : response.get("users");
        if (element == null || !element.isJsonArray()) {
            throw new IOException("Phản hồi máy chủ thiếu danh sách tài khoản.");
        }
        List<User> users = new ArrayList<User>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new IOException("Máy chủ trả về tài khoản không hợp lệ.");
            }
            JsonObject object = entry.getAsJsonObject();
            users.add(new User(value(object, "username"),
                    value(object, "phoneNumber"), "",
                    value(object, "status")));
        }
        return users;
    }

    public static String value(JsonObject response, String key) {
        JsonElement element = response == null ? null : response.get(key);
        return element == null || element.isJsonNull()
                ? null : element.getAsString();
    }

    public static List<String> stringList(JsonObject response, String key)
            throws IOException {
        JsonElement element = response == null ? null : response.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IOException("Phản hồi máy chủ thiếu " + key + ".");
        }
        List<String> values = new ArrayList<String>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive()) {
                throw new IOException("Máy chủ trả về mục " + key + " không hợp lệ.");
            }
            values.add(value.getAsString());
        }
        return values;
    }
}
