package server;

import auth.PhoneNumber;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import model.User;
import storage.FriendStorage;
import storage.UserStorage;

import java.util.List;

public class AccountService {

    private final UserStorage userStorage;
    private final FriendStorage friendStorage;

    public AccountService() {
        this(new UserStorage(), new FriendStorage());
    }

    public AccountService(UserStorage userStorage, FriendStorage friendStorage) {
        if (userStorage == null || friendStorage == null) {
            throw new IllegalArgumentException("Account storage is required.");
        }
        this.userStorage = userStorage;
        this.friendStorage = friendStorage;
    }

    public synchronized JsonObject handle(String packet) {
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(packet);
            if (!parsed.isJsonObject()) {
                return null;
            }
            request = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            return null;
        }

        String type = value(request, "type");
        if (type == null) {
            return null;
        }
        switch (type.toUpperCase()) {
            case "REGISTER":
                return register(request);
            case "AUTHENTICATE":
                return authenticate(request);
            case "USER_LIST":
                return listUsers(request);
            case "FRIEND_DATA":
                return friendData(request);
            case "SEND_FRIEND_REQUEST":
                return sendFriendRequest(request);
            case "RESPOND_FRIEND_REQUEST":
                return respondFriendRequest(request);
            default:
                return null;
        }
    }

    private JsonObject register(JsonObject request) {
        String username = value(request, "username");
        String phoneNumber = PhoneNumber.normalize(value(request, "phoneNumber"));
        String password = value(request, "password");
        if (!hasText(username) || !hasText(password) || phoneNumber == null) {
            return result("REGISTER_RESULT", "INVALID",
                    "Tên hiển thị, số điện thoại hợp lệ và mật khẩu là bắt buộc.");
        }
        username = username.trim();

        List<User> users = userStorage.loadAll();
        if (users == null) {
            return result("REGISTER_RESULT", "ERROR",
                    "Không thể đọc dữ liệu tài khoản trên máy chủ.");
        }
        for (User user : users) {
            if (phoneNumber.equals(user.getPhoneNumber())) {
                return result("REGISTER_RESULT", "DUPLICATE_PHONE",
                        "Số điện thoại này đã được đăng ký.");
            }
        }
        for (User legacyUser : users) {
            if (legacyUser.getPhoneNumber() == null
                    && username.equalsIgnoreCase(legacyUser.getUsername())
                    && password.equals(legacyUser.getPassword())) {
                legacyUser.setPhoneNumber(phoneNumber);
                if (!userStorage.saveAll(users)) {
                    legacyUser.setPhoneNumber(null);
                    return result("REGISTER_RESULT", "ERROR",
                            "Không thể cập nhật tài khoản cũ trên máy chủ.");
                }
                if (!friendStorage.migrateIdentity(username, phoneNumber)) {
                    legacyUser.setPhoneNumber(null);
                    if (!userStorage.saveAll(users)) {
                        return result("REGISTER_RESULT", "ERROR",
                                "Không thể khôi phục dữ liệu sau khi cập nhật tài khoản.");
                    }
                    return result("REGISTER_RESULT", "ERROR",
                            "Không thể cập nhật danh sách bạn bè cho tài khoản cũ.");
                }
                return result("REGISTER_RESULT", "OK",
                        "Đã thêm số điện thoại vào tài khoản cũ.");
            }
        }
        if (!userStorage.addUser(
                new User(username, phoneNumber, password, "OFFLINE"))) {
            return result("REGISTER_RESULT", "ERROR",
                    "Không thể lưu tài khoản trên máy chủ.");
        }
        return result("REGISTER_RESULT", "OK", "Đăng ký thành công.");
    }

    private JsonObject authenticate(JsonObject request) {
        String username = value(request, "username");
        String password = value(request, "password");
        User user = findAuthenticatedUser(username, password);
        if (user == null) {
            List<User> users = userStorage.loadAll();
            if (users == null) {
                return result("AUTHENTICATE_RESULT", "ERROR",
                        "Không thể đọc dữ liệu tài khoản trên máy chủ.");
            }
            return result("AUTHENTICATE_RESULT", "INVALID",
                    "Tên tài khoản hoặc mật khẩu không chính xác.");
        }
        JsonObject response = result("AUTHENTICATE_RESULT", "OK",
                "Đăng nhập thành công.");
        response.addProperty("username", user.getUsername());
        if (user.getPhoneNumber() != null) {
            response.addProperty("phoneNumber", user.getPhoneNumber());
        }
        response.addProperty("identity", user.getIdentity());
        return response;
    }

    private JsonObject listUsers(JsonObject request) {
        if (!isAuthenticated(request)) {
            return result("USER_LIST_RESULT", "UNAUTHORIZED",
                    "Vui lòng đăng nhập lại.");
        }
        List<User> users = userStorage.loadAll();
        if (users == null) {
            return result("USER_LIST_RESULT", "ERROR",
                    "Không thể đọc danh sách tài khoản trên máy chủ.");
        }
        JsonObject response = result("USER_LIST_RESULT", "OK", "");
        JsonArray names = new JsonArray();
        for (User user : users) {
            JsonObject entry = new JsonObject();
            entry.addProperty("username", user.getUsername());
            entry.addProperty("phoneNumber", user.getPhoneNumber() == null
                    ? "" : user.getPhoneNumber());
            entry.addProperty("identity", user.getIdentity());
            entry.addProperty("status", user.getStatus());
            names.add(entry);
        }
        response.add("users", names);
        return response;
    }

    private JsonObject friendData(JsonObject request) {
        if (!isAuthenticated(request)) {
            return result("FRIEND_DATA_RESULT", "UNAUTHORIZED",
                    "Vui lòng đăng nhập lại.");
        }
        String username = value(request, "username");
        User user = findAuthenticatedUser(username, value(request, "password"));
        List<String> friends = friendStorage.getFriends(user.getIdentity());
        List<String> incoming = friendStorage.getIncomingRequests(user.getIdentity());
        if (friends == null || incoming == null) {
            return result("FRIEND_DATA_RESULT", "ERROR",
                    "Không thể đọc dữ liệu bạn bè trên máy chủ.");
        }
        JsonObject response = result("FRIEND_DATA_RESULT", "OK", "");
        response.add("friends", toJsonArray(friends));
        response.add("requests", toJsonArray(incoming));
        return response;
    }

    private JsonObject sendFriendRequest(JsonObject request) {
        if (!isAuthenticated(request)) {
            return result("SEND_FRIEND_REQUEST_RESULT", "UNAUTHORIZED",
                    "Vui lòng đăng nhập lại.");
        }
        User sender = findAuthenticatedUser(value(request, "username"),
                value(request, "password"));
        String receiver = value(request, "target");
        User recipient = userStorage.findByPhoneNumber(receiver);
        if (recipient == null) {
            return result("SEND_FRIEND_REQUEST_RESULT", "NOT_FOUND",
                    "Không tìm thấy tài khoản này trên máy chủ.");
        }

        String senderIdentity = sender.getIdentity();
        String recipientIdentity = recipient.getIdentity();
        List<String> friends = friendStorage.getFriends(senderIdentity);
        List<String> requests = friendStorage.getIncomingRequests(recipientIdentity);
        if (friends == null || requests == null) {
            return result("SEND_FRIEND_REQUEST_RESULT", "ERROR",
                    "Không thể đọc dữ liệu bạn bè trên máy chủ.");
        }
        if (containsIgnoreCase(friends, recipientIdentity)) {
            return result("SEND_FRIEND_REQUEST_RESULT", "ALREADY_FRIENDS",
                    "Hai bạn đã là bạn bè.");
        }
        if (containsIgnoreCase(requests, senderIdentity)) {
            return result("SEND_FRIEND_REQUEST_RESULT", "PENDING",
                    "Lời mời đã được gửi và đang chờ chấp nhận.");
        }
        if (!friendStorage.sendRequest(senderIdentity, recipientIdentity)) {
            return result("SEND_FRIEND_REQUEST_RESULT", "ERROR",
                    "Không thể gửi lời mời trên máy chủ.");
        }
        return result("SEND_FRIEND_REQUEST_RESULT", "OK",
                "Đã gửi lời mời kết bạn.");
    }

    private JsonObject respondFriendRequest(JsonObject request) {
        if (!isAuthenticated(request)) {
            return result("RESPOND_FRIEND_REQUEST_RESULT", "UNAUTHORIZED",
                    "Vui lòng đăng nhập lại.");
        }
        User recipient = findAuthenticatedUser(value(request, "username"),
                value(request, "password"));
        User requester = userStorage.findByPhoneNumber(value(request, "target"));
        if (requester == null) {
            requester = userStorage.findByIdentity(value(request, "target"));
        }
        if (requester == null) {
            return result("RESPOND_FRIEND_REQUEST_RESULT", "NOT_FOUND",
                    "Không tìm thấy tài khoản gửi lời mời.");
        }
        String recipientIdentity = recipient.getIdentity();
        String requesterIdentity = requester.getIdentity();
        String action = value(request, "action");
        boolean accepted;
        if ("ACCEPT".equalsIgnoreCase(action)) {
            accepted = friendStorage.acceptRequest(recipientIdentity, requesterIdentity);
        } else if ("DECLINE".equalsIgnoreCase(action)) {
            accepted = friendStorage.declineRequest(recipientIdentity, requesterIdentity);
        } else {
            return result("RESPOND_FRIEND_REQUEST_RESULT", "INVALID",
                    "Thao tác lời mời không hợp lệ.");
        }
        if (!accepted) {
            return result("RESPOND_FRIEND_REQUEST_RESULT", "ERROR",
                    "Lời mời không còn tồn tại hoặc không thể cập nhật.");
        }
        return result("RESPOND_FRIEND_REQUEST_RESULT", "OK",
                "Đã cập nhật lời mời kết bạn.");
    }

    private boolean isAuthenticated(JsonObject request) {
        return findAuthenticatedUser(value(request, "username"),
                value(request, "password")) != null;
    }

    private User findAuthenticatedUser(String username, String password) {
        if (!hasText(username) || password == null) {
            return null;
        }
        User user = userStorage.findByPhoneNumber(username);
        if (user == null) {
            User legacyUser = userStorage.findByUsername(username);
            if (legacyUser != null && legacyUser.getPhoneNumber() == null) {
                user = legacyUser;
            }
        }
        return user != null && password.equals(user.getPassword()) ? user : null;
    }

    private static JsonObject result(String type, String status, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("type", type);
        response.addProperty("status", status);
        response.addProperty("message", message);
        return response;
    }

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static String value(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? null : element.getAsString();
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String item : values) {
            if (item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
