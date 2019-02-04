package za.org.grassroot.services;

import org.springframework.security.access.AccessDeniedException;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;

import java.util.List;
import java.util.Set;

public interface PermissionBroker {

    /**
     * Checks if a user has a required permission on a group and throws an error if the user does not
     * @param user The user for whom we are checking permission
     * @param targetGroup The group on which we are checking permission
     * @param requiredPermission The permission (from the Permission enum) to check. If this is null, the method just
     *                           checks that the user is a member of this group
     * @throws AccessDeniedException
     */
    void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission);

    /**
     * Similar to validateGroupPermission, but returns true or false instead of throwing an error
     * @param user The user to check
     * @param group The group that the user is supposed ot be a member of
     * @param requiredPermission The permission to check (passing null checks for group membership)
     * @return true if the permission is available, false if not
     */
    boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission);

    /**
     * Return the set of groups on which the user has a requisite permission, and which have not been deactivated.
     * @param user The user for whom the list of groups is required
     * @param requiredPermission The permission being checked. Passing null returns all active groups the user is in.
     * @return The requisite set of groups
     */
    Set<Group> getActiveGroupsWithPermission(User user, Permission requiredPermission);

    /**
     * Returns the set of active groups for which the user has the required permission. Returns the GroupDTO entity,
     * which contains less internal information than the Group entity, but includes last event and modification timestamps.
     * @param user The user for whom the set of groups is required
     * @param requiredPermission The permission being checked. Passing null returns all active groups.
     * @return The list of GroupDTO entities for the user, sorted by recent activity.
     */
    List<Group> getActiveGroupsSorted(User user, Permission requiredPermission);

    /**
     * Return a list of groups for which the user has a requisite permission, which have not been deactivated, and which
     * are filtered by a given permission.
     * @param user The user for whom the page of groups is required
     * @param requiredPermission The permission by which to filter
     * @param pageNumber The page number
     * @param pageSize The page size
     * @return A page of groups
     */
    List<Group> getPageOfGroups(User user, Permission requiredPermission, int pageNumber, int pageSize);

    int countActiveGroupsWithPermission(User user, Permission requiredPermission);

    boolean isSystemAdmin(User user);

    void validateSystemRole(User user, StandardRole roleName);

    void addSystemRole(User user, StandardRole role);

    void removeSystemRole(User user, StandardRole role);

}
