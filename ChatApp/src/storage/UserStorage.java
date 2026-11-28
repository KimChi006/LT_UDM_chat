package storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes data/users.json.
 *
 * The file format is kept identical to the one LoginUI already uses, so old
 * accounts stay readable. Passwords are currently stored as plain text by the
 * team's LoginUI; this class does not change that login protocol.
 */
public class UserStorage {

    private final JsonStorage storage;

    public UserStorage() {
        this(new JsonStorage("users.json"));
    }

    /** Used by tests with a temporary file. */
    public UserStorage(JsonStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null");
        }
        this.storage = storage;
    }

    /**
     * @return all users, or null when JSON is invalid.
     */
    public List<User> loadAll() {
        JsonArray array = storage.readArray();
        if (array == null) {
            return null;
        }

        List<User> users = new ArrayList<User>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String username = readString(object, "username");
            if (username == null || username.trim().isEmpty()) {
                continue;
            }

            String password = readString(object, "password");
            String status = readString(object, "status");
            users.add(new User(
                    username,
                    password == null ? "" : password,
                    status == null ? "OFFLINE" : status
            ));
        }
        return users;
    }

    public boolean saveAll(List<User> users) {
        if (users == null) {
            return false;
        }

        JsonArray array = new JsonArray();
        for (User user : users) {
            if (user == null || user.getUsername() == null) {
                continue;
            }

            JsonObject object = new JsonObject();
            object.addProperty("username", user.getUsername());
            object.addProperty("password", user.getPassword() == null ? "" : user.getPassword());
            object.addProperty("status", user.getStatus() == null ? "OFFLINE" : user.getStatus());
            array.add(object);
        }
        return storage.writeArray(array);
    }

    /** Case-insensitive, like LoginUI.authenticate(). */
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        List<User> users = loadAll();
        if (users == null) {
            return null;
        }

        for (User user : users) {
            if (username.equalsIgnoreCase(user.getUsername())) {
                return user;
            }
        }
        return null;
    }

    public boolean exists(String username) {
        return findByUsername(username) != null;
    }

    /** Adds one account only when the username is still free. */
    public boolean addUser(User user) {
        if (user == null || user.getUsername() == null || exists(user.getUsername())) {
            return false;
        }

        List<User> users = loadAll();
        if (users == null) {
            return false;
        }

        users.add(user);
        return saveAll(users);
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }
}
