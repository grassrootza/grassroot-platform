package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleManagementService {

    /*
    Methods to deal with roles in themselves
     */

    Role createStandardRole(String roleName);

    Role getRole(Long roleId);

    Role updateRole(Role role);

    void deleteRole(Role role);

    /*
    Methods to fetch standard roles and create and fetch group roles
     */

    Role fetchStandardRoleByName(String name);

    Integer getNumberStandardRoles();

    /*
    Methods to assign roles to users and groups
     */

    User addStandardRoleToUser(Role role, User user);

    User addStandardRoleToUser(String roleName, User user);

    User removeStandardRoleFromUser(Role role, User user);

    User removeStandardRoleFromUser(String roleName, User user);

}
