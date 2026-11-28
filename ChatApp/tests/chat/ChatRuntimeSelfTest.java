package chat;

import history.HistoryManager;
import media.FileTransferClient;
import media.FileInfo;
import media.MediaManager;
import model.PacketType;
import server.ServerMain;
import storage.GroupStorage;
import storage.JsonStorage;
import storage.MessageStorage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChatRuntimeSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testCloseUnblocksSocketReader();
        testHomeInboxStoresIncomingMessage();
        testMessagesRouteBothWaysAfterReconnect();
        System.out.println("RESULT: passed=" + passed + ", failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testCloseUnblocksSocketReader() throws Exception {
        System.out.println("-- ClientSocket close --");
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            ClientSocket client = new ClientSocket(
                    "127.0.0.1", serverSocket.getLocalPort());
            check("client connects", client.connect());
            Socket accepted = serverSocket.accept();

            CountDownLatch reading = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                reading.countDown();
                try {
                    client.getReader().readLine();
                } catch (IOException ignored) {
                    // Closing the socket is expected to unblock the reader.
                }
            }, "SocketCloseTestReader");
            reader.setDaemon(true);
            reader.start();
            reading.await(1, TimeUnit.SECONDS);
            Thread.sleep(100);

            Thread closer = new Thread(client::close, "SocketCloseTestCloser");
            closer.setDaemon(true);
            closer.start();
            closer.join(2000);
            reader.join(2000);

            check("closing a chat socket does not block", !closer.isAlive());
            check("socket reader exits after close", !reader.isAlive());
            accepted.close();
        }
    }

    private static void testHomeInboxStoresIncomingMessage() throws Exception {
        System.out.println("-- Home inbox delivery --");
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        ServerMain server = new ServerMain(port);
        Thread serverThread = new Thread(() -> server.startServer(port), "ChatHomeInboxTestServer");
        serverThread.setDaemon(true);
        serverThread.start();

        Path tempFolder = Files.createTempDirectory("chat-home-inbox-");
        Path messagesFile = tempFolder.resolve("messages.json");
        HistoryManager history = new HistoryManager(
                new MessageStorage(new JsonStorage(messagesFile.toFile())));
        CountDownLatch messageReceived = new CountDownLatch(1);
        ClientSocket hoaSocket = new ClientSocket("127.0.0.1", port);
        MessageListener hoaListener = null;
        TestClient chi = null;
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (!server.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            check("home inbox test server starts", server.isRunning());
            check("home inbox connects", hoaSocket.connect());
            hoaSocket.sendPacket("{\"type\":\"LOGIN\",\"username\":\"hoa\"}");

            ChatInbox inbox = new ChatInbox("hoa", history,
                    new GroupStorage(new JsonStorage(tempFolder.resolve("groups.json").toFile())),
                    new ChatInbox.Listener() {
                        @Override
                        public void onPrivateMessage(String sender) {
                            messageReceived.countDown();
                        }

                        @Override
                        public void onGroupMessage(String groupName) {
                        }

                        @Override
                        public void onDisconnected() {
                        }
                    });
            hoaListener = new MessageListener(hoaSocket, inbox);
            hoaListener.start();

            deadline = System.currentTimeMillis() + 3000;
            while (!server.getClients().containsKey("hoa")
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            check("home inbox registers the signed-in user",
                    server.getClients().containsKey("hoa"));

            chi = new TestClient(port, "chi");
            chi.login();
            chi.sendMessage("hoa", "message-while-home-is-open-😀");

            check("message reaches the recipient while home is open",
                    messageReceived.await(3, TimeUnit.SECONDS));
            List<Message> messages = history.getPrivateHistory("chi", "hoa");
            check("home inbox saves incoming text and emoji",
                    messages.size() == 1
                            && "message-while-home-is-open-😀"
                            .equals(messages.get(0).getContent()));
        } finally {
            if (chi != null) {
                chi.close();
            }
            if (hoaListener != null) {
                hoaListener.stop();
            } else {
                hoaSocket.close();
            }
            server.stopServer();
            serverThread.join(2000);
            Files.deleteIfExists(messagesFile);
            Files.deleteIfExists(tempFolder.resolve("groups.json"));
            Files.deleteIfExists(tempFolder);
        }
    }

    private static void testMessagesRouteBothWaysAfterReconnect() throws Exception {
        System.out.println("-- Private message routing --");
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        ServerMain server = new ServerMain(port);
        Thread serverThread = new Thread(() -> server.startServer(port), "ChatRuntimeTestServer");
        serverThread.setDaemon(true);
        serverThread.start();

        TestClient oldChi = null;
        TestClient chi = null;
        TestClient hoa = null;
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (!server.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            check("test server starts", server.isRunning());

            oldChi = new TestClient(port, "chi");
            oldChi.login();
            chi = new TestClient(port, "chi");
            chi.login();
            hoa = new TestClient(port, "hoa");
            hoa.login();

            oldChi.close();
            Thread.sleep(150);
            check("old connection cannot remove the reconnected account",
                    server.getClients().containsKey("chi"));

            hoa.sendMessage("chi", "hoa-to-chi");
            check("Hoa message reaches Chi", chi.awaitMessage("hoa-to-chi"));

            chi.sendMessage("hoa", "chi-to-hoa");
            check("Chi message reaches Hoa", hoa.awaitMessage("chi-to-hoa"));

            String filename = "runtime-test-" + UUID.randomUUID() + ".txt";
            byte[] content = "media-transfer-check".getBytes(StandardCharsets.UTF_8);
            String mediaPacket = "{\"type\":\"FILE\",\"sender\":\"hoa\","
                    + "\"receiver\":\"chi\",\"transferid\":\""
                    + UUID.randomUUID() + "\",\"filename\":\"" + filename
                    + "\",\"filesize\":\"" + content.length
                    + "\",\"totalchunks\":\"1\",\"chunkindex\":\"0\",\"data\":\""
                    + Base64.getEncoder().encodeToString(content) + "\"}";
            hoa.sendPacket(mediaPacket);
            String receivedPacket = chi.readPacket("\"type\":\"FILE\"",
                    "\"filename\":\"" + filename + "\"");
            FileInfo receivedFile = null;
            try {
                if (receivedPacket != null) {
                    receivedFile = new FileTransferClient(null, "chi")
                            .receive(receivedPacket, null);
                }
                check("media file reaches and is assembled by the receiver",
                        receivedFile != null && receivedFile.getFile().isFile()
                                && java.util.Arrays.equals(content,
                                Files.readAllBytes(receivedFile.getFile().toPath())));
            } finally {
                File mediaFile = new File(MediaManager.getFolder(PacketType.FILE), filename);
                Files.deleteIfExists(mediaFile.toPath());
            }

            chi.sendPacket("{\"type\":\"GROUP_MESSAGE\",\"sender\":\"chi\","
                    + "\"groupId\":\"runtime-group\",\"content\":\"group-round-trip\"}");
            check("group messages reach other clients",
                    hoa.awaitPacket("\"type\":\"GROUP_MESSAGE\"",
                            "\"content\":\"group-round-trip\""));
        } finally {
            if (oldChi != null) {
                oldChi.close();
            }
            if (chi != null) {
                chi.close();
            }
            if (hoa != null) {
                hoa.close();
            }
            server.stopServer();
            serverThread.join(2000);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static class TestClient implements AutoCloseable {

        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private final String username;

        TestClient(int port, String username) throws IOException {
            this.username = username;
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        void login() throws IOException {
            writer.println("{\"type\":\"LOGIN\",\"username\":\"" + username + "\"}");
            for (int count = 0; count < 2; count++) {
                String packet = readPacket("\"type\":\"STATUS\"",
                        "\"username\":\"" + username + "\"");
                if (packet == null) {
                    throw new IOException("No login acknowledgement for " + username);
                }
            }
        }

        void sendMessage(String receiver, String content) {
            sendPacket("{\"type\":\"MESSAGE\",\"sender\":\"" + username
                    + "\",\"receiver\":\"" + receiver
                    + "\",\"content\":\"" + content + "\"}");
        }

        void sendPacket(String packet) {
            writer.println(packet);
        }

        boolean awaitMessage(String content) throws IOException {
            return awaitPacket("\"type\":\"MESSAGE\"", "\"content\":\"" + content + "\"");
        }

        boolean awaitPacket(String type, String field) throws IOException {
            return readPacket(type, field) != null;
        }

        String readPacket(String type, String field) throws IOException {
            String packet;
            while ((packet = reader.readLine()) != null) {
                if (packet.contains(type) && packet.contains(field)) {
                    return packet;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
