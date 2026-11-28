package emoji;

import javax.swing.ImageIcon;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.Arrays;
import java.util.Comparator;

public final class EmojiTextRenderer {

    private static final String[] EMOJIS = EmojiPanel.getDefaultEmojis();

    static {
        Arrays.sort(EMOJIS, Comparator.comparingInt(String::length).reversed());
    }

    private EmojiTextRenderer() {
    }

    public static void append(JTextPane pane, String text) {
        if (pane == null || text == null || text.isEmpty()) {
            return;
        }

        StyledDocument document = pane.getStyledDocument();
        int lineStart = 0;
        while (lineStart < text.length()) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            appendLine(document, text.substring(lineStart, lineEnd));
            if (newline >= 0) {
                insertText(document, "\n");
            }
            lineStart = lineEnd + 1;
        }
        pane.setCaretPosition(document.getLength());
    }

    private static void appendLine(StyledDocument document, String line) {
        int marker = line.lastIndexOf("]: ");
        String content = marker < 0 ? null : line.substring(marker + 3);
        String standaloneEmoji = content == null ? null : findEmoji(content, 0);
        boolean standaloneSticker = content != null
                && standaloneEmoji != null
                && standaloneEmoji.equals(content);

        for (int offset = 0; offset < line.length();) {
            String emoji = findEmoji(line, offset);
            if (emoji == null) {
                int next = offset + Character.charCount(line.codePointAt(offset));
                insertText(document, line.substring(offset, next));
                offset = next;
                continue;
            }

            int size = standaloneSticker ? 42 : 24;
            ImageIcon icon = EmojiPanel.loadEmojiIcon(emoji, size);
            if (icon == null) {
                insertText(document, "[emoji]");
            } else {
                SimpleAttributeSet attributes = new SimpleAttributeSet();
                StyleConstants.setIcon(attributes, icon);
                try {
                    document.insertString(document.getLength(), "\uFFFC", attributes);
                } catch (BadLocationException exception) {
                    System.err.println("Cannot render emoji: " + exception.getMessage());
                    insertText(document, "[emoji]");
                }
            }
            offset += emoji.length();
        }
    }

    private static String findEmoji(String text, int offset) {
        for (String emoji : EMOJIS) {
            if (text.startsWith(emoji, offset)) {
                return emoji;
            }
        }
        return null;
    }

    private static void insertText(StyledDocument document, String text) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attributes, "Segoe UI");
        StyleConstants.setFontSize(attributes, 14);
        StyleConstants.setForeground(attributes, new Color(0x172B4D));
        try {
            document.insertString(document.getLength(), text, attributes);
        } catch (BadLocationException exception) {
            System.err.println("Cannot append chat text: " + exception.getMessage());
        }
    }
}
