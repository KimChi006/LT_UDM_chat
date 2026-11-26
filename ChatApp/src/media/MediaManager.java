package media;

import chat.ClientSocket;
import model.PacketType;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point of the media module (TV3). Other modules only need this class.
 *
 * Usage in ChatUI (private chat):
 * <pre>
 *   mediaManager = new MediaManager(clientSocket, username, info ->
 *           SwingUtilities.invokeLater(() -> appendMessage(info.getSender()
 *                   + " sent " + info)));
 *
 *   // "Attach" button:
 *   File file = MediaManager.chooseFile(this);
 *   if (file != null) {
 *       mediaManager.sendMedia(receiver, null, file);
 *   }
 *
 *   // first line of onPacketReceived(String packet):
 *   if (mediaManager.handlePacket(packet)) {
 *       return;
 *   }
 * </pre>
 * In GroupChatUI call sendMedia(null, groupId, file) and
 * handlePacket(packet, groupId).
 *
 * Listener callbacks run on background threads: use SwingUtilities.invokeLater
 * before touching Swing components.
 */
public class MediaManager {

    public static final String MEDIA_ROOT = "media";
    public static final String FOLDER_FILES = "files";
    public static final String FOLDER_IMAGES = "images";
    public static final String FOLDER_VIDEOS = "videos";
    public static final String FOLDER_SOUNDS = "sounds";

