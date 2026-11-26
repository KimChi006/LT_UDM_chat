package media;

import model.PacketType;

import java.io.File;

/**
 * Describes one media item (file, image, video or sound) that was sent or received.
 */
public class FileInfo {

    private PacketType type;
    private String filename;
    private long filesize;
    private String filepath;
    private String sender;
    private String receiver;
    private String groupId;
    private String time;

    public FileInfo() {
    }

    public FileInfo(PacketType type, String filename, long filesize,
                    String filepath, String sender, String receiver,
                    String groupId, String time) {
        this.type = type;
        this.filename = filename;
        this.filesize = filesize;
        this.filepath = filepath;
        this.sender = sender;
        this.receiver = receiver;
        this.groupId = groupId;
        this.time = time;
    }

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFilesize() {
        return filesize;
    }

    public void setFilesize(long filesize) {
        this.filesize = filesize;
    }

    /** Relative or absolute path of the file on this machine. */
    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public File getFile() {
        return filepath == null ? null : new File(filepath);
    }

    public boolean isImage() {
        return type == PacketType.IMAGE;
    }

    /** Human readable size, for example "1.5 MB". */
    public String getReadableSize() {
        if (filesize < 1024) {
            return filesize + " B";
        }
        double kb = filesize / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }

    @Override
    public String toString() {
        return "[" + type + "] " + filename + " (" + getReadableSize() + ")";
    }
}
