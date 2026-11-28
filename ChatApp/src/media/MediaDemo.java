package media;

import chat.ClientSocket;
import chat.MessageListener;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Cửa sổ kiểm tra gửi tệp khi không mở ChatUI.
 */
public class MediaDemo extends JFrame implements MessageListener.PacketHandler {

    private final ClientSocket clientSocket;
    private final MediaManager mediaManager;

    private final JTextArea log = new JTextArea();
    private FileInfo lastReceived;

    public MediaDemo(String username, String receiver) {
        setTitle("Thử gửi tệp - " + username + " đến " + receiver);
        setSize(520, 420);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        log.setEditable(false);
        add(new JScrollPane(log), BorderLayout.CENTER);

        JButton sendButton = new JButton("Gửi tệp...");
        JButton openButton = new JButton("Mở tệp vừa nhận");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(sendButton);
        bottom.add(openButton);
        add(bottom, BorderLayout.SOUTH);

        clientSocket = new ClientSocket("localhost", 8080);
        if (!clientSocket.connect()) {
            log("Không thể kết nối máy chủ.");
            sendButton.setEnabled(false);
        } else {
            clientSocket.sendPacket("{\"type\":\"LOGIN\",\"username\":\""
                    + FileTransferClient.escape(username) + "\"}");
            new MessageListener(clientSocket, this).start();
            log("Đã kết nối với tài khoản " + username);
        }

        mediaManager = new MediaManager(clientSocket, username, new MediaManager.MediaListener() {
            @Override
            public void onMediaReceived(FileInfo info) {
                lastReceived = info;
                log(info.getSender() + " đã gửi " + info + " → " + info.getFilepath());
            }

            @Override
            public void onMediaSent(FileInfo info) {
                log("Đã gửi " + info);
            }

            @Override
            public void onSendProgress(String filename, int percent) {
                if (percent % 25 == 0) {
                    log("  " + filename + ": " + percent + "%");
                }
            }

            @Override
            public void onMediaError(String message) {
                log("Lỗi: " + message);
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
        log("Đã mất kết nối máy chủ.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(null, "Tên người dùng:");
            if (username == null || username.trim().isEmpty()) {
                return;
            }
            String receiver = JOptionPane.showInputDialog(null, "Gửi đến người dùng nào?");
            if (receiver == null || receiver.trim().isEmpty()) {
                return;
            }
            new MediaDemo(username.trim(), receiver.trim());
        });
    }
}
