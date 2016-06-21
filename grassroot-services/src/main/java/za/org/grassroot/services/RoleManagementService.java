package za.org.grassroot.services;

import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleManagementService {

    /*
	Methods to deal with roles in themselves
     */

	Role createStandardRole(String roleName);

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
}
