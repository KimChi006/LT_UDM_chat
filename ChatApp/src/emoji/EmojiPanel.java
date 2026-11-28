package emoji;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.GridLayout;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class EmojiPanel extends JPanel {

    private static final String[] DEFAULT_EMOJIS = {
            "😀", "😂", "😍", "😎", "😢", "😡",
            "👍", "👎", "👏", "🎉", "❤️", "🔥",
            "🤣", "🤪", "🤡", "💩", "👻", "🤖",
            "👽", "🐸", "😹", "🙈", "🙉", "🙊",
            "🦄", "🍕", "🍔", "😜", "😈", "👾",
            "💥", "🍿", "🧐", "🤯", "🫠", "🥵",
            "🧸", "😏"
    };

    private final Set<EmojiListener> listeners =
            java.util.Collections.synchronizedSet(new HashSet<EmojiListener>());

    public EmojiPanel() {
        this(DEFAULT_EMOJIS, false);
    }

    public EmojiPanel(String[] emojis) {
        this(emojis, false);
    }

    public EmojiPanel(String[] emojis, boolean largeItems) {
        super(new GridLayout(0, 6, 4, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Color.WHITE);

        String[] emojiItems = emojis == null || emojis.length == 0
                ? DEFAULT_EMOJIS : emojis;
        for (final String emoji : emojiItems) {
            int iconSize = largeItems ? 42 : 26;
            ImageIcon icon = loadEmojiIcon(emoji, iconSize);
            JButton button = icon == null
                    ? new JButton(emoji) : new JButton("", icon);
            button.setFont(new Font("Segoe UI Emoji", Font.PLAIN,
                    largeItems ? 34 : 20));
            button.setToolTipText(emoji);
            int buttonSize = largeItems ? 58 : 38;
            button.setPreferredSize(new java.awt.Dimension(buttonSize, buttonSize));
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
            button.addActionListener(event -> notifyListeners(emoji));
            add(button);
        }
    }

    public static String[] getDefaultEmojis() {
        return DEFAULT_EMOJIS.clone();
    }

    public static ImageIcon loadEmojiIcon(String emoji, int iconSize) {
        StringBuilder codepoints = new StringBuilder();
        for (int offset = 0; offset < emoji.length();) {
            int codepoint = emoji.codePointAt(offset);
            offset += Character.charCount(codepoint);
            if (codepoint != 0xfe0f) {
                if (codepoints.length() > 0) {
                    codepoints.append('-');
                }
                codepoints.append(Integer.toHexString(codepoint));
            }
        }

        String filename = codepoints + ".png";
        URL resource = EmojiPanel.class.getResource("/emoji/" + filename);
        if (resource != null) {
            return createIcon(resource, iconSize);
        }

        Path path = Paths.get("resources", "emoji", filename);
        if (!Files.isRegularFile(path)) {
            path = Paths.get("ChatApp", "resources", "emoji", filename);
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return createIcon(path.toUri().toURL(), iconSize);
        } catch (java.net.MalformedURLException exception) {
            System.err.println("Cannot load emoji icon " + path + ": "
                    + exception.getMessage());
            return null;
        }
    }

    private static ImageIcon createIcon(URL resource, int iconSize) {
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(
                iconSize, iconSize, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    public void addEmojiListener(EmojiListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeEmojiListener(EmojiListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String emoji) {
        Set<EmojiListener> snapshot;
        synchronized (listeners) {
            snapshot = new HashSet<EmojiListener>(listeners);
        }
        for (EmojiListener listener : snapshot) {
            listener.onEmojiSelected(emoji);
        }
    }

    public interface EmojiListener {
        void onEmojiSelected(String emoji);
    }
}