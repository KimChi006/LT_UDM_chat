package media;

import chat.ClientSocket;
import model.PacketType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side of the media transfer.
 *
 * Sending: the file is cut into chunks, every chunk is Base64 encoded and sent
 * as ONE JSON line through ClientSocket (the project protocol is line based).
 *
 * Receiving: chunks are appended to a temporary file under media/ and renamed
 * when the last chunk arrives.
 *
 * Packet example (all values are strings, the server parser needs that):
 * {"type":"IMAGE","sender":"chi","receiver":"nam","transferid":"...",
 *  "filename":"a.png","filesize":"20480","chunkindex":"0",
 *  "totalchunks":"1","data":"...base64..."}
 */
public class FileTransferClient {

    /** Raw bytes per chunk (about 43 KB after Base64). */
    public static final int CHUNK_SIZE = 32 * 1024;

    /** Largest accepted Base64 text for one chunk. */
    public static final int MAX_ENCODED_CHUNK = (CHUNK_SIZE / 3 + 1) * 4 + 16;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public interface ProgressListener {
        void onProgress(long done, long total);
    }

    private static class Incoming {
        PacketType type;
        String filename;
        String sender;
        String receiver;
        String groupId;
        long expectedSize;
        long written;
        int nextIndex;
        int totalChunks;
        File tempFile;
        FileOutputStream out;
    }

    private final ClientSocket clientSocket;
    private final String username;
    private final Map<String, Incoming> incoming =
            new ConcurrentHashMap<String, Incoming>();

    public FileTransferClient(ClientSocket clientSocket, String username) {
        this.clientSocket = clientSocket;
        this.username = username;
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /**
     * Sends a file to one user (receiver) or to a group (groupId).
     * Blocking call: run it on a background thread (MediaManager does that).
     */
    public FileInfo sendFile(String receiver, String groupId, File file,
                             ProgressListener progress) throws IOException {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read");
        }

        long size = file.length();
        if (size > MediaManager.MAX_FILE_SIZE) {
            throw new IOException("File is too large (max "
                    + (MediaManager.MAX_FILE_SIZE / (1024 * 1024)) + " MB)");
        }

        boolean isGroup = groupId != null && !groupId.trim().isEmpty();
        boolean hasReceiver = receiver != null && !receiver.trim().isEmpty();
        if (!isGroup && !hasReceiver) {
            throw new IOException("Receiver or group is required");
        }

        PacketType type = MediaManager.detectType(file);
        String transferId = UUID.randomUUID().toString();
        int totalChunks = size == 0 ? 1 : (int) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE);

        String header = "{\"type\":\"" + type.name() + "\""
                + ",\"sender\":\"" + escape(username) + "\""
                + ",\"receiver\":\"" + escape(isGroup ? "" : receiver.trim()) + "\""
                + (isGroup ? ",\"groupId\":\"" + escape(groupId.trim()) + "\"" : "")
                + ",\"transferid\":\"" + transferId + "\""
                + ",\"filename\":\"" + escape(file.getName()) + "\""
                + ",\"filesize\":\"" + size + "\""
                + ",\"totalchunks\":\"" + totalChunks + "\"";

        long sent = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[CHUNK_SIZE];

