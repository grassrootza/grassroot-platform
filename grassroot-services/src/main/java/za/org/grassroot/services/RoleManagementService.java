package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
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

    Integer getNumberStandardRoles();

    /*
    Methods to assign roles to users and groups
     */

    User addStandardRoleToUser(Role role, User user);

    User addStandardRoleToUser(String roleName, User user);

    User removeStandardRoleFromUser(Role role, User user);

    User removeStandardRoleFromUser(String roleName, User user);

    Group addRoleToGroup(Role role, Group group);

    Group addRoleToGroup(String roleName, Group group);



    /*
    Deprecated:

    Role createGroupRole(String roleName, Long groupId, String groupName);
     */

}
