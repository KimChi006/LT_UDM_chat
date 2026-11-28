package chat;

import status.StatusManager;

import java.io.BufferedReader;
import java.io.IOException;

public class MessageListener implements Runnable {

    private final ClientSocket clientSocket;
    private final PacketHandler packetHandler;
    private volatile boolean running;

    public interface PacketHandler {
        void onPacketReceived(String packet);
        void onDisconnected();
    }

    public MessageListener(ClientSocket clientSocket,
                         PacketHandler packetHandler) {
        this.clientSocket = clientSocket;
        this.packetHandler = packetHandler;
        this.running = false;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;

        Thread listenerThread = new Thread(this, "MessageListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public void run() {
        BufferedReader reader = clientSocket.getReader();

        if (reader == null) {
            running = false;
            packetHandler.onDisconnected();
            return;
        }

        try {
            String packet;
            while (running && (packet = reader.readLine()) != null) {
                StatusManager.getInstance().updateFromPacket(packet);
                packetHandler.onPacketReceived(packet);
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("Error receiving packet: "
                        + e.getMessage());
            }
        } finally {
            running = false;
            packetHandler.onDisconnected();
        }
    }

    public void stop() {
        running = false;
        clientSocket.close();
    }

    public boolean isRunning() {
        return running;
    }
}