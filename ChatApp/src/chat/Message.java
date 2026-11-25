
package chat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {

    private String type;
    private String sender;
    private String receiver;
    private String content;
    private String time;
    private String groupId;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Message(String type, String sender, String receiver,
                  String content, String groupId) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.groupId = groupId;
        this.time = LocalDateTime.now().format(TIME_FORMAT);
    }

    public Message(String type, String sender, String receiver,
                 String content, String time, String groupId) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.time = time;
        this.groupId = groupId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
