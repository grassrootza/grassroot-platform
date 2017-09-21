package za.org.grassroot.services;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.services.group.GroupPermissionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

    private static final Logger log = LoggerFactory.getLogger(PermissionBrokerImpl.class);

    private final GroupRepository groupRepository;

    private final RoleRepository roleRepository;

    private final EntityManager entityManager;

    private static final Set<Permission> defaultOrdinaryMemberPermissions =
            constructPermissionSet(Collections.emptySet(),
                    Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
                    Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
                    Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
                    Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS,
                    Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
                    Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
                    Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK);

    private static final Set<Permission> defaultCommitteeMemberPermissions =
            constructPermissionSet(defaultOrdinaryMemberPermissions,
                    Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                    Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                    Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
                    Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
                    Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION,
                    Permission.GROUP_PERMISSION_MUTE_MEMBER);

    private static final Set<Permission> defaultGroupOrganizerPermissions =
            constructPermissionSet(defaultCommitteeMemberPermissions,
                    Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                    Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
                    Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
                    Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                    Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                    Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
                    Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER,
                    Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
                    Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE);

    // closed group structure ... again, externalize
    private static final Set<Permission> closedOrdinaryMemberPermissions =
            constructPermissionSet(Collections.emptySet(),
                    Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS);

    private static final Set<Permission> closedCommitteeMemberPermissions =
            constructPermissionSet(defaultOrdinaryMemberPermissions,
                    Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
                    Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
                    Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
                    Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
                    Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
                    Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK,
                    Permission.GROUP_PERMISSION_MUTE_MEMBER);

    private static final Set<Permission> closedGroupOrganizerPermissions =
            constructPermissionSet(defaultCommitteeMemberPermissions,
                    Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                    Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                    Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
                    Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
                    Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION,
                    Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
                    Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                    Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                    Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
                    Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER,
                    Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
                    Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE);

    // a couple of permissions that we don't let users remove from organizers (since then no one can change them)
    private static final Set<Permission> protectedOrganizerPermissions =
            constructPermissionSet(Collections.emptySet(),
                    Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                    Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
                    Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE);

    @Autowired
    public PermissionBrokerImpl(GroupRepository groupRepository, RoleRepository roleRepository, EntityManager entityManager) {
        this.groupRepository = groupRepository;
        this.roleRepository = roleRepository;
        this.entityManager = entityManager;
    }


    private static Set<Permission> constructPermissionSet(Set<Permission> baseSet, Permission... permissions) {
        Set<Permission> set = new HashSet<>();
        set.addAll(baseSet);
        Collections.addAll(set, permissions);
        return java.util.Collections.unmodifiableSet(set);
    }

    private Query fetchGroupsWithPermission(User user, Permission permission) {
        return entityManager.createNativeQuery("select group_profile.*, greatest(latest_group_change, latest_event, latest_todo) as latest_activity from group_profile " +
                "inner join group_user_membership as membership on (group_profile.id = membership.group_id and group_profile.active = true and membership.user_id = :user) " +
                "left outer join (select group_id, max(created_date_time) as latest_group_change from group_log group by group_id) as group_log on (group_log.group_id = group_profile.id) " +
                "left outer join (select parent_group_id, max(created_date_time) as latest_event from event group by parent_group_id) as event on (event.parent_group_id = group_profile.id) " +
                "left outer join (select parent_group_id, max(created_date_time) as latest_todo from action_todo group by parent_group_id) as todo on (todo.parent_group_id = group_profile.id) " +
                "where group_profile.active = true " +
                "and :permission in (select permission from role_permissions where role_id = membership.role_id) " +
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

    @Override
    public void setRolePermissionsFromTemplate(Group group, GroupPermissionTemplate template) {
        Role organizer = group.getRole(BaseRoles.ROLE_GROUP_ORGANIZER);
        Role committee = group.getRole(BaseRoles.ROLE_COMMITTEE_MEMBER);
        Role member = group.getRole(BaseRoles.ROLE_ORDINARY_MEMBER);

        switch (template) {
            case DEFAULT_GROUP:
                organizer.setPermissions(defaultGroupOrganizerPermissions);
                committee.setPermissions(defaultCommitteeMemberPermissions);
                member.setPermissions(defaultOrdinaryMemberPermissions);
                break;
            case CLOSED_GROUP:
                log.trace("setting permissions for closed group ... looks like = {}", closedGroupOrganizerPermissions.toString());
                organizer.setPermissions(closedGroupOrganizerPermissions);
                committee.setPermissions(closedCommitteeMemberPermissions);
                member.setPermissions(closedOrdinaryMemberPermissions);
                break;
            default:
                organizer.setPermissions(defaultGroupOrganizerPermissions);
                committee.setPermissions(defaultCommitteeMemberPermissions);
                member.setPermissions(defaultOrdinaryMemberPermissions);
                break;
        }
    }

    public void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission) {
        if (!isGroupPermissionAvailable(user, targetGroup, requiredPermission)) {
            throw new AccessDeniedException("User " + user + " has no permission " + requiredPermission + " available for group " + targetGroup);
        }
    }

    public boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission) {
        return user.getMemberships().stream().anyMatch(membership ->
                membership.getGroup().equals(group) &&
                        (requiredPermission == null || membership.getRole().getPermissions().contains(requiredPermission)));
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Set<Group> getActiveGroupsWithPermission(User user, Permission requiredPermission) {
        Objects.requireNonNull(user, "User cannot be null");
        Query resultQuery = requiredPermission == null ? fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        List<Group> activeGroups = resultQuery.getResultList();
        return activeGroups == null ? new HashSet<>() : new HashSet<>(activeGroups);
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Group> getPageOfGroups(User user, Permission requiredPermission, int pageNumber, int pageSize) {
        Query resultQuery = requiredPermission == null ? fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        return resultQuery
                .setFirstResult((pageNumber) * pageSize)
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
            return groupRepository.countActiveGroupsWhereUserHasPermission(user, requiredPermission);
        }
    }


    @Override
    @Transactional(readOnly = true)
    @Timed
    @SuppressWarnings("unchecked")
    public List<Group> getActiveGroupsSorted(User user, Permission requiredPermission) {
        Query resultQuery = requiredPermission == null ?
                fetchAllGroupsSortedForUser(user) : fetchGroupsWithPermission(user, requiredPermission);
        return resultQuery.getResultList();
    }

    @Override
    public Set<Permission> getProtectedOrganizerPermissions() {
        return protectedOrganizerPermissions;
    }

    @Override
    public void validateSystemRole(User user, String roleName) {
        log.info("attempting to validate system role, with name: " + roleName);
        List<Role> systemRoles = roleRepository.findByNameAndRoleType(roleName, Role.RoleType.STANDARD);
        if (systemRoles == null || systemRoles.isEmpty()) {
            throw new UnsupportedOperationException("Error! Attempt to check invalid role");
        }
        for (Role role : systemRoles) {
            if (!user.getStandardRoles().contains(role)) {
                throw new AccessDeniedException("Error! User " + user.getDisplayName() + " does not have the role " + roleName);
            }
        }
    }

    @Override
    @Transactional
    public void addSystemRole(User user, String roleName) {
        Role systemRole = roleRepository.findByNameAndRoleType(roleName, Role.RoleType.STANDARD).get(0);
        user.addStandardRole(systemRole);
    }

    @Override
    @Transactional
    public void removeSystemRole(User user, String roleName) {
        Role systemRole = roleRepository.findByNameAndRoleType(roleName, Role.RoleType.STANDARD).get(0);
        user.removeStandardRole(systemRole);
    }

}
