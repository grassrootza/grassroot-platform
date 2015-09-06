package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    public Group loadGroup(Long groupId);

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

    public Group getGroupById(Long groupId);

    public Group createSubGroup(User createdByUser, Group group, String subGroupName);

    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName);

    public List<User> getAllUsersInGroupAndSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Group group);
}
