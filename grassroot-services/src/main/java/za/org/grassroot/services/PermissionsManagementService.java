package za.org.grassroot.services;

import za.org.grassroot.core.domain.Permission;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface PermissionsManagementService {

    Permission createPermission(Permission permission);

    Permission updatePermission(Permission permission);

    Permission getPermission(Long permissionId);

    void  deletePermission(Permission permission);

    List<Permission> getPermissions();

    Permission findByName(String name);

}
