package storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class FriendStorage {

    private final JsonStorage storage;

    public FriendStorage() {
        this(new JsonStorage("friends.json"));
    }

    public FriendStorage(JsonStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null");
        }
        this.storage = storage;
    }

    public List<String> getFriends(String username) {
        JsonArray records = storage.readArray();
        if (records == null) {
            return null;
        }
        JsonObject user = findUser(records, username, false);
        return readNames(user, "friends");
    }

    public List<String> getIncomingRequests(String username) {
        JsonArray records = storage.readArray();
        if (records == null) {
            return null;
        }
        JsonObject user = findUser(records, username, false);
        return readNames(user, "incomingRequests");
    }

    public boolean sendRequest(String sender, String receiver) {
        if (!hasText(sender) || !hasText(receiver)
                || sender.equalsIgnoreCase(receiver)) {
            return false;
        }

        JsonArray records = storage.readArray();
        if (records == null) {
            return false;
        }
        JsonObject senderRecord = findUser(records, sender, true);
        JsonObject receiverRecord = findUser(records, receiver, true);
        JsonArray friends = getNames(senderRecord, "friends");
        JsonArray incoming = getNames(receiverRecord, "incomingRequests");
        if (contains(friends, receiver) || contains(incoming, sender)) {
            return false;
        }

        incoming.add(sender);
        return storage.writeArray(records);
    }

    public boolean acceptRequest(String recipient, String requester) {
        if (!hasText(recipient) || !hasText(requester)
                || recipient.equalsIgnoreCase(requester)) {
            return false;
        }

        JsonArray records = storage.readArray();
        if (records == null) {
            return false;
        }
        JsonObject recipientRecord = findUser(records, recipient, false);
        JsonObject requesterRecord = findUser(records, requester, true);
        if (recipientRecord == null
                || !remove(getNames(recipientRecord, "incomingRequests"), requester)) {
            return false;
        }

        addIfMissing(getNames(recipientRecord, "friends"), requester);
        addIfMissing(getNames(requesterRecord, "friends"), recipient);
        return storage.writeArray(records);
    }

    public boolean declineRequest(String recipient, String requester) {
        if (!hasText(recipient) || !hasText(requester)) {
            return false;
        }

        JsonArray records = storage.readArray();
        if (records == null) {
            return false;
        }
        JsonObject recipientRecord = findUser(records, recipient, false);
        if (recipientRecord == null
                || !remove(getNames(recipientRecord, "incomingRequests"), requester)) {
            return false;
        }
        return storage.writeArray(records);
    }

    private static JsonObject findUser(JsonArray records, String username,
                                       boolean create) {
        for (JsonElement element : records) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject record = element.getAsJsonObject();
            if (username.equalsIgnoreCase(readString(record, "username"))) {
                getNames(record, "friends");
                getNames(record, "incomingRequests");
                return record;
            }
        }
        if (!create) {
            return null;
        }

        JsonObject record = new JsonObject();
        record.addProperty("username", username);
        record.add("friends", new JsonArray());
        record.add("incomingRequests", new JsonArray());
        records.add(record);
        return record;
    }

    private static List<String> readNames(JsonObject record, String key) {
        List<String> names = new ArrayList<String>();
        if (record == null) {
            return names;
        }
        JsonArray values = getNames(record, key);
        for (JsonElement element : values) {
            if (element.isJsonPrimitive()) {
                names.add(element.getAsString());
            }
        }
        return names;
    }

    private static JsonArray getNames(JsonObject record, String key) {
        JsonElement element = record.get(key);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        JsonArray values = new JsonArray();
        record.add(key, values);
        return values;
    }

    private static void addIfMissing(JsonArray values, String name) {
        if (!contains(values, name)) {
            values.add(name);
        }
    }

    private static boolean contains(JsonArray values, String name) {
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()
                    && name.equalsIgnoreCase(value.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean remove(JsonArray values, String name) {
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (value.isJsonPrimitive()
                    && name.equalsIgnoreCase(value.getAsString())) {
                values.remove(index);
                return true;
            }
        }
        return false;
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? null : element.getAsString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
