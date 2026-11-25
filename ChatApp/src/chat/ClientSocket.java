package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientSocket {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private String serverHost;
    private int serverPort;

    public ClientSocket(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public boolean connect() {
        try {
            socket = new Socket(serverHost, serverPort);

            reader = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            writer = new PrintWriter(
                    socket.getOutputStream(),
                    true,
                    StandardCharsets.UTF_8
            );

            System.out.println("Connected to server: "
                    + serverHost + ":" + serverPort);

            return true;

        } catch (IOException e) {
            System.err.println("Cannot connect to server: "
                    + e.getMessage());
            close();
            return false;
        }
    }

    public void sendPacket(String packet) {
        if (writer == null) {
            System.err.println("Connection is not established.");
            return;
        }

        writer.println(packet);

        if (writer.checkError()) {
            System.err.println("Error sending packet to server.");
        }
    }

    public BufferedReader getReader() {
        return reader;
    }

    public boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }

    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing reader: "
                    + e.getMessage());
        }

        if (writer != null) {
            writer.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: "
                    + e.getMessage());
        }

        socket = null;
        reader = null;
        writer = null;
    }
}