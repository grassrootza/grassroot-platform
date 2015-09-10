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

    public Group saveGroup(Group groupToSave);

    public void deleteGroup(Group groupToDelete);

    public Group addGroupMember(Group currentGroup, User newMember);

    public Group addGroupMember(Long currentGroupId, Long newMemberId);

    public Group createNewGroup(User creatingUser, List<String> phoneNumbers);

    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers);

    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers);

    public Group getLastCreatedGroup(User creatingUser);

    public boolean needsToRenameGroup(User sessionUser);

    public Long groupToRename(User sessionUser);

    public List<Group> getCreatedGroups(User creatingUser);

    public List<Group> getGroupsPartOf(User sessionUser);

    public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize);

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
}
