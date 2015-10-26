package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleManagementService {

    Role createRole(Role role);

    Role getRole(Long roleId);

    Role updateRole(Role role);

    void deleteRole(Role role);

    List<Role> getAllRoles();

    Role fetchStandardRoleByName(String name);

    List<Role> fetchGroupRoles(Long groupId);

    Role fetchGroupRole(String roleName, Long groupId);

    Role createGroupRole(String roleName, Long groupId, String groupName);

}
