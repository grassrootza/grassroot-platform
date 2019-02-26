package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.repository.GroupRepository;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

    private static final Logger log = LoggerFactory.getLogger(PermissionBrokerImpl.class);

    private final GroupRepository groupRepository;

    private final EntityManager entityManager;

    @Autowired
    public PermissionBrokerImpl(GroupRepository groupRepository, EntityManager entityManager) {
        this.groupRepository = groupRepository;
        this.entityManager = entityManager;
    }


    private Query fetchGroupsWithPermission(User user, Permission permission) {
        return entityManager.createNativeQuery("select group_profile.*, greatest(last_task_creation_time, last_log_creation_time) as latest_activity from group_profile " +
                "inner join group_user_membership as membership on (group_profile.id = membership.group_id and group_profile.active = true and membership.user_id = :user) " +
                "where group_profile.active = true " +
                "and :permission in (select permission from group_role_permission where role = membership.role and group_id = group_profile.id) " +
                "order by latest_activity desc", Group.class)
                .setParameter("user", user.getId())
                .setParameter("permission", permission.getName());
    }

    private Query fetchAllGroupsSortedForUser(User user) {
        log.info("about to execute select query ...");
        return entityManager.createNativeQuery("SELECT group_profile.*, greatest(latest_group_change, latest_event, latest_todo) as latest_activity from group_profile " +
                "inner join group_user_membership as membership on (group_profile.id = membership.group_id and group_profile.active=true and membership.user_id= :user) " +
                "left outer join (select group_id, max(created_date_time) as latest_group_change from group_log group by group_id) as group_log on (group_log.group_id=group_profile.id) " +
                "left outer join (select parent_group_id, max(created_date_time) as latest_event from event group by parent_group_id) as event on (event.parent_group_id=group_profile.id) " +
                "left outer join (select parent_group_id, max(created_date_time) as latest_todo from action_todo group by parent_group_id) as todo on (todo.parent_group_id = group_profile.id) " +
                "order by latest_activity desc", Group.class)
                .setParameter("user", user.getId());
    }

    public void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission) {
        if (!isGroupPermissionAvailable(user, targetGroup, requiredPermission)) {
            throw new AccessDeniedException("User " + user + " has no permission " + requiredPermission + " available for group " + targetGroup);
        }
    }

    public boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission) {
        final Optional<Membership> membershipOptional = user.getMembershipOptional(group);
        return membershipOptional.isPresent() && (requiredPermission == null || membershipOptional.get().getRolePermissions().contains(requiredPermission));
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Set<Group> getActiveGroupsWithPermission(User user, Permission requiredPermission) {
        Objects.requireNonNull(user, "User cannot be null");
        Query resultQuery = requiredPermission == null ? fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        List<Group> activeGroups = resultQuery.getResultList();
        log.info("Found {} active groups in query", activeGroups == null ? "none" : activeGroups.size());
        return activeGroups == null ? new HashSet<>() : new HashSet<>(activeGroups);
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Group> getPageOfGroups(User user, Permission requiredPermission, int pageNumber, int pageSize) {
        Query query = requiredPermission == null ? fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        return query.setFirstResult((pageNumber) * pageSize)
                    .setMaxResults(pageSize)
                    .getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveGroupsWithPermission(User user, Permission requiredPermission) {
        if (requiredPermission == null) {
            return groupRepository.countByMembershipsUserAndActiveTrue(user);
        } else {
            // using entity manager to create query on this runs into some lazy initialization issues on role, and repo can handle in hql, hence
            return groupRepository.countActiveGroupsWhereMemberHasPermission(user, requiredPermission);
        }
    }


    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Group> getActiveGroupsSorted(User user, Permission requiredPermission) {
        Query resultQuery = requiredPermission == null ?
                fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        return resultQuery.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSystemAdmin(User user) {
        return user.getStandardRoles().contains(StandardRole.ROLE_SYSTEM_ADMIN);
    }

    @Override
    public void validateSystemRole(User user, StandardRole role) {
        log.info("attempting to validate system role, with name: " + role);
        if (!user.getStandardRoles().contains(role)) {
            throw new AccessDeniedException("Error! User " + user.getDisplayName() + " does not have the role " + role);
        }
        log.debug("user has requisite role, returning");
    }

    @Override
    @Transactional
    public void addSystemRole(User user, StandardRole role) {
        user.addStandardRole(role);
    }

    @Override
    @Transactional
    public void removeSystemRole(User user, StandardRole role) {
        user.removeStandardRole(role);
    }

}
