package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by luke on 2016/02/17.
 */
public interface AsyncRoleService {

    void addRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser);

    void addRoleToGroupAndUsers(String roleName, Group group, List<User> addingToUsers, User callingUser);

    void removeUsersRoleInGroup(User user, Group group);

    void resetGroupToDefaultRolesPermissions(Long groupId, GroupPermissionTemplate template, User callingUser);

    /*
    Methods to assign permissions to roles
     */

    void assignPermissionsToGroupRoles(Group group, GroupPermissionTemplate template);
}
