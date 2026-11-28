package emoji;

import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.Component;

public final class EmojiPickerPopup {

    private static final String[] STICKERS = {
            "😀", "😂", "😍", "😎", "😢", "😡",
            "👍", "👏", "🎉", "❤️", "🔥", "💥",
            "🐸", "🦄", "👻", "🤖", "🍕", "🍔"
    };

    private final JPopupMenu popup = new JPopupMenu();

    public EmojiPickerPopup(SelectionListener listener) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Emoji", createPanel(null, false, listener));
        tabs.addTab("Sticker", createPanel(STICKERS, true, listener));
        tabs.setPreferredSize(new java.awt.Dimension(370, 340));
        popup.add(tabs);
    }

    private JScrollPane createPanel(String[] symbols, boolean sticker,
                                    SelectionListener listener) {
        EmojiPanel panel = symbols == null
                ? new EmojiPanel() : new EmojiPanel(symbols, true);
        panel.addEmojiListener(emoji -> {
            popup.setVisible(false);
            if (listener != null) {
                listener.onSelected(emoji, sticker);
            }
        });

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(java.awt.Color.WHITE);
        return scrollPane;
    }

    public void showAt(Component invoker) {
        if (invoker != null && invoker.isShowing()) {
            popup.show(invoker, 0, -popup.getPreferredSize().height);
        }
    }

    public interface SelectionListener {
        void onSelected(String symbol, boolean sticker);
    }
}
