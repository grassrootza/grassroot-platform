package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

    private static final Logger log = LoggerFactory.getLogger(PermissionBrokerImpl.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RoleRepository roleRepository;

    // major todo: externalize these permissions

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
                    Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION);

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
                    Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK);

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


    private static final Set<Permission> constructPermissionSet(Set<Permission> baseSet, Permission... permissions) {
        Set<Permission> set = new HashSet<>();
        set.addAll(baseSet);
        Collections.addAll(set, permissions);
        return java.util.Collections.unmodifiableSet(set);
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
                log.info("setting permissions for closed group ... looks like = {}", closedGroupOrganizerPermissions.toString());
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
    public Set<Group> getActiveGroupsWithPermission(User user, Permission requiredPermission) {
        Objects.requireNonNull(user, "User cannot be null");
        List<Group> activeGroups = groupRepository.findByMembershipsUserAndActiveTrue(user);
        return activeGroups.stream()
                .filter(group -> requiredPermission == null || isGroupPermissionAvailable(user, group, requiredPermission))
                .collect(Collectors.toSet());
    }

    @Override
    public GroupPage getPageOfGroupDTOs(User user, Permission requiredPermission, int pageNumber, int pageSize) {
        return new GroupPage(getActiveGroupDTOs(user, requiredPermission), pageNumber, pageSize);
    }


    @Override
    @Transactional(readOnly = true)
    public List<GroupDTO> getActiveGroupDTOs(User user, Permission requiredPermission) {

        // we use a list here because the sorting matter
        List<Group> groups = groupRepository.findActiveUserGroupsOrderedByRecentActivity(user.getId());
        final boolean filterByPermission = (requiredPermission != null);

        // todo: the permission checking version of this defeats some of the purpose, by getting all the groups anyway, so, rethink
        List<GroupDTO> list = new ArrayList<>();
        for (Group group : groups) {
            GroupDTO groupDTO = new GroupDTO(group);
            if (filterByPermission) {
                for (Membership membership : user.getMemberships()) {
                    if (membership.getGroup().getUid().equals(groupDTO.getUid()) && membership.getRole().getPermissions().contains(requiredPermission))
                        list.add(groupDTO);
                }
            } else {
                list.add(groupDTO);
            }
        }

        return list;

    }

    @Override
    public Set<Permission> getPermissions(Group group, String roleName) {
        return group.getRole(roleName).getPermissions();
    }

    @Override
    public Set<Permission> getPermissions(User user, Group group) {
        for (Membership membership : user.getMemberships()) {
            if (membership.getGroup().equals(group)) {
                return membership.getRole().getPermissions();
            }
        }
        throw new AccessDeniedException("Error! User " + user + " is not a member of group " + group);
    }

    @Override
    public Set<Permission> getProtectedOrganizerPermissions() {
        return protectedOrganizerPermissions;
    }

    @Override
    public void validateSystemRole(User user, String roleName) {
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

}
