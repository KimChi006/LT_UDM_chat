package group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupManager {

    private final List<Group> groups;

    public GroupManager() {
        groups = new ArrayList<>();
    }

    // Create a new group
    public Group createGroup(String groupName, String creator) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return null;
        }

        if (creator == null || creator.trim().isEmpty()) {
            return null;
        }

        String groupId = UUID.randomUUID().toString();

        Group group = new Group(
                groupId,
                groupName.trim(),
                creator.trim()
        );

        groups.add(group);
        return group;
    }

    // Find a group by ID
    public Group findGroupById(String groupId) {
        if (groupId == null) {
            return null;
        }

        for (Group group : groups) {
            if (group.getGroupId().equals(groupId)) {
                return group;
            }
        }

        return null;
    }

    // Add a member to a group
    public boolean addMemberToGroup(String groupId, String username) {
        Group group = findGroupById(groupId);

        if (group == null) {
            return false;
        }

        return group.addMember(username);
    }

    // Remove a member from a group
    public boolean removeMemberFromGroup(String groupId, String username) {
        Group group = findGroupById(groupId);

        if (group == null) {
            return false;
        }

        return group.removeMember(username);
    }

    // Get all groups belonging to a user
    public List<Group> getGroupsForMember(String username) {
        List<Group> result = new ArrayList<>();

        if (username == null) {
            return result;
        }

        for (Group group : groups) {
            if (group.isMember(username)) {
                result.add(group);
            }
        }

        return result;
    }

    // Get all groups
    public List<Group> getAllGroups() {
        return new ArrayList<>(groups);
    }

    // Delete a group
    public boolean deleteGroup(String groupId) {
        Group group = findGroupById(groupId);

        if (group == null) {
            return false;
        }

        return groups.remove(group);
    }

    // Get total number of groups
    public int getGroupCount() {
        return groups.size();
    }
}