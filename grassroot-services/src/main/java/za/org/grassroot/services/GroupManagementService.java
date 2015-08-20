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

    Group createNewGroup(User creatingUser, String phoneNumbers);

    Group getLastCreatedGroup(User creatingUser);

    List<Group> getCreatedGroups(User creatingUser);

}
