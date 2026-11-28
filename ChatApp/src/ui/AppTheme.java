package ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

public final class AppTheme {

    public static final Color PRIMARY = new Color(0x1684F8);
    public static final Color PRIMARY_DARK = new Color(0x0C6ED5);
    public static final Color PRIMARY_LIGHT = new Color(0xEAF3FF);
    public static final Color BACKGROUND = new Color(0xF3F6FA);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(0x172B4D);
    public static final Color MUTED = new Color(0x78869A);
    public static final Color BORDER = new Color(0xE3E9F0);

    private AppTheme() {
    }

    public static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? PRIMARY : SURFACE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? PRIMARY : BORDER),
                BorderFactory.createEmptyBorder(9, 15, 9, 15)
        ));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT);
        field.setBackground(SURFACE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(9, 11, 9, 11)
        ));
    }

    public static void styleChatArea(JTextArea area) {
        area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        area.setForeground(TEXT);
        area.setBackground(BACKGROUND);
        area.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
    }

    public static void styleChatPane(JTextPane pane) {
        pane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        pane.setForeground(TEXT);
        pane.setBackground(BACKGROUND);
        pane.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        pane.setEditable(false);
    }
}
