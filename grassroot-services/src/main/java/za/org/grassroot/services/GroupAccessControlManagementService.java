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

    void addUserGroupPermissions(Group group, User addingToUser, User modifyingUser, Set<Permission> groupPermissions);

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

    boolean hasGroupPermission(String permissionName, Group group, User user);

    /**
     *
     * @param groupId
     * @param permission
     * @return
     */
    Group loadGroup(Long groupId, Permission permission);


    /**
     *
     * @param groupId
     * @param permissionName
     * @return
     */
    Group loadGroup(Long groupId, String permissionName);

}
