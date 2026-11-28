package storage;

import auth.PhoneNumber;
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
            String phoneNumber = PhoneNumber.normalize(
                    readString(object, "phoneNumber"));
            users.add(new User(
                    username,
                    phoneNumber,
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
            if (user.getPhoneNumber() != null
                    && !user.getPhoneNumber().trim().isEmpty()) {
                object.addProperty("phoneNumber", user.getPhoneNumber());
            }
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

    public User findByPhoneNumber(String phoneNumber) {
        String normalized = PhoneNumber.normalize(phoneNumber);
        if (normalized == null) {
            return null;
        }
        List<User> users = loadAll();
        if (users == null) {
            return null;
        }
        for (User user : users) {
            if (normalized.equals(user.getPhoneNumber())) {
                return user;
            }
        }
        return null;
    }

    public User findByIdentity(String identity) {
        User byPhone = findByPhoneNumber(identity);
        if (byPhone != null) {
            return byPhone;
        }
        User byLegacyName = findByUsername(identity);
        return byLegacyName != null && byLegacyName.getPhoneNumber() == null
                ? byLegacyName : null;
    }

    /** Adds accounts with unique phone numbers; display names may be shared. */
    public boolean addUser(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }

        List<User> users = loadAll();
        if (users == null) {
            return false;
        }
        String phoneNumber = PhoneNumber.normalize(user.getPhoneNumber());
        if (phoneNumber == null) {
            return false;
        }
        for (User existing : users) {
            if (phoneNumber.equals(existing.getPhoneNumber())) {
                return false;
            }
        }
        user.setPhoneNumber(phoneNumber);

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
