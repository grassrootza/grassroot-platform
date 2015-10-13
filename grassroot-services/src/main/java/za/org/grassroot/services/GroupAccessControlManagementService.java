package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.security.acl.Permission;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupAccessControlManagementService {

    /**
     * @param group
     * @param role
     * @return
     */
    Role createGroupRole(Group group, Role role);


    /**
     * @param permission
     * @param group
     * @param user
     * @return
     */
    boolean hasGroupPermission(Permission permission, Group group, User user);
}
