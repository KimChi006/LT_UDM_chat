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
            String senderName = readString(object, "senderName");
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
            message.setSenderName(senderName);
            message.setFilepath(readString(object, "filepath"));
            message.setMessageId(readString(object, "messageId"));
            message.setTransferId(readString(object, "transferId"));
            message.setDeliveryStatus(readString(object, "deliveryStatus"));
            message.setDeliveredCount(readInt(object, "deliveredCount"));
            message.setReadCount(readInt(object, "readCount"));
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
        message.setTransferId(info.getTransferId());
        return save(message);
    }

    public boolean updateDeliveryStatus(String messageId, String status) {
        if (!hasText(messageId) || !hasText(status)) {
            return false;
        }
        List<Message> messages = loadAll();
        if (messages == null) {
            return false;
        }
        boolean updated = false;
        for (Message message : messages) {
            if (messageId.equals(message.getMessageId())) {
                message.setDeliveryStatus(status);
                updated = true;
            }
        }
        return updated && writeAll(messages);
    }

    public boolean updateGroupReceiptCounts(String messageId,
                                             int deliveredCount, int readCount) {
        if (!hasText(messageId)) {
            return false;
        }
        List<Message> messages = loadAll();
        if (messages == null) {
            return false;
        }
        boolean updated = false;
        for (Message message : messages) {
            if (messageId.equals(message.getMessageId())) {
                message.setDeliveredCount(deliveredCount);
                message.setReadCount(readCount);
                updated = true;
            }
        }
        return updated && writeAll(messages);
    }

    private boolean writeAll(List<Message> messages) {
        JsonArray array = new JsonArray();

        for (Message message : messages) {
            JsonObject object = new JsonObject();
            addIfPresent(object, "type", message.getType());
            addIfPresent(object, "sender", message.getSender());
            addIfPresent(object, "senderName", message.getSenderName());
            addIfPresent(object, "receiver", message.getReceiver());
            addIfPresent(object, "content", message.getContent());
            addIfPresent(object, "time", message.getTime());
            addIfPresent(object, "groupId", message.getGroupId());
            addIfPresent(object, "filepath", message.getFilepath());
            addIfPresent(object, "messageId", message.getMessageId());
            addIfPresent(object, "transferId", message.getTransferId());
            addIfPresent(object, "deliveryStatus", message.getDeliveryStatus());
            if (message.getDeliveredCount() > 0) {
                object.addProperty("deliveredCount", message.getDeliveredCount());
            }
            if (message.getReadCount() > 0) {
                object.addProperty("readCount", message.getReadCount());
            }
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
        if (hasText(message.getTransferId())) {
            return value(message.getType()) + DELIMITER
                    + value(message.getSender()) + DELIMITER
                    + value(message.getTransferId());
        }
        return value(message.getType()) + DELIMITER
                + value(message.getSender()) + DELIMITER
                + value(message.getReceiver()) + DELIMITER
                + value(message.getContent()) + DELIMITER
                + value(message.getTime()) + DELIMITER
                + value(message.getGroupId()) + DELIMITER
                + value(message.getFilepath()) + DELIMITER
                + value(message.getMessageId());
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static int readInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException exception) {
            return 0;
        }
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
