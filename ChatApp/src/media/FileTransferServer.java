package media;

import model.PacketType;

/**
 * Server side helpers for media packets.
 *
 * The server only relays media packets (MessageRouter already forwards every
 * packet type), so no change in src/server is required. These helpers are
 * optional: the server owner can call them from MessageRouter/ClientHandler
 * if the server should reject invalid or oversized media packets.
 */
public final class FileTransferServer {

    private FileTransferServer() {
    }

    /** Returns true if the packet type is FILE, IMAGE, VIDEO or SOUND. */
    public static boolean isMediaPacket(String packet) {
        String type = FileTransferClient.extract(packet, "type");
        return MediaManager.isMediaType(type);
    }

    /**
     * Checks a media packet. Returns null if it is valid,
     * otherwise a short error message.
     */
    public static String validate(String packet) {
        if (packet == null || packet.trim().isEmpty()) {
            return "Empty packet";
        }
        String type = FileTransferClient.extract(packet, "type");
        if (!MediaManager.isMediaType(type)) {
            return "Not a media packet";
        }
        String sender = FileTransferClient.extract(packet, "sender");
        if (sender == null || sender.trim().isEmpty()) {
            return "Missing sender";
        }
        String filename = FileTransferClient.extract(packet, "filename");
        if (filename == null || filename.trim().isEmpty()) {
            return "Missing filename";
        }
        long filesize = FileTransferClient.parseLong(
                FileTransferClient.extract(packet, "filesize"), -1);
        if (filesize < 0 || filesize > MediaManager.MAX_FILE_SIZE) {
            return "Invalid or too large filesize";
        }
        String data = FileTransferClient.extract(packet, "data");
        if (data != null && data.length() > FileTransferClient.MAX_ENCODED_CHUNK) {
            return "Chunk is too large";
        }
        return null;
    }

    /** Convenience: map a packet type name to the enum, or null. */
    public static PacketType toPacketType(String type) {
        return MediaManager.toMediaType(type);
    }
}
