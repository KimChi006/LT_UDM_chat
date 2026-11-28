package emoji;

import javax.swing.JTextPane;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;

public class EmojiTextRendererSelfTest {

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failures = 0;
        String[] emojis = EmojiPanel.getDefaultEmojis();
        for (String emoji : emojis) {
            if (EmojiPanel.loadEmojiIcon(emoji, 24) == null) {
                System.out.println("FAIL  emoji asset exists for " + emoji);
                failures++;
            } else {
                passed++;
            }
        }

        JTextPane pane = new JTextPane();
        EmojiTextRenderer.append(pane, "chi [time]: 😀\n");
        StyledDocument document = pane.getStyledDocument();
        String text = document.getText(0, document.getLength());
        int iconOffset = text.indexOf('\uFFFC');
        boolean renderedAsIcon = iconOffset >= 0
                && StyleConstants.getIcon(
                document.getCharacterElement(iconOffset).getAttributes()) != null;
        if (renderedAsIcon && !text.contains("😀")) {
            System.out.println("PASS  chat transcript renders emoji as an image");
            passed++;
        } else {
            System.out.println("FAIL  chat transcript renders emoji as an image");
            failures++;
        }

        if (failures > 0) {
            throw new AssertionError(failures + " emoji rendering checks failed");
        }
        System.out.println("RESULT: passed=" + passed + ", failed=0");
    }
}
