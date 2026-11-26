package media;

import chat.ClientSocket;
import chat.MessageListener;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Small stand-alone window to test the media module without ChatUI.
 * Start ServerMain first, then run this class twice (two users).
 */
public class MediaDemo extends JFrame implements MessageListener.PacketHandler {

    private final String username;
    private final String receiver;
    private final ClientSocket clientSocket;
    private final MediaManager mediaManager;

    private final JTextArea log = new JTextArea();
    private FileInfo lastReceived;

    public MediaDemo(String username, String receiver) {
        this.username = username;
        this.receiver = receiver;

        setTitle("Media demo - " + username + " -> " + receiver);
        setSize(520, 420);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        log.setEditable(false);
        add(new JScrollPane(log), BorderLayout.CENTER);

        JButton sendButton = new JButton("Send file...");
        JButton openButton = new JButton("Open last received");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(sendButton);
        bottom.add(openButton);
        add(bottom, BorderLayout.SOUTH);

        clientSocket = new ClientSocket("localhost", 8080);
        if (!clientSocket.connect()) {
            log("[System] Cannot connect to server.");
            sendButton.setEnabled(false);
        } else {
            clientSocket.sendPacket("{\"type\":\"LOGIN\",\"username\":\""
                    + FileTransferClient.escape(username) + "\"}");
            new MessageListener(clientSocket, this).start();
            log("[System] Connected as " + username);
        }

        mediaManager = new MediaManager(clientSocket, username, new MediaManager.MediaListener() {
            @Override
            public void onMediaReceived(FileInfo info) {
                lastReceived = info;
                log(info.getSender() + " sent " + info + " -> " + info.getFilepath());
            }

            @Override
            public void onMediaSent(FileInfo info) {
                log("Sent " + info);
            }

            @Override
            public void onSendProgress(String filename, int percent) {
                if (percent % 25 == 0) {
                    log("  " + filename + ": " + percent + "%");
                }
            }

            @Override
            public void onMediaError(String message) {
                log("[Error] " + message);
            }
        });

        sendButton.addActionListener(e -> {
            File file = MediaManager.chooseFile(this);
            if (file != null) {
                mediaManager.sendMedia(receiver, null, file);
            }
        });
        openButton.addActionListener(e -> MediaManager.openMedia(this, lastReceived));

        setVisible(true);
    }

    private void log(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    @Override
    public void onPacketReceived(String packet) {
        mediaManager.handlePacket(packet);
    }

    @Override
    public void onDisconnected() {
        log("[System] Disconnected from server.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null, "Your username:");
            if (username == null || username.trim().isEmpty()) {
                return;
            }
            String receiver = JOptionPane.showInputDialog(null, "Send to which user?");
            if (receiver == null || receiver.trim().isEmpty()) {
                return;
            }
            new MediaDemo(username.trim(), receiver.trim());
        });
    }
}
