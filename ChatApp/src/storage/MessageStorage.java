package storage;

import chat.Message;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import media.FileInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Stores chat messages in data/messages.json.
 *
 * The existing chat.Message class is used directly, so no second message
 * model is created. Media items are also stored as Message objects:
 * their filename becomes the content and their path goes to the filepath key.
 */
public class MessageStorage {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DELIMITER = "|";

    private final JsonStorage storage;

    public MessageStorage() {
        this(new JsonStorage("messages.json"));
    }

    /** Used by tests with a temporary file. */
    public MessageStorage(JsonStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null");
        }
        this.storage = storage;
    }

    /**
     * Appends one message without losing the messages that are already saved.
     * Returns false when the JSON file is broken, so the old data is preserved.
     */
    public boolean save(Message message) {
        if (message == null) {
            return false;
        }

        List<Message> messages = loadAll();
        if (messages == null) {
            return false;
        }

        // A packet may be handled twice by different listeners; keep one copy.
        if (contains(messages, message)) {
            return true;
        }

        messages.add(message);
        return writeAll(messages);
    }

    /**
     * @return all messages ordered by time, or null when JSON is invalid.
     */
    public List<Message> loadAll() {
        JsonArray array = storage.readArray();
        if (array == null) {
            return null;
        }

        List<Message> messages = new ArrayList<Message>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String type = readString(object, "type");
            String sender = readString(object, "sender");
            String receiver = readString(object, "receiver");
            String content = readString(object, "content");
            String time = readString(object, "time");
            String groupId = readString(object, "groupId");

            if (sender == null || content == null) {
                continue; // Skip a broken record, keep the rest of the file.
            }

            Message message = new Message(
                    type, sender, receiver, content, time, groupId
            );
            message.setFilepath(readString(object, "filepath"));
            messages.add(message);
        }

        Collections.sort(messages, new Comparator<Message>() {
            @Override
            public int compare(Message first, Message second) {
                String firstTime = first.getTime() == null ? "" : first.getTime();
                String secondTime = second.getTime() == null ? "" : second.getTime();
                return firstTime.compareTo(secondTime);
            }
        });

        return messages;
    }

    /**
     * Both directions of a private conversation, excluding group messages.
     */
    public List<Message> getPrivateHistory(String userA, String userB) {
        List<Message> result = new ArrayList<Message>();
        if (!hasText(userA) || !hasText(userB)) {
            return result;
        }

        List<Message> messages = loadAll();
        if (messages == null) {
            return result;
        }

        for (Message message : messages) {
            if (hasText(message.getGroupId())) {
                continue; // Group chat must never appear in private history.
            }

            String sender = message.getSender();
            String receiver = message.getReceiver();
            if (!hasText(receiver)) {
                continue;
            }

            boolean firstDirection = userA.equals(sender) && userB.equals(receiver);
            boolean secondDirection = userB.equals(sender) && userA.equals(receiver);
            if (firstDirection || secondDirection) {
                result.add(message);
            }
        }

        return result;
    }

    /** Only messages of the requested group. */
    public List<Message> getGroupHistory(String groupId) {
        List<Message> result = new ArrayList<Message>();
        if (!hasText(groupId)) {
            return result;
        }

        List<Message> messages = loadAll();
        if (messages == null) {
            return result;
        }

        for (Message message : messages) {
            if (groupId.equals(message.getGroupId())) {
                result.add(message);
            }
        }

        return result;
    }

    /**
     * Stores one completed media transfer. FileTransferClient calls the
     * MediaManager callback only after all chunks arrived, so no packet
     * chunk is ever written here.
     */
    public boolean saveMedia(FileInfo info) {
        if (info == null) {
            return false;
        }

        String type = info.getType() == null ? "FILE" : info.getType().name();
        String time = hasText(info.getTime())
                ? info.getTime()
                : LocalDateTime.now().format(TIME_FORMAT);

        Message message = new Message(
                type,
                info.getSender(),
                info.getReceiver(),
                info.getFilename(),
                info.getGroupId()
        );
        message.setTime(time);
        message.setFilepath(info.getFilepath());
        return save(message);
    }

    private boolean writeAll(List<Message> messages) {
        JsonArray array = new JsonArray();

        for (Message message : messages) {
            JsonObject object = new JsonObject();
            addIfPresent(object, "type", message.getType());
            addIfPresent(object, "sender", message.getSender());
            addIfPresent(object, "receiver", message.getReceiver());
            addIfPresent(object, "content", message.getContent());
            addIfPresent(object, "time", message.getTime());
            addIfPresent(object, "groupId", message.getGroupId());
            addIfPresent(object, "filepath", message.getFilepath());
            array.add(object);
        }

        return storage.writeArray(array);
    }

    private static boolean contains(List<Message> messages, Message message) {
        String key = keyOf(message);
        for (Message existing : messages) {
            if (key.equals(keyOf(existing))) {
                return true;
            }
        }
        return false;
    }

    private static String keyOf(Message message) {
        return value(message.getType()) + DELIMITER
                + value(message.getSender()) + DELIMITER
                + value(message.getReceiver()) + DELIMITER
                + value(message.getContent()) + DELIMITER
                + value(message.getTime()) + DELIMITER
                + value(message.getGroupId()) + DELIMITER
                + value(message.getFilepath());
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static void addIfPresent(JsonObject object, String key, String value) {
        if (hasText(value)) {
            object.addProperty(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
