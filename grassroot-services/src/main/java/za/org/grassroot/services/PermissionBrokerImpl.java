package za.org.grassroot.services;

import edu.emory.mathcs.backport.java.util.Collections;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.HashSet;
import java.util.Set;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

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
                                   Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER);

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
                                   Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER);


    private static final Set<Permission> constructPermissionSet(Set<Permission> baseSet, Permission... permissions) {
        Set<Permission> set = new HashSet<>();
        set.addAll(baseSet);
        for (Permission permission : permissions) {
            set.add(permission);
        }
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

    // NOTE: parking some old code here, may reuse in future if we create similar methods

    /*    private Role fixAndReturnGroupRole(String roleName, Group group, GroupPermissionTemplate template) {
        group.setGroupRoles(roleManagementService.createGroupRoles(group.getUid()));
        groupRepository.saveAndFlush(group);
        return fetchGroupRole(roleName, group.getUid());
    }

    private Role fixPermissionsForRole(Role role, GroupPermissionTemplate template) {
        log.error("Uh oh, for some reason the role permissions weren't set previously");
        role.setPermissions(permissionsManagementService.getPermissions(role.getName(), template));
        return roleRepository.save(role);
    }

    @Async
    @Override
    public void resetGroupToDefaultRolesPermissions(Long groupId, GroupPermissionTemplate template, User callingUser) {

        log.info("Resetting group to creator as organizer, rest as members ... ");
        Long startTime = System.currentTimeMillis();
        Group group = groupRepository.findOne(groupId);
        Long creatingUserId = group.getCreatedByUser().getId();

        List<User> groupMembers = userRepository.findByGroupsPartOfAndIdNot(group, creatingUserId);

        Role ordinaryRole = fetchGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, group.getUid());
        if (ordinaryRole == null) { ordinaryRole = fixAndReturnGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, group, template); }

        if (ordinaryRole.getPermissions() == null || ordinaryRole.getPermissions().isEmpty())
            ordinaryRole = fixPermissionsForRole(ordinaryRole, template);

        addRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, group.getCreatedByUser(), callingUser);
        addRoleToGroupAndUsers(BaseRoles.ROLE_ORDINARY_MEMBER, group, groupMembers, callingUser);

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Added roles to members, total time took %d msecs", endTime - startTime));
        log.info("Exiting the resetGroupToDefault method ...");
    }*/

}
