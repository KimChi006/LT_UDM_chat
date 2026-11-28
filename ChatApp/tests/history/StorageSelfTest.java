package history;

import chat.Message;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import group.Group;
import group.GroupManager;
import media.FileInfo;
import model.PacketType;
import model.User;
import storage.GroupStorage;
import storage.JsonStorage;
import storage.MessageStorage;
import storage.UserStorage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Small console test for TV4. No JUnit is needed: run main() and read PASS/FAIL.
 */
public class StorageSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("tv4-test-").toFile();
        System.out.println("Temporary test folder: " + root.getAbsolutePath());
        System.out.println("Resolved data file: " + JsonStorage.resolveDataFile("messages.json").getPath());

        testJsonStorage(root);
        testMessageStorage(root);
        testUserStorage(root);
        testGroupStorage(root);
        testHistoryManager(root);
        testGroupCreateHelper();
        testGroupCreationFlow(root);
        testExistingDataFiles();

        System.out.println("RESULT: passed=" + passed + ", failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testJsonStorage(File root) throws Exception {
        System.out.println("\n-- JsonStorage --");

        JsonStorage missing = new JsonStorage(new File(root, "missing.json"));
        check("missing file does not exist", !missing.exists());
        check("missing file reads as empty text", "".equals(missing.readText()));
        JsonArray emptyArray = missing.readArray();
        check("missing file reads as empty array", emptyArray != null && emptyArray.size() == 0);

        JsonStorage normal = new JsonStorage(new File(root, "normal.json"));
        check("write JSON text", normal.writeText("[{\"sender\":\"chi\"}]"));
        check("read JSON text back", normal.readText().contains("chi"));
        JsonArray array = normal.readArray();
        check("parse JSON array", array != null && array.size() == 1);

        File emptyFile = new File(root, "empty.json");
        Files.write(emptyFile.toPath(), new byte[0]);
        JsonStorage empty = new JsonStorage(emptyFile);
        check("empty file reads as empty text", "".equals(empty.readText()));
        JsonArray fromEmpty = empty.readArray();
        check("empty file reads as empty array", fromEmpty != null && fromEmpty.size() == 0);

        File badFile = new File(root, "bad.json");
        String brokenText = "{ this is not json";
        Files.write(badFile.toPath(), brokenText.getBytes(StandardCharsets.UTF_8));
        JsonStorage bad = new JsonStorage(badFile);
        check("malformed JSON returns null", bad.readArray() == null);

        MessageStorage refused = new MessageStorage(bad);
        Message message = new Message("MESSAGE", "chi", "nam", "test", null);
        check("save is refused when JSON is broken", !refused.save(message));
        String afterRefusal = new String(Files.readAllBytes(badFile.toPath()), StandardCharsets.UTF_8);
        check("broken file is not overwritten", brokenText.equals(afterRefusal));
    }

    private static void testMessageStorage(File root) throws Exception {
        System.out.println("\n-- MessageStorage --");

        File file = new File(root, "messages.json");
        MessageStorage storage = new MessageStorage(new JsonStorage(file));

        Message first = message("MESSAGE", "chi", "nam", "hello", null, "2026-11-22 10:30:00");
        Message second = message("MESSAGE", "nam", "chi", "hi", null, "2026-11-22 10:31:00");
        Message third = message("MESSAGE", "chi", "lan", "other", null, "2026-11-22 10:32:00");
        Message groupOne = message("GROUP_MESSAGE", "chi", null, "group one", "group-1", "2026-11-22 10:33:00");
        Message groupTwo = message("GROUP_MESSAGE", "nam", null, "group two", "group-2", "2026-11-22 10:34:00");

        check("save first message", storage.save(first));
        check("save second message", storage.save(second));
        check("save third message", storage.save(third));
        check("save group one", storage.save(groupOne));
        check("save group two", storage.save(groupTwo));
        check(" five messages loaded", storage.loadAll().size() == 5);

        List<Message> privateHistory = storage.getPrivateHistory("chi", "nam");
        check("private history is two-way", privateHistory.size() == 2);
        check("private history has A to B", containsContent(privateHistory, "hello"));
        check("private history has B to A", containsContent(privateHistory, "hi"));

        check("other private chat is separate",
                storage.getPrivateHistory("chi", "lan").size() == 1);
        check("group messages do not pollute private history",
                !containsContent(privateHistory, "group one"));

        List<Message> oneGroup = storage.getGroupHistory("group-1");
        check("group history filters by groupId", oneGroup.size() == 1);
        check("group history has the right content", containsContent(oneGroup, "group one"));
        check("unknown group has no history", storage.getGroupHistory("missing").size() == 0);

        Message fourth = message("MESSAGE", "chi", "nam", "third", null, "2026-11-22 10:35:00");
        storage.save(fourth);
        check("old messages survive a new save", storage.loadAll().size() == 6);
        check("private history includes the new message",
                storage.getPrivateHistory("chi", "nam").size() == 3);

        storage.save(first);
        check("duplicate message is not saved twice", storage.loadAll().size() == 6);

        MessageStorage reopened = new MessageStorage(new JsonStorage(file));
        check("data survives close and reopen", reopened.loadAll().size() == 6);

        FileInfo image = new FileInfo(PacketType.IMAGE, "pic.png", 123L,
                "media/images/pic.png", "chi", "nam", null, "2026-11-22 10:36:00");
        check("save private media", storage.saveMedia(image));
        Message savedImage = storage.loadAll().get(storage.loadAll().size() - 1);
        check("media filename becomes content", "pic.png".equals(savedImage.getContent()));
        check("media type is stored", "IMAGE".equals(savedImage.getType()));
        check("media filepath is stored", "media/images/pic.png".equals(savedImage.getFilepath()));
        check("media time is stored", "2026-11-22 10:36:00".equals(savedImage.getTime()));

        FileInfo groupFile = new FileInfo(PacketType.FILE, "doc.pdf", 10L,
                "media/files/doc.pdf", "nam", null, "group-1", "2026-11-22 10:37:00");
        storage.saveMedia(groupFile);
        List<Message> groupHistory = storage.getGroupHistory("group-1");
        check("group media stays in its group", groupHistory.size() == 2);
        Message savedFile = groupHistory.get(groupHistory.size() - 1);
        check("group media filepath is stored", "media/files/doc.pdf".equals(savedFile.getFilepath()));
    }

    private static void testUserStorage(File root) throws Exception {
        System.out.println("\n-- UserStorage --");

        File file = new File(root, "users.json");
        UserStorage storage = new UserStorage(new JsonStorage(file));
        check("users file starts empty", storage.loadAll().size() == 0);

        storage.addUser(new User("chi", "pw1", "OFFLINE"));
        storage.addUser(new User("nam", "pw2", "ONLINE"));
        check("two users saved", storage.loadAll().size() == 2);
        check("find user ignoring case", storage.findByUsername("CHI") != null);
        check("duplicate username is rejected", !storage.addUser(new User("chi", "other", "OFFLINE")));

        UserStorage reopened = new UserStorage(new JsonStorage(file));
        check("users survive close and reopen", reopened.loadAll().size() == 2);
    }

    private static void testGroupStorage(File root) throws Exception {
        System.out.println("\n-- GroupStorage --");

        File file = new File(root, "groups.json");
        GroupStorage storage = new GroupStorage(new JsonStorage(file));
        check("groups file starts empty", storage.loadAll().size() == 0);

        Group group = new Group("group-1", "Team", "chi");
        group.addMember("nam");
        group.addMember("lan");
        storage.saveGroup(group);

        check("group saved", storage.loadAll().size() == 1);
        Group loaded = storage.findById("group-1");
        check("group id and name restored", loaded != null && "Team".equals(loaded.getGroupName()));
        check("members restored", loaded != null && loaded.getMemberCount() == 3);
        check("creator is not duplicated", loaded != null && countMember(loaded, "chi") == 1);

        check("existing member is not added twice", !storage.addMember("group-1", "chi"));
        check("new member is added", storage.addMember("group-1", "minh"));
        GroupStorage reopened = new GroupStorage(new JsonStorage(file));
        check("member survives close and reopen",
                reopened.findById("group-1").getMemberCount() == 4);

        check("member is removed", reopened.removeMember("group-1", "lan"));
        Group afterRemoval = new GroupStorage(new JsonStorage(file)).findById("group-1");
        check("removal survives close and reopen",
                afterRemoval.getMemberCount() == 3 && !afterRemoval.isMember("lan"));

        check("missing group cannot be deleted", !reopened.deleteGroup("missing"));
        check("group is deleted", reopened.deleteGroup("group-1"));
        check("no groups left", new GroupStorage(new JsonStorage(file)).loadAll().isEmpty());
    }

    private static void testHistoryManager(File root) throws Exception {
        System.out.println("\n-- HistoryManager --");

        File file = new File(root, "history.json");
        JsonStorage jsonStorage = new JsonStorage(file);
        HistoryManager manager = new HistoryManager(new MessageStorage(jsonStorage));

        check("empty history formats to empty text",
                "".equals(manager.formatHistory(manager.getPrivateHistory("a", "b"))));

        manager.savePrivateMessage("chi", "nam", "hello", "2026-11-22 11:00:00");
        manager.savePrivateMessage("nam", "chi", "hi", "2026-11-22 11:01:00");
        manager.saveGroupMessage("chi", "group-1", "group hi", "2026-11-22 11:02:00");

        check("manager reads both directions",
                manager.getPrivateHistory("chi", "nam").size() == 2);
        check("manager reads group history",
                manager.getGroupHistory("group-1").size() == 1);

        String formatted = manager.formatHistory(manager.getPrivateHistory("chi", "nam"));
        check("formatted history contains sender", formatted.contains("chi"));
        check("formatted history contains time", formatted.contains("2026-11-22 11:00:00"));

        FileInfo media = new FileInfo(PacketType.SOUND, "song.mp3", 50L,
                "media/sounds/song.mp3", "nam", "chi", null, "2026-11-22 11:03:00");
        manager.saveMedia(media);
        String withMedia = manager.formatHistory(manager.getPrivateHistory("chi", "nam"));
        check("media is marked in formatted history",
                withMedia.contains("[Media]") && withMedia.contains("song.mp3"));

        HistoryManager reopened = new HistoryManager(new MessageStorage(new JsonStorage(file)));
        check("history survives close and reopen",
                reopened.getPrivateHistory("chi", "nam").size() == 3
                        && reopened.getGroupHistory("group-1").size() == 1);
    }

    private static void testGroupCreateHelper() {
        System.out.println("\n-- GroupTestUI helper --");

        List<String> members = GroupTestUI.parseMembers("hung, QuocAnh, hung, , chi");
        check("members are trimmed and blank entries skipped", members.size() == 3);
        check("duplicate member is removed", countIgnoreCase(members, "hung") == 1);
        check("creator remains in parsed list for the UI check",
                countIgnoreCase(members, "chi") == 1);
        check("null member text gives empty list", GroupTestUI.parseMembers(null).isEmpty());
    }

    private static int countIgnoreCase(List<String> values, String value) {
        int count = 0;
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                count++;
            }
        }
        return count;
    }

    private static void testGroupCreationFlow(File root) throws Exception {
        System.out.println("\n-- Group creation flow --");

        GroupManager groupManager = new GroupManager();
        Group group = groupManager.createGroup("Test TV4", "chi");
        check("GroupManager created a group id", group != null && group.getGroupId() != null);

        List<String> members = GroupTestUI.parseMembers("hung, QuocAnh, hung");
        for (String member : members) {
            if (!member.equalsIgnoreCase("chi")) {
                groupManager.addMemberToGroup(group.getGroupId(), member);
            }
        }

        File groupsFile = new File(root, "flow-groups.json");
        GroupStorage groupStorage = new GroupStorage(new JsonStorage(groupsFile));
        check("created group is saved", groupStorage.saveGroup(group));

        Group reloaded = groupStorage.findById(group.getGroupId());
        check("creator is added automatically",
                reloaded != null && reloaded.isMember("chi"));
        check("members are restored from groups.json",
                reloaded != null && reloaded.getMemberCount() == 3);

        File messagesFile = new File(root, "flow-messages.json");
        HistoryManager history = new HistoryManager(
                new MessageStorage(new JsonStorage(messagesFile))
        );
        history.saveGroupMessage("chi", group.getGroupId(), "hello group", "2026-11-27 12:00:00");
        history.saveGroupMessage("hung", group.getGroupId(), "reply", "2026-11-27 12:01:00");
        history.savePrivateMessage("chi", "hung", "private note", "2026-11-27 12:02:00");

        check("group history has messages from two senders",
                history.getGroupHistory(group.getGroupId()).size() == 2);
        check("private history is not mixed into group",
                history.getPrivateHistory("chi", "hung").size() == 1);

        HistoryManager reopened = new HistoryManager(
                new MessageStorage(new JsonStorage(messagesFile))
        );
        check("group history survives close and reopen",
                reopened.getGroupHistory(group.getGroupId()).size() == 2);
    }

    private static void testExistingDataFiles() {
        System.out.println("\n-- Existing data files --");

        List<Message> sample = new MessageStorage().loadAll();
        check("existing messages.json sample is readable",
                sample != null && sample.size() >= 1);

        JsonObject status = new JsonStorage("status.json").readObject();
        check("existing status.json object is readable", status != null);
    }

    private static Message message(String type, String sender, String receiver,
                                   String content, String groupId, String time) {
        Message message = new Message(type, sender, receiver, content, groupId);
        message.setTime(time);
        return message;
    }

    private static boolean containsContent(List<Message> messages, String content) {
        for (Message message : messages) {
            if (content.equals(message.getContent())) {
                return true;
            }
        }
        return false;
    }

    private static int countMember(Group group, String username) {
        int count = 0;
        for (String member : group.getMembers()) {
            if (username.equals(member)) {
                count++;
            }
        }
        return count;
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
