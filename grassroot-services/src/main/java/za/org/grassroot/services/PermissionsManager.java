package za.org.grassroot.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.BasePermissions;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.repository.PermissionRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import javax.transaction.Transactional;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class PermissionsManager implements PermissionsManagementService {

    private static final Logger log = LoggerFactory.getLogger(PermissionsManager.class);

    // major todo: externalize these permissions

    private static final ImmutableList<String> defaultOrdinaryMemberPermissions =
            new ImmutableList.Builder<String>().
                    add(BasePermissions.GROUP_PERMISSION_SEE_MEMBER_DETAILS).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_GROUP_MEETING).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_GROUP_VOTE).
                    add(BasePermissions.GROUP_PERMISSION_READ_UPCOMING_EVENTS).
                    add(BasePermissions.GROUP_PERMISSION_VIEW_MEETING_RSVPS).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).
                    add(BasePermissions.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK).build();

    private static final ImmutableList<String> defaultCommitteeMemberPermissions =
            new ImmutableList.Builder<String>().addAll(defaultOrdinaryMemberPermissions).
                    add(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_AUTHORIZE_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION).build();

    private static final ImmutableList<String> defaultGroupOrganizerPermissions =
            new ImmutableList.Builder<String>().addAll(defaultCommitteeMemberPermissions).
                    add(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_AUTHORIZE_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS).
                    add(BasePermissions.GROUP_PERMISSION_DELINK_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_DELETE_MEMBER).build();

    // closed group structure ... again, externalize
    private static final ImmutableList<String> closedOrdinaryMemberPermissions =
            new ImmutableList.Builder<String>().
                    add(BasePermissions.GROUP_PERMISSION_READ_UPCOMING_EVENTS).build();

    private static final ImmutableList<String> closedCommitteeMemberPermissions =
            new ImmutableList.Builder<String>().addAll(defaultOrdinaryMemberPermissions).
                    add(BasePermissions.GROUP_PERMISSION_SEE_MEMBER_DETAILS).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_GROUP_MEETING).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_GROUP_VOTE).
                    add(BasePermissions.GROUP_PERMISSION_VIEW_MEETING_RSVPS).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).
                    add(BasePermissions.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK).build();

    private static final ImmutableList<String> closedGroupOrganizerPermissions =
            new ImmutableList.Builder<String>().addAll(defaultCommitteeMemberPermissions).
                    add(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_CREATE_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_AUTHORIZE_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION).
                    add(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER).
                    add(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS).
                    add(BasePermissions.GROUP_PERMISSION_DELINK_SUBGROUP).
                    add(BasePermissions.GROUP_PERMISSION_FORCE_DELETE_MEMBER).build();

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public Permission createPermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    @Override
    public Permission updatePermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    @Override
    public Permission getPermission(Long permissionId) {
        return permissionRepository.findOne(permissionId);
    }

    @Override
    public void deletePermission(Permission permission) {
        permissionRepository.delete(permission);
    }

    @Override
    public List<Permission> getPermissions() {
        return Lists.newArrayList(permissionRepository.findAll());
    }

    @Override
    public Permission findByName(String name) {
        return permissionRepository.findByName(name);
    }

    @Override
    public Set<Permission> findByNames(List<String> permissionNames) {
        // todo: take out the null handling, so errors if this is called with null are picked up
        List<Permission> permissionsList = permissionRepository.findByNameIn(permissionNames);
        log.info("findByNames returned this permissions list ... " + permissionsList);
        return permissionsList.isEmpty() ? new HashSet<>() : new HashSet<>(permissionsList);
    }

    @Override
    public Set<Permission> defaultGroupOrganizerPermissions() {
        return findByNames(defaultGroupOrganizerPermissions);
    }

    @Override
    public Set<Permission> defaultCommitteeMemberPermissions() {
        return findByNames(defaultCommitteeMemberPermissions);
    }

    @Override
    public Set<Permission> defaultOrdinaryMemberPermissions() {
        return findByNames(defaultOrdinaryMemberPermissions);
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
        return findByNames(closedGroupOrganizerPermissions);
    }

    @Override
    public Set<Permission> closedGroupCommitteeMemberPermissions() {
        return findByNames(closedCommitteeMemberPermissions);
    }

    @Override
    public Set<Permission> closedGroupOrdinaryMemberPermissions() {
        return findByNames(closedOrdinaryMemberPermissions);
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
    public Map<String, Role> setRolePermissionsFromTemplate(Map<String, Role> roles, GroupPermissionTemplate template) {

        Role organizer = roles.get(BaseRoles.ROLE_GROUP_ORGANIZER);
        Role committee = roles.get(BaseRoles.ROLE_COMMITTEE_MEMBER);
        Role member = roles.get(BaseRoles.ROLE_ORDINARY_MEMBER);

        switch(template) {
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

        Map<String, Role> savedRoles = new HashMap<>();
        savedRoles.put(BaseRoles.ROLE_GROUP_ORGANIZER, roleRepository.save(organizer));
        savedRoles.put(BaseRoles.ROLE_COMMITTEE_MEMBER, roleRepository.save(committee));
        savedRoles.put(BaseRoles.ROLE_ORDINARY_MEMBER, roleRepository.save(member));

        return savedRoles;
    }

    @Override
    public Set<Permission> getPermissions(String roleName, GroupPermissionTemplate template) {
        // todo: so this is basically iterating over two enums in a way that isn't great, this all needs a refactor prob
        Set<Permission> permissions;
        switch(template) {
            case DEFAULT_GROUP:
                permissions = defaultPermissionsGroupRole(roleName);
                break;
            case CLOSED_GROUP:
                permissions = closedPermissionsGroupRole(roleName);
                break;
            default:
                permissions = defaultPermissionsGroupRole(roleName);
                break;
        }
        return permissions;
    }
}
