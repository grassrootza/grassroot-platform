package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.Set;

public interface PermissionBroker {

    void setRolePermissionsFromTemplate(Group group, GroupPermissionTemplate template);

    void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission);

    boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission);

    Set<Permission> getPermissions(Group group, String roleName);

    Set<Permission> getProtectedOrganizerPermissions();

}
