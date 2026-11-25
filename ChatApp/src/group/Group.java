package group;

import java.util.ArrayList;
import java.util.List;

public class Group {

    private String groupId;
    private String groupName;
    private String creator;
    private List<String> members;

    public Group(String groupId, String groupName, String creator) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.creator = creator;
        this.members = new ArrayList<>();

        if (creator != null && !creator.isEmpty()) {
            members.add(creator);
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getCreator() {
        return creator;
    }

    public List<String> getMembers() {
        return new ArrayList<>(members);
    }

    public boolean addMember(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        username = username.trim();

        if (members.contains(username)) {
            return false;
        }

        members.add(username);
        return true;
    }

    public boolean removeMember(String username) {
        if (username == null) {
            return false;
        }

        if (username.equals(creator)) {
            return false;
        }

        return members.remove(username);
    }

    public boolean isMember(String username) {
        return members.contains(username);
    }

    public int getMemberCount() {
        return members.size();
    }
}