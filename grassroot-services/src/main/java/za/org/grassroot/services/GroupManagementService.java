package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    Group loadGroup(Long groupId);

    Group saveGroup(Group groupToSave);

    void deleteGroup(Group groupToDelete);

    Group addGroupMember(Group currentGroup, User newMember);

    Group createNewGroup(User creatingUser, List<String> phoneNumbers);

    Group createNewGroup(Long creatingUserId, List<String> phoneNumbers);

    Group addNumbersToGroup(Long groupId, List<String> phoneNumbers);

    Group getLastCreatedGroup(User creatingUser);

    boolean needsToRenameGroup(User sessionUser);

    Long groupToRename(User sessionUser);

    List<Group> getCreatedGroups(User creatingUser);

    Group getGroupById(Long groupId);
}
