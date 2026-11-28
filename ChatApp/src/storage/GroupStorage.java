package storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import group.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes data/groups.json.
 *
 * Group objects are rebuilt with the constructor that already exists in
 * group.Group, then members are added through Group.addMember(). This keeps
 * the TV2 model unchanged.
 */
public class GroupStorage {

    private final JsonStorage storage;

    public GroupStorage() {
        this(new JsonStorage("groups.json"));
    }

    /** Used by tests with a temporary file. */
    public GroupStorage(JsonStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null");
        }
        this.storage = storage;
    }

    /**
     * @return all groups, or null when JSON is invalid.
     */
    public List<Group> loadAll() {
        JsonArray array = storage.readArray();
        if (array == null) {
            return null;
        }

        List<Group> groups = new ArrayList<Group>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String groupId = readString(object, "groupId");
            if (groupId == null || groupId.trim().isEmpty()) {
                continue;
            }

            String groupName = readString(object, "groupName");
            String creator = readString(object, "creator");
            Group group = new Group(groupId, groupName, creator);

            JsonElement members = object.get("members");
            if (members != null && members.isJsonArray()) {
                for (JsonElement memberElement : members.getAsJsonArray()) {
                    if (memberElement.isJsonPrimitive()) {
                        group.addMember(memberElement.getAsString());
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    public boolean saveAll(List<Group> groups) {
        if (groups == null) {
            return false;
        }

        JsonArray array = new JsonArray();
        for (Group group : groups) {
            if (group == null || group.getGroupId() == null) {
                continue;
            }

            JsonObject object = new JsonObject();
            object.addProperty("groupId", group.getGroupId());
            object.addProperty("groupName", group.getGroupName() == null ? "" : group.getGroupName());
            object.addProperty("creator", group.getCreator() == null ? "" : group.getCreator());

            JsonArray members = new JsonArray();
            for (String member : group.getMembers()) {
                members.add(member);
            }
            object.add("members", members);
            array.add(object);
        }
        return storage.writeArray(array);
    }

    /** Inserts a new group or replaces an existing one with the same id. */
    public boolean saveGroup(Group group) {
        if (group == null || group.getGroupId() == null) {
            return false;
        }

        List<Group> groups = loadAll();
        if (groups == null) {
            return false;
        }

        int index = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (group.getGroupId().equals(groups.get(i).getGroupId())) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            groups.add(group);
        } else {
            groups.set(index, group);
        }
        return saveAll(groups);
    }

    public Group findById(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return null;
        }

        List<Group> groups = loadAll();
        if (groups == null) {
            return null;
        }

        for (Group group : groups) {
            if (groupId.equals(group.getGroupId())) {
                return group;
            }
        }
        return null;
    }

    public boolean addMember(String groupId, String username) {
        Group group = findById(groupId);
        if (group == null || !group.addMember(username)) {
            return false;
        }
        return saveGroup(group);
    }

    public boolean removeMember(String groupId, String username) {
        Group group = findById(groupId);
        if (group == null || !group.removeMember(username)) {
            return false;
        }
        return saveGroup(group);
    }

    public boolean deleteGroup(String groupId) {
        List<Group> groups = loadAll();
        if (groups == null) {
            return false;
        }

        boolean removed = false;
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groupId != null && groupId.equals(groups.get(i).getGroupId())) {
                groups.remove(i);
                removed = true;
            }
        }
        return removed && saveAll(groups);
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }
}
