package za.org.grassroot.services;


import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.Set;


/**
 * @author Lesetse Kimwaga
 */
public interface GroupAccessControlManagementService {


    /**
     * @param group
     * @param user
     * @param groupPermissions
     */
    void addUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions);

    /**
     * @param group
     * @param user
     * @param groupPermissions
     */
    void removeUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions);


    /**
     * @param permission
     * @param group
     * @param user
     * @return
     */
    boolean hasGroupPermission(Permission permission, Group group, User user);

    /**
     *
     * @param group
     * @param user
     * @param groupPermissions
     */
    void updateUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions);

}
