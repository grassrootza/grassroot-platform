package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.List;

/**
 * Created by luke on 2016/02/17.
 */
public interface AsyncRoleService {

    void addRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser);

    void resetGroupToDefaultRolesPermissions(Long groupId, GroupPermissionTemplate template, User callingUser);
}
