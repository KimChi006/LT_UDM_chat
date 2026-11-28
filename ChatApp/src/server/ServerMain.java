package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {

    public static final int SERVER_PORT = 8080;
    public static final int MAX_CLIENT = 100;

    private ServerSocket serverSocket;
    private final ConcurrentMap<String, ClientHandler> clients;
    private final MessageRouter messageRouter;
    private final AccountService accountService;
    private final ExecutorService clientExecutor;
    private final int port;
    private volatile boolean running;

    public ServerMain() {
        this(SERVER_PORT);
    }

    public ServerMain(int port) {
        this(port, new AccountService());
    }

    public ServerMain(int port, AccountService accountService) {
        if (accountService == null) {
            throw new IllegalArgumentException("Account service is required.");
        }
        this.port = port;
        clients = new ConcurrentHashMap<String, ClientHandler>();
        messageRouter = new MessageRouter(clients);
        this.accountService = accountService;
        clientExecutor = Executors.newFixedThreadPool(MAX_CLIENT);
    }

    public static void main(String[] args) {
        int port = SERVER_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                System.err.println("Invalid port: " + args[0] + ". Using " + SERVER_PORT);
            }
        }

        ServerMain server = new ServerMain(port);
        server.startServer();
    }

    public void startServer() {
        startServer(port);
    }

    public void startServer(int port) {
        if (running) {
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(
                        clientSocket, messageRouter, this);
                clientExecutor.execute(clientHandler);
            }
        } catch (IOException exception) {
            if (running || serverSocket == null || !serverSocket.isClosed()) {
                System.err.println("Cannot start or continue server on port "
                        + port + ": " + exception.getMessage());
            }
        } finally {
            running = false;
        }
    }

    public void stopServer() {
        running = false;
        for (ClientHandler clientHandler : clients.values()) {
            clientHandler.close();
        }
        clients.clear();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException exception) {
                System.err.println("Error closing server: " + exception.getMessage());
            }
        }
        clientExecutor.shutdownNow();
    }

    public void registerClient(String username, ClientHandler clientHandler) {
        if (username != null && clientHandler != null) {
            clients.put(username, clientHandler);
            messageRouter.registerClient(username, clientHandler);
        }
    }

    public void unregisterClient(String username) {
        if (username == null) {
            return;
        }
        ClientHandler current = clients.get(username);
        if (current != null) {
            unregisterClient(username, current);
        }
    }

    public boolean unregisterClient(String username, ClientHandler expectedHandler) {
        if (username == null || expectedHandler == null) {
            return false;
        }
        boolean removed = clients.remove(username, expectedHandler);
        messageRouter.unregisterClient(username, expectedHandler);
        return removed;
    }

    public Map<String, ClientHandler> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    public MessageRouter getMessageRouter() {
        return messageRouter;
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public boolean isRunning() {
        return running;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}