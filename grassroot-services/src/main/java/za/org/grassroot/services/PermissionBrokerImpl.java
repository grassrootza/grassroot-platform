package za.org.grassroot.services;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;

import java.util.Set;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

    public void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission) {
        if (!isGroupPermissionAvailable(user, targetGroup, requiredPermission)) {
            throw new AccessDeniedException("User " + user + " has no permission " + requiredPermission + " available for group " + targetGroup);
        }
    }

    public boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission) {
        for (Membership membership : user.getMemberships()) {
            if (membership.getGroup().equals(group)) {
                return membership.getRole().getPermissions().contains(requiredPermission);
            }
        }
        return false;
    }

    @Override
    public Set<Permission> getPermissions(Group group, String roleName) {
        return group.getRole(roleName).getPermissions();
    }

}
