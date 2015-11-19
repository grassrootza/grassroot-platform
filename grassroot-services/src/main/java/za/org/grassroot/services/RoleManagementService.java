package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleManagementService {

    /*
    Methods to deal with roles in themselves
     */

    Role createRole(Role role);

    Role createStandardRole(String roleName);

    Role getRole(Long roleId);

    Role updateRole(Role role);

    void deleteRole(Role role);

    List<Role> getAllRoles();

    /*
    Methods to fetch standard and group roles
     */

    Role fetchStandardRoleByName(String name);

    Role fetchGroupRoleByName(String name);

    List<Role> fetchGroupRoles(Long groupId);

    Role fetchGroupRole(String roleName, Long groupId);

    Role fetchGroupRole(String roleName, Group group);

    Integer getNumberStandardRoles();

    /*
    Methods to assign roles to users and groups
     */

    User addStandardRoleToUser(Role role, User user);

    User addStandardRoleToUser(String roleName, User user);

    User removeStandardRoleFromUser(Role role, User user);

    User removeStandardRoleFromUser(String roleName, User user);

    Role addRoleToGroup(Role role, Group group);

    Role addRoleToGroup(String roleName, Group group);

    void addDefaultRoleToGroupAndUser(String roleName, Group group, User user);

    /*
    Methods to assign permissions to roles
     */

    Role assignPermissionsToRole(Role role, List<Permission> permissions);

    Role addPermissionToRole(Role role, Permission permission);

    Role removePermissionFromRole(Role role, Permission permission);

    /*
    Deprecated:

    Role createGroupRole(String roleName, Long groupId, String groupName);
     */

}
