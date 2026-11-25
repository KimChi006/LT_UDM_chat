package auth;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Image;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class UIIcons {

    private UIIcons() {
    }

    static JLabel createLabel(String text, String filename) {
        ImageIcon icon = loadIcon(filename);
        if (icon == null) {
            return new JLabel(text);
        }
        JLabel label = new JLabel(text, icon, SwingConstants.LEFT);
        label.setIconTextGap(6);
        return label;
    }

    static ImageIcon loadIcon(String filename) {
        return loadIcon(filename, 20);
    }

    static ImageIcon loadIcon(String filename, int size) {
        URL resource = UIIcons.class.getResource("/icons/" + filename);
        if (resource == null) {
            Path path = Paths.get("resources", "icons", filename);
            if (!Files.isRegularFile(path)) {
                path = Paths.get("ChatApp", "resources", "icons", filename);
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                resource = path.toUri().toURL();
            } catch (MalformedURLException exception) {
                System.err.println("Cannot load UI icon " + path + ": "
                        + exception.getMessage());
                return null;
            }
        }

        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(
                size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
}
