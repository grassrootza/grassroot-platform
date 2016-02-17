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
    Methods to fetch standard roles and create and fetch group roles
     */

    Role fetchStandardRoleByName(String name);

    Role fetchGroupRoleByName(String name);

    List<Role> createGroupRoles(Long groupId, String groupName);

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

    User removeGroupRolesFromUser(User user, Group group);

    /*
    Methods to retrieve roles for user in group
     */

    Role getUserRoleInGroup(User user, Group group);

    boolean doesUserHaveRoleInGroup(User user, Group group, String roleName);

}
