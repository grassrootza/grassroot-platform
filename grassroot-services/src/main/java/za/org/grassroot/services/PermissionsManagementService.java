package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface PermissionsManagementService {

    Set<Permission> getPermissions(String roleName, GroupPermissionTemplate template);

    void setRolePermissionsFromTemplate(Group group, GroupPermissionTemplate template);

}
