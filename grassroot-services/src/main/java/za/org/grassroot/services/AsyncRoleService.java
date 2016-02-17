package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * Created by luke on 2016/02/17.
 */
public interface AsyncRoleService {

    void addDefaultRoleToGroupAndUser(String roleName, Group group, User user);

    void addDefaultRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser);

    void removeUsersRoleInGroup(User user, Group group);

    void resetGroupToDefaultRolesPermissions(Long groupId);

        /*
    Methods to assign permissions to roles
     */

    Role assignPermissionsToRole(Role role, List<Permission> permissions);

    Role addPermissionToRole(Role role, Permission permission);

    Role removePermissionFromRole(Role role, Permission permission);


}
