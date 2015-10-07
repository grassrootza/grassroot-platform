package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    public Group loadGroup(Long groupId);

    public List<Group> getGroupsFromUser(User sessionUser);

    public boolean isUserInGroup(Group group, User user);

    public Group saveGroup(Group groupToSave);

    public void deleteGroup(Group groupToDelete);

    public Group addGroupMember(Group currentGroup, User newMember);

    public Group addGroupMember(Long currentGroupId, Long newMemberId);

    public Group removeGroupMember(Group group, User user);

    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList);

    public Group createNewGroup(User creatingUser, List<String> phoneNumbers);

    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers);

    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers);

    public Group getLastCreatedGroup(User creatingUser);

    public boolean needsToRenameGroup(User sessionUser);

    public Long groupToRename(User sessionUser);

    public List<Group> getCreatedGroups(User creatingUser);

    public List<Group> getGroupsPartOf(User sessionUser);

    public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize);

    public List<Group> getSubGroups(Group group);

    public Group getGroupById(Long groupId);

    public Group getGroupByToken(String groupToken);

    public Group generateGroupToken(Group group, Integer daysValid);

    public Group generateGroupToken(Long groupId, Integer daysValid);

    public Group extendGroupToken(Group group, Integer daysExtension);

    public Group invalidateGroupToken(Group group);

    public Group invalidateGroupToken(Long groupId);

    public boolean groupHasValidToken(Group group);

    public boolean tokenExists(String groupToken);

    public Group createSubGroup(User createdByUser, Group group, String subGroupName);

    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName);

    public List<User> getAllUsersInGroupAndSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Group group);

    List<Group> getAllParentGroups(Group group);

    /*
    Pass in the group you want to make a child as the 'possibleChildGroup', and the desired parent
    as the 'possibleParentGroup', and this will return true if the possible child is already in the parent chain
     of the possible parent, i.e., if it will create an infinite loop
     */
    boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup);

    public boolean canUserDeleteGroup(User user, Group group);

}
