package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;

public interface PermissionBroker {
    void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission);

    boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission);
}
