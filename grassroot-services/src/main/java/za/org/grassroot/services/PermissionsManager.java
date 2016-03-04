package za.org.grassroot.services;

import com.google.common.collect.Lists;
import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class PermissionsManager implements PermissionsManagementService {

    private static final Logger log = LoggerFactory.getLogger(PermissionsManager.class);

    // major todo: externalize these permissions

    private static final Set<Permission> defaultOrdinaryMemberPermissions = constructPersmissionSet(Collections.emptySet(),
            Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
            Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS,
            Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
            Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
            Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK
    );

    private static final Set<Permission> defaultCommitteeMemberPermissions = constructPersmissionSet(defaultOrdinaryMemberPermissions,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
            Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
            Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION
    );

    private static final Set<Permission> defaultGroupOrganizerPermissions = constructPersmissionSet(defaultCommitteeMemberPermissions,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
            Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
            Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
            Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
            Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER
    );

    // closed group structure ... again, externalize
    private static final Set<Permission> closedOrdinaryMemberPermissions = constructPersmissionSet(Collections.emptySet(),
            Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS
    );

    private static final Set<Permission> closedCommitteeMemberPermissions = constructPersmissionSet(defaultOrdinaryMemberPermissions,
            Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
            Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
            Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
            Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK
    );

    private static final Set<Permission> closedGroupOrganizerPermissions = constructPersmissionSet(defaultCommitteeMemberPermissions,
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


    private static final Set<Permission> constructPersmissionSet(Set<Permission> baseSet, Permission... permissions) {
        Set<Permission> set = new HashSet<>();
        set.addAll(baseSet);
        for (Permission permission : permissions) {
            set.add(permission);
        }
        return set;
    }

    @Override
    public List<Permission> getPermissions() {
        return Lists.newArrayList(Permission.values());
    }

    @Override
    public Set<Permission> defaultGroupOrganizerPermissions() {
        return defaultGroupOrganizerPermissions;
    }

    @Override
    public Set<Permission> defaultCommitteeMemberPermissions() {
        return defaultCommitteeMemberPermissions;
    }

    @Override
    public Set<Permission> defaultOrdinaryMemberPermissions() {
        return defaultOrdinaryMemberPermissions;
    }

    @Override
    public Set<Permission> defaultPermissionsGroupRole(String roleName) {
        // todo: error handling if passed a strange or null role; for now, return ordinary member
        switch (roleName) {
            case BaseRoles.ROLE_GROUP_ORGANIZER:
                return defaultGroupOrganizerPermissions();
            case BaseRoles.ROLE_COMMITTEE_MEMBER:
                return defaultCommitteeMemberPermissions();
            case BaseRoles.ROLE_ORDINARY_MEMBER:
                return defaultOrdinaryMemberPermissions();
            default:
                return defaultOrdinaryMemberPermissions();
        }
    }

    @Override
    public Set<Permission> closedGroupOrganizerPermissions() {
        return closedGroupOrganizerPermissions;
    }

    @Override
    public Set<Permission> closedGroupCommitteeMemberPermissions() {
        return closedCommitteeMemberPermissions;
    }

    @Override
    public Set<Permission> closedGroupOrdinaryMemberPermissions() {
        return closedOrdinaryMemberPermissions;
    }

    @Override
    public Set<Permission> closedPermissionsGroupRole(String roleName) {
        switch (roleName) {
            case BaseRoles.ROLE_GROUP_ORGANIZER:
                return closedGroupOrganizerPermissions();
            case BaseRoles.ROLE_COMMITTEE_MEMBER:
                return closedGroupCommitteeMemberPermissions();
            case BaseRoles.ROLE_ORDINARY_MEMBER:
                return closedGroupOrdinaryMemberPermissions();
            default:
                return closedGroupOrdinaryMemberPermissions();
        }
    }

    @Override
    public void setRolePermissionsFromTemplate(Group group, GroupPermissionTemplate template) {
        Role organizer = group.getRole(BaseRoles.ROLE_GROUP_ORGANIZER);
        Role committee = group.getRole(BaseRoles.ROLE_COMMITTEE_MEMBER);
        Role member = group.getRole(BaseRoles.ROLE_ORDINARY_MEMBER);

        switch (template) {
            case DEFAULT_GROUP:
                organizer.setPermissions(defaultGroupOrganizerPermissions());
                committee.setPermissions(defaultCommitteeMemberPermissions());
                member.setPermissions(defaultOrdinaryMemberPermissions());
                break;
            case CLOSED_GROUP:
                organizer.setPermissions(closedGroupOrganizerPermissions());
                committee.setPermissions(closedGroupCommitteeMemberPermissions());
                member.setPermissions(closedGroupOrdinaryMemberPermissions());
                break;
            default:
                organizer.setPermissions(defaultGroupOrganizerPermissions());
                committee.setPermissions(defaultCommitteeMemberPermissions());
                member.setPermissions(defaultOrdinaryMemberPermissions());
                break;
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