            for (int index = 0; index < totalChunks; index++) {
                if (!clientSocket.isConnected()) {
                    throw new IOException("Connection to server was lost");
                }

                int read = readFully(in, buffer);
                if (read <= 0 && size > 0) {
                    throw new IOException("File changed while sending");
                }

                byte[] part = read == buffer.length ? buffer : Arrays.copyOf(buffer, Math.max(read, 0));
                String data = Base64.getEncoder().encodeToString(part);

                clientSocket.sendPacket(header
                        + ",\"chunkindex\":\"" + index + "\""
                        + ",\"data\":\"" + data + "\"}");

                sent += Math.max(read, 0);
                if (progress != null) {
                    progress.onProgress(sent, size);
                }
            }
        }

        return new FileInfo(type, file.getName(), size, file.getPath(),
                username, isGroup ? null : receiver.trim(),
                isGroup ? groupId.trim() : null, now());
    }

    // ------------------------------------------------------------------
    // Receiving
    // ------------------------------------------------------------------

    /**
     * Handles one media packet.
     *
     * @param expectedGroupId null/empty for private chat, otherwise the id of
     *                        the group chat window that is asking.
     * @return FileInfo when the LAST chunk arrived and the file is saved,
     *         otherwise null (not for this chat, or transfer still running).
     */
    public FileInfo receive(String packet, String expectedGroupId) throws IOException {
        String type = extract(packet, "type");
        PacketType packetType = MediaManager.toMediaType(type);
        if (packetType == null) {
            return null;
        }

        String sender = extract(packet, "sender");
        if (sender == null || sender.isEmpty() || sender.equals(username)) {
            return null; // server broadcasts echo our own packets back
        }

        String groupId = extract(packet, "groupId");
        boolean packetIsGroup = groupId != null && !groupId.isEmpty();
        String receiver = extract(packet, "receiver");

        if (expectedGroupId == null || expectedGroupId.isEmpty()) {
            if (packetIsGroup || !username.equals(receiver)) {
                return null;
            }
        } else if (!expectedGroupId.equals(groupId)) {
            return null;
        }

        String transferId = extract(packet, "transferid");
        if (transferId == null || transferId.isEmpty()) {
            throw new IOException("Media packet has no transferid");
        }

        String key = sender + "|" + transferId;
        int index = (int) parseLong(extract(packet, "chunkindex"), -1);
        String data = extract(packet, "data");
        if (data == null) {
            data = "";
        }
        if (data.length() > MAX_ENCODED_CHUNK) {
            abort(key);
            throw new IOException("Chunk is too large");
        }

        Incoming transfer;
        if (index == 0) {
            abort(key);
            transfer = startTransfer(packet, packetType, sender, receiver, groupId);
            incoming.put(key, transfer);
        } else {
            transfer = incoming.get(key);
            if (transfer == null) {
                throw new IOException("Received a chunk of an unknown transfer");
            }
        }

        if (index != transfer.nextIndex) {
            abort(key);
            throw new IOException("Chunk out of order for " + transfer.filename);
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            transfer.written += bytes.length;
            if (transfer.written > transfer.expectedSize) {
                throw new IOException("Received more data than expected");
            }
            transfer.out.write(bytes);
            transfer.nextIndex++;
        } catch (IllegalArgumentException | IOException e) {
            abort(key);
            throw new IOException("Cannot save " + transfer.filename + ": " + e.getMessage());
        }

        if (transfer.nextIndex < transfer.totalChunks) {
            return null; // more chunks are coming
        }

        incoming.remove(key);
        transfer.out.close();

        if (transfer.written != transfer.expectedSize) {
            transfer.tempFile.delete();
            throw new IOException("File is incomplete: " + transfer.filename);
        }

        File finalFile = uniqueFile(MediaManager.getFolder(transfer.type), transfer.filename);
        Files.move(transfer.tempFile.toPath(), finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        return new FileInfo(transfer.type, finalFile.getName(), transfer.written,
                MediaManager.toRelativePath(transfer.type, finalFile.getName()),
                transfer.sender, transfer.receiver, transfer.groupId, now());
    }

    private Incoming startTransfer(String packet, PacketType type, String sender,
                                   String receiver, String groupId) throws IOException {
        long size = parseLong(extract(packet, "filesize"), -1);
        int total = (int) parseLong(extract(packet, "totalchunks"), -1);
        int maxChunks = (int) (MediaManager.MAX_FILE_SIZE / CHUNK_SIZE) + 1;

        if (size < 0 || size > MediaManager.MAX_FILE_SIZE) {
            throw new IOException("Invalid or too large filesize");
        }
        if (total < 1 || total > maxChunks) {
            throw new IOException("Invalid number of chunks");
        }

        Incoming transfer = new Incoming();
        transfer.type = type;
        transfer.filename = safeFilename(extract(packet, "filename"));
        transfer.sender = sender;
        transfer.receiver = receiver;
        transfer.groupId = (groupId == null || groupId.isEmpty()) ? null : groupId;
        transfer.expectedSize = size;
        transfer.totalChunks = total;

        File folder = MediaManager.getFolder(type);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create folder " + folder.getPath());
        }
        transfer.tempFile = new File(folder, "." + UUID.randomUUID() + ".part");
        transfer.out = new FileOutputStream(transfer.tempFile);
        return transfer;
    }

    private void abort(String key) {
        Incoming transfer = incoming.remove(key);
        if (transfer != null) {
            try {
                transfer.out.close();
            } catch (IOException ignored) {
                // nothing to do
            }
            transfer.tempFile.delete();
        }
    }

    /** Cancels every unfinished download and deletes the temporary files. */
    public void cancelAll() {
        for (String key : incoming.keySet()) {
            abort(key);
        }
    }

    // ------------------------------------------------------------------
    // Helpers (package-private so MediaManager and FileTransferServer can use them)
    // ------------------------------------------------------------------

    static String safeFilename(String name) {
        String clean = name == null ? "" : new File(name.replace('\\', '/')).getName();
        clean = clean.replaceAll("[^\\p{L}\\p{N}._ ()\\-]", "_").trim();
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")) {
            clean = "file";
        }
        if (clean.startsWith(".")) {
            clean = "_" + clean.substring(1);
        }
        return clean;
    }

    private static File uniqueFile(File folder, String filename) {
        File candidate = new File(folder, filename);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";

        int counter = 1;
        while (candidate.exists()) {
            candidate = new File(folder, base + "_" + counter + ext);
            counter++;
        }
        return candidate;
    }

    private static int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7e) {  // non-ASCII is escaped: safe on any server charset
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** Reads the string value of "key":"value" from a one-line JSON packet. */
    static String extract(String json, String key) {
        if (json == null) {
            return null;
        }
        String search = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int keyIndex = json.indexOf(search, from);
            if (keyIndex == -1) {
                return null;
            }
            int pos = keyIndex + search.length();
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
            if (pos >= json.length() || json.charAt(pos) != ':') {
                from = keyIndex + 1;
                continue;
            }
            pos++;
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
            if (pos >= json.length() || json.charAt(pos) != '"') {
                from = keyIndex + 1;
                continue;
            }
            return readString(json, pos + 1);
        }
    }

    private static String readString(String json, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            i++;
            if (i >= json.length()) {
                return null;
            }
            char e = json.charAt(i);
            switch (e) {
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'u':
                    if (i + 4 < json.length()) {
                        try {
                            result.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        } catch (NumberFormatException ex) {
                            result.append('?');
                        }
                        i += 4;
                    }
                    break;
                default: result.append(e);
            }
        }
        return null;
    }
}
