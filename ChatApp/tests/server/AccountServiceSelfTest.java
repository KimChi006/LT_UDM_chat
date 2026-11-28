package server;

import auth.AccountClient;
import com.google.gson.JsonObject;
import storage.FriendStorage;
import storage.JsonStorage;
import storage.UserStorage;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AccountServiceSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("account-service-test-").toFile();
        File usersFile = new File(root, "users.json");
        File friendsFile = new File(root, "friends.json");
        UserStorage userStorage = new UserStorage(new JsonStorage(usersFile));
        FriendStorage friendStorage = new FriendStorage(new JsonStorage(friendsFile));
        userStorage.saveAll(java.util.Collections.singletonList(
                new model.User("Legacy", "legacy-pw", "OFFLINE")));
        friendStorage.sendRequest("0900000001", "Legacy");
        AccountService service = new AccountService(
                userStorage, friendStorage);
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        ServerMain server = new ServerMain(port, service);
        Thread serverThread = new Thread(server::startServer,
                "AccountServiceSelfTestServer");
        serverThread.setDaemon(true);
        serverThread.start();
        try {
            long deadline = System.currentTimeMillis() + 3000;
            while (!server.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            check("account server starts", server.isRunning());

            AccountClient client = new AccountClient("127.0.0.1", port);
            checkStatus("first account registers",
                    client.register("Chi", "0900000001", "pw1"), "OK");
            checkStatus("same display name can register with another phone",
                    client.register("Chi", "0900000002", "pw2"), "OK");
            checkStatus("duplicate phone number is rejected",
                    client.register("Hoa", "0900000001", "other"), "DUPLICATE_PHONE");
            checkStatus("nine-digit phone number is rejected",
                    client.register("Invalid", "090000001", "pw"), "INVALID");
            checkStatus("eleven-digit phone number is rejected",
                    client.register("Invalid", "09000000011", "pw"), "INVALID");
            checkStatus("valid account can log in",
                    client.authenticate("0900000001", "pw1"), "OK");
            checkStatus("invalid password is rejected",
                    client.authenticate("0900000001", "wrong"), "INVALID");

            JsonObject directory = client.listUsers("0900000001", "pw1");
            List<model.User> users = AccountClient.userList(directory);
            check("account directory is shared by server",
                    "OK".equals(AccountClient.value(directory, "status"))
                            && users.size() == 3
                            && users.stream().filter(user ->
                            "Chi".equals(user.getUsername())).count() == 2);
            checkStatus("directory requires valid credentials",
                    client.listUsers("0900000001", "wrong"), "UNAUTHORIZED");

            checkStatus("friend request reaches central server",
                    client.sendFriendRequest(
                            "0900000001", "pw1", "0900000002"), "OK");
            JsonObject hoaFriends = client.getFriendData("0900000002", "pw2");
            check("recipient can see request from another device",
                    AccountClient.stringList(hoaFriends, "requests")
                            .contains("0900000001"));
            check("request does not expose a friend before acceptance",
                    AccountClient.stringList(
                            client.getFriendData("0900000001", "pw1"),
                            "friends").isEmpty());
            checkStatus("recipient accepts the request",
                    client.respondFriendRequest("0900000002", "pw2",
                            "0900000001", true), "OK");
            check("accepted friendship is shared in both directions",
                    AccountClient.stringList(
                            client.getFriendData("0900000001", "pw1"),
                            "friends").contains("0900000002")
                            && AccountClient.stringList(
                            client.getFriendData("0900000002", "pw2"),
                            "friends").contains("0900000001"));

            checkStatus("legacy account can add a phone number",
                    client.register("Legacy", "0900000003", "legacy-pw"), "OK");
            checkStatus("migrated legacy account logs in by phone",
                    client.authenticate("0900000003", "legacy-pw"), "OK");
            check("legacy friend request remains after phone migration",
                    AccountClient.stringList(
                            client.getFriendData("0900000003", "legacy-pw"),
                            "requests").contains("0900000001"));
        } finally {
            server.stopServer();
            serverThread.join(TimeUnit.SECONDS.toMillis(2));
            Files.deleteIfExists(new File(root, "users.json.tmp").toPath());
            Files.deleteIfExists(new File(root, "friends.json.tmp").toPath());
            Files.deleteIfExists(usersFile.toPath());
            Files.deleteIfExists(friendsFile.toPath());
            Files.deleteIfExists(root.toPath());
        }

        System.out.println("RESULT: passed=" + passed + ", failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void checkStatus(String name, JsonObject response, String expected) {
        check(name, expected.equals(AccountClient.value(response, "status")));
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
}
