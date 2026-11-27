package history;

import chat.Message;
import media.FileInfo;
import storage.MessageStorage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The only class that chat windows need for history.
 *
 * ChatUI and GroupChatUI call this class; they never touch JsonStorage or
 * MessageStorage directly. All methods are synchronized because a message can
 * be saved from the UI thread while another one arrives on the socket thread.
 */
public class HistoryManager {

    private static final HistoryManager INSTANCE = new HistoryManager();
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MessageStorage messageStorage;

    public static HistoryManager getInstance() {
        return INSTANCE;
    }

    public HistoryManager() {
        this(new MessageStorage());
    }

    /** Used by tests with a temporary storage file. */
    public HistoryManager(MessageStorage messageStorage) {
        if (messageStorage == null) {
            throw new IllegalArgumentException("MessageStorage cannot be null");
        }
        this.messageStorage = messageStorage;
    }

    public synchronized void savePrivateMessage(String sender, String receiver,
                                                 String content, String time) {
        String savedTime = time == null || time.trim().isEmpty() ? now() : time;
        messageStorage.save(new Message(
                "MESSAGE", sender, receiver, content, savedTime, null
        ));
    }

    public synchronized void savePrivateMessage(Message message) {
        messageStorage.save(message);
    }

    public synchronized void saveGroupMessage(String sender, String groupId,
                                               String content, String time) {
        String savedTime = time == null || time.trim().isEmpty() ? now() : time;
        messageStorage.save(new Message(
                "GROUP_MESSAGE", sender, null, content, savedTime, groupId
        ));
    }

    public synchronized void saveMedia(FileInfo info) {
        messageStorage.saveMedia(info);
    }

    public synchronized List<Message> getPrivateHistory(String userA, String userB) {
        return messageStorage.getPrivateHistory(userA, userB);
    }

    public synchronized List<Message> getGroupHistory(String groupId) {
        return messageStorage.getGroupHistory(groupId);
    }

    /**
     * Converts messages to the same text format used by the chat windows.
     * Media items get a [Media] prefix so they are easy to recognise.
     */
    public synchronized String formatHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Message message : messages) {
            if (message.getFilepath() != null && !message.getFilepath().trim().isEmpty()) {
                text.append("[Media] ");
            }
            text.append(message.getSender())
                    .append(" [")
                    .append(message.getTime())
                    .append("]: ")
                    .append(message.getContent())
                    .append('\n');
        }
        return text.toString();
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }
}
