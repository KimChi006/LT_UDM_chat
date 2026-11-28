package ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.border.AbstractBorder;

public final class AppTheme {

    public static final Color PRIMARY = new Color(0x0068FF);
    public static final Color PRIMARY_DARK = new Color(0x0054D1);
    public static final Color PRIMARY_LIGHT = new Color(0xEAF2FF);
    public static final Color BACKGROUND = new Color(0xEEF2F8);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(0x1F2D3D);
    public static final Color MUTED = new Color(0x7A8796);
    public static final Color BORDER = new Color(0xE5EAF1);
    public static final Color CHAT_BACKGROUND = new Color(0xF5F7FB);

    private AppTheme() {
    }

    public static void styleButton(JButton button, boolean primary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? PRIMARY : SURFACE);
        button.setBorder(new RoundedBorder(primary ? PRIMARY : BORDER, 10));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
        final Color normal = primary ? PRIMARY : SURFACE;
        final Color hover = primary ? PRIMARY_DARK : PRIMARY_LIGHT;
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setBackground(normal);
            }
        });
    }

    public static void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(TEXT);
        field.setBackground(SURFACE);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER, 9),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
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
        pane.setBackground(CHAT_BACKGROUND);
        pane.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        pane.setEditable(false);
    }

    public static final class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        public RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(java.awt.Component component, Graphics graphics,
                                int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(java.awt.Component component) {
            return new Insets(8, 12, 8, 12);
        }

        @Override
        public Insets getBorderInsets(java.awt.Component component, Insets insets) {
            insets.left = insets.right = 12;
            insets.top = insets.bottom = 8;
            return insets;
        }
    }
}
