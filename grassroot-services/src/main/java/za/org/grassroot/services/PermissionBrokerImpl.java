package za.org.grassroot.services;

import edu.emory.mathcs.backport.java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionBrokerImpl implements PermissionBroker {

    @Autowired
    GroupRepository groupRepository;

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
        if (requiredPermission == null) {
            return group.getMembers().contains(user);
        } else {
            for (Membership membership : user.getMemberships()) {
                if (membership.getGroup().equals(group)) {
                    return membership.getRole().getPermissions().contains(requiredPermission);
                }
            }
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Group> getActiveGroups(User user, Permission requiredPermission) {
        List<Group> allActiveGroups = groupRepository.findByMembershipsUserAndActive(user, true);
        if (requiredPermission == null)
            return new HashSet<>(allActiveGroups);
        else
            return allActiveGroups.stream().filter(g -> isGroupPermissionAvailable(user, g, requiredPermission)).
                collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<GroupDTO> getActiveGroupDTOs(User user, Permission requiredPermission) {

        List<Object[]> listObjArray =  groupRepository.findActiveUserGroupsOrderedByRecentEvent(user.getId());
        final boolean filterByPermission = (requiredPermission != null);

        // todo: the permission checking version of this defeats the purpose, by getting all the groups anyway, so, rethink
        List<GroupDTO> list = new ArrayList<>();
        for (Object[] objArray : listObjArray) {
            GroupDTO groupDTO = new GroupDTO(objArray);
            if (filterByPermission) {
                for (Membership membership : user.getMemberships()) {
                    if (membership.getGroup().getUid().equals(groupDTO.getUid()) && membership.getRole().getPermissions().contains(requiredPermission))
                        list.add(groupDTO);
                }
            } else {
                list.add(groupDTO);
            }
        }

        return new HashSet<>(list);
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

}