    /** Largest file that can be sent (50 MB). */
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private static final Set<String> IMAGE_EXT = new HashSet<String>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "bmp", "webp"));
    private static final Set<String> VIDEO_EXT = new HashSet<String>(
            Arrays.asList("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm"));
    private static final Set<String> SOUND_EXT = new HashSet<String>(
            Arrays.asList("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma"));

    public interface MediaListener {

        /** A media file was fully received and saved. */
        void onMediaReceived(FileInfo info);

        /** A media file was fully sent. */
        default void onMediaSent(FileInfo info) {
        }

        /** Sending progress from 0 to 100. */
        default void onSendProgress(String filename, int percent) {
        }

        /** Sending or receiving failed. */
        default void onMediaError(String message) {
        }
    }

    private final String username;
    private final FileTransferClient transferClient;
    private final MediaListener listener;
    private final ExecutorService sender;

    public MediaManager(ClientSocket clientSocket, String username,
                        MediaListener listener) {
        this.username = username;
        this.listener = listener;
        this.transferClient = new FileTransferClient(clientSocket, username);
        this.sender = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MediaSender");
            thread.setDaemon(true);
            return thread;
        });
        ensureFolders();
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /**
     * Sends a file in the background.
     *
     * @param receiver username for private chat, or null for group chat
     * @param groupId  group id for group chat, or null for private chat
     */
    public void sendMedia(String receiver, String groupId, File file) {
        sender.execute(() -> {
            try {
                FileInfo info = transferClient.sendFile(receiver, groupId, file,
                        (done, total) -> {
                            int percent = total == 0 ? 100 : (int) (done * 100 / total);
                            if (listener != null) {
                                listener.onSendProgress(file.getName(), percent);
                            }
                        });
                if (listener != null) {
                    listener.onMediaSent(info);
                }
            } catch (IOException e) {
                if (listener != null) {
                    listener.onMediaError("Cannot send "
                            + (file == null ? "file" : file.getName()) + ": " + e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Receiving
    // ------------------------------------------------------------------

    /** Private chat version. See {@link #handlePacket(String, String)}. */
    public boolean handlePacket(String packet) {
        return handlePacket(packet, null);
    }

    /**
     * Call this for every packet received by a chat window.
     *
     * @param expectedGroupId null for private chat, group id for group chat
     * @return true if the packet is a media packet (FILE/IMAGE/VIDEO/SOUND),
     *         so the caller should not treat it as a text message.
     */
    public boolean handlePacket(String packet, String expectedGroupId) {
        String type = FileTransferClient.extract(packet, "type");
        if (!isMediaType(type)) {
            return false;
        }

        try {
            FileInfo info = transferClient.receive(packet, expectedGroupId);
            if (info != null && listener != null) {
                listener.onMediaReceived(info);
            }
        } catch (IOException e) {
            if (listener != null) {
                listener.onMediaError(e.getMessage());
            }
        }
        return true;
    }

    /** Call when the chat window closes. */
    public void shutdown() {
        sender.shutdown();
        transferClient.cancelAll();
    }

    public String getUsername() {
        return username;
    }

    // ------------------------------------------------------------------
    // Folders and types
    // ------------------------------------------------------------------

    /** Creates media/files, media/images, media/videos and media/sounds. */
    public static void ensureFolders() {
        for (PacketType type : new PacketType[]{PacketType.FILE, PacketType.IMAGE,
                PacketType.VIDEO, PacketType.SOUND}) {
            File folder = getFolder(type);
            if (!folder.isDirectory()) {
                folder.mkdirs();
            }
        }
    }

    public static File getFolder(PacketType type) {
        return new File(MEDIA_ROOT, folderName(type));
    }

    public static String folderName(PacketType type) {
        switch (type) {
            case IMAGE: return FOLDER_IMAGES;
            case VIDEO: return FOLDER_VIDEOS;
            case SOUND: return FOLDER_SOUNDS;
            default:    return FOLDER_FILES;
        }
    }

    /** Relative path with forward slashes, e.g. media/images/a.png. */
    public static String toRelativePath(PacketType type, String filename) {
        return MEDIA_ROOT + "/" + folderName(type) + "/" + filename;
    }

    public static PacketType detectType(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";

        if (IMAGE_EXT.contains(ext)) {
            return PacketType.IMAGE;
        }
        if (VIDEO_EXT.contains(ext)) {
            return PacketType.VIDEO;
        }
        if (SOUND_EXT.contains(ext)) {
            return PacketType.SOUND;
        }
        return PacketType.FILE;
    }

    public static boolean isMediaType(String type) {
        return toMediaType(type) != null;
    }

    /** Returns FILE/IMAGE/VIDEO/SOUND for the given name, otherwise null. */
    public static PacketType toMediaType(String type) {
        if (type == null) {
            return null;
        }
        switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "FILE":  return PacketType.FILE;
            case "IMAGE": return PacketType.IMAGE;
            case "VIDEO": return PacketType.VIDEO;
            case "SOUND": return PacketType.SOUND;
            default:      return null;
        }
    }

    // ------------------------------------------------------------------
    // Swing helpers
    // ------------------------------------------------------------------

    /** Opens a file chooser and returns the selected file, or null. */
    public static File chooseFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to send");
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    /**
     * Opens a received item: images are shown in a preview window,
     * other media is opened with the default program of the system.
     */
    public static void openMedia(Component parent, FileInfo info) {
        if (info == null || info.getFile() == null || !info.getFile().exists()) {
            JOptionPane.showMessageDialog(parent, "File not found.");
            return;
        }

        if (info.isImage()) {
            showImage(parent, info);
            return;
        }

        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(info.getFile());
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Saved to " + info.getFile().getAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(parent,
                    "Cannot open file: " + e.getMessage());
        }
    }

    /** Returns a scaled thumbnail for an image, or null if it cannot be read. */
    public static ImageIcon loadThumbnail(FileInfo info, int maxSize) {
        if (info == null || info.getFile() == null) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(info.getFile());
            if (image == null) {
                return null;
            }
            return new ImageIcon(scale(image, maxSize, maxSize));
        } catch (IOException e) {
            return null;
        }
    }

    private static void showImage(Component parent, FileInfo info) {
        ImageIcon icon = loadThumbnail(info, 700);
        if (icon == null) {
            JOptionPane.showMessageDialog(parent, "Cannot display this image.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog(
                    parent == null ? null : SwingUtilities.getWindowAncestor(parent),
                    info.getFilename());
            dialog.add(new JScrollPane(new JLabel(icon, SwingConstants.CENTER)));
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    private static Image scale(BufferedImage image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        double ratio = Math.min(1.0, Math.min((double) maxWidth / width,
                (double) maxHeight / height));
        if (ratio >= 1.0) {
            return image;
        }
        return image.getScaledInstance((int) (width * ratio),
                (int) (height * ratio), Image.SCALE_SMOOTH);
    }
}
