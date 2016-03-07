package za.org.grassroot.services;

import edu.emory.mathcs.backport.java.util.Collections;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class PermissionsManager implements PermissionsManagementService {

    // major todo: externalize these permissions

    private static final Set<Permission> defaultOrdinaryMemberPermissions = constructPermissionSet(Collections.emptySet(),
            Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
            Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS,
            Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
            Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
            Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK
    );

    private static final Set<Permission> defaultCommitteeMemberPermissions = constructPermissionSet(defaultOrdinaryMemberPermissions,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
            Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
            Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION
    );

    private static final Set<Permission> defaultGroupOrganizerPermissions = constructPermissionSet(defaultCommitteeMemberPermissions,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
            Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
            Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
            Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER
    );

    // closed group structure ... again, externalize
    private static final Set<Permission> closedOrdinaryMemberPermissions = constructPermissionSet(Collections.emptySet(),
            Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS
    );

    private static final Set<Permission> closedCommitteeMemberPermissions = constructPermissionSet(defaultOrdinaryMemberPermissions,
            Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
            Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
            Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
            Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK
    );

    private static final Set<Permission> closedGroupOrganizerPermissions = constructPermissionSet(defaultCommitteeMemberPermissions,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
            Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
            Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION,
            Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
            Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
            Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER
    );


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

    private Set<Permission> defaultPermissionsGroupRole(String roleName) {
        // todo: error handling if passed a strange or null role; for now, return ordinary member
        switch (roleName) {
            case BaseRoles.ROLE_GROUP_ORGANIZER:
                return defaultGroupOrganizerPermissions;
            case BaseRoles.ROLE_COMMITTEE_MEMBER:
                return defaultCommitteeMemberPermissions;
            case BaseRoles.ROLE_ORDINARY_MEMBER:
                return defaultOrdinaryMemberPermissions;
            default:
                return defaultOrdinaryMemberPermissions;
        }
    }

    private Set<Permission> closedPermissionsGroupRole(String roleName) {
        switch (roleName) {
            case BaseRoles.ROLE_GROUP_ORGANIZER:
                return closedGroupOrganizerPermissions;
            case BaseRoles.ROLE_COMMITTEE_MEMBER:
                return closedCommitteeMemberPermissions;
            case BaseRoles.ROLE_ORDINARY_MEMBER:
                return closedOrdinaryMemberPermissions;
            default:
                return closedOrdinaryMemberPermissions;
        }
    }

    @Override
    public Set<Permission> getPermissions(String roleName, GroupPermissionTemplate template) {
        // todo: so this is basically iterating over two enums in a way that isn't great, this all needs a refactor prob
        switch (template) {
            case DEFAULT_GROUP:
                return defaultPermissionsGroupRole(roleName);
            case CLOSED_GROUP:
                return closedPermissionsGroupRole(roleName);
            default:
                return defaultPermissionsGroupRole(roleName);
        }
    }
}
