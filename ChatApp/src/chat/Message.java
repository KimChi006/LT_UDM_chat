
package chat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {

    private String type;
    private String sender;
    private String senderName;
    private String receiver;
    private String content;
    private String time;
    private String groupId;
    private String messageId;
    private String transferId;
    private String deliveryStatus;
    private int deliveredCount;
    private int readCount;

    // Only used for media messages, for example "media/images/photo.png".
    private String filepath;

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

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
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

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getDeliveredCount() {
        return deliveredCount;
    }

    public void setDeliveredCount(int deliveredCount) {
        this.deliveredCount = Math.max(0, deliveredCount);
    }

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = Math.max(0, readCount);
    }
}
