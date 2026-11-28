package storage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FriendStorageSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("friend-storage-test-").toFile();
        File file = new File(root, "friends.json");
        FriendStorage storage = new FriendStorage(new JsonStorage(file));

        check("no friendship before accepting request",
                storage.getFriends("chi").isEmpty());
        check("request can be sent", storage.sendRequest("chi", "hoa"));
        check("request does not add sender as a friend",
                storage.getFriends("chi").isEmpty());
        check("request appears for recipient",
                storage.getIncomingRequests("hoa").contains("chi"));
        check("duplicate request is rejected",
                !storage.sendRequest("chi", "hoa"));
        check("self request is rejected",
                !storage.sendRequest("chi", "chi"));
        check("recipient can accept request",
                storage.acceptRequest("hoa", "chi"));
        check("acceptance creates mutual friendship",
                storage.getFriends("hoa").contains("chi")
                        && storage.getFriends("chi").contains("hoa"));
        check("accepted request is removed",
                storage.getIncomingRequests("hoa").isEmpty());
        check("friendship persists after reopening",
                new FriendStorage(new JsonStorage(file))
                        .getFriends("chi").contains("hoa"));

        check("new request can be declined",
                storage.sendRequest("lan", "hoa")
                        && storage.declineRequest("hoa", "lan"));
        check("declined request does not create friendship",
                !storage.getFriends("lan").contains("hoa"));
        check("declined request is removed",
                storage.getIncomingRequests("hoa").isEmpty());

        File brokenFile = new File(root, "broken.json");
        String broken = "{ invalid";
        Files.write(brokenFile.toPath(), broken.getBytes(StandardCharsets.UTF_8));
        FriendStorage brokenStorage =
                new FriendStorage(new JsonStorage(brokenFile));
        check("invalid JSON refuses relationship updates",
                !brokenStorage.sendRequest("a", "b"));
        check("invalid JSON is preserved",
                broken.equals(new String(Files.readAllBytes(brokenFile.toPath()),
                        StandardCharsets.UTF_8)));

        delete(file);
        delete(brokenFile);
        Files.deleteIfExists(root.toPath());
        System.out.println("RESULT: passed=" + passed + ", failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void delete(File file) throws Exception {
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(new File(file.getParentFile(),
                file.getName() + ".tmp").toPath());
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
