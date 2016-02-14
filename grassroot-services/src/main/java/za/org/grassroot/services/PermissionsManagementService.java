package za.org.grassroot.services;

import za.org.grassroot.core.domain.Permission;

import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface PermissionsManagementService {

    /*
    Usual CRUD and find methods for permissions
     */

    Permission createPermission(Permission permission);

    Permission updatePermission(Permission permission);

    Permission getPermission(Long permissionId);

    void  deletePermission(Permission permission);

    List<Permission> getPermissions();

    Permission findByName(String name);

    Set<Permission> findByNames(List<String> permissionNames);

    /*
    Default permission structures for key roles
     */

    Set<Permission> defaultGroupOrganizerPermissions();

    Set<Permission> defaultCommitteeMemberPermissions();

    Set<Permission> defaultOrdinaryMemberPermissions();

    Set<Permission> defaultPermissionsGroupRole(String roleName);

    /*
    default permissions for a "closed", i.e., one-way group, where only organizer & committee member can call things
    major todo: externalize these permission structures
     */

    Set<Permission> closedGroupOrganizerPermissions();

    Set<Permission> closedGroupCommitteeMemberPermissions();

    Set<Permission> closedGroupOrdinaryMemberPermissions();

    Set<Permission> closedPermissionsGroupRole(String roleName);

}
