package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.BasePermissions;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.repository.PermissionRepository;

import javax.transaction.Transactional;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class PermissionsManager implements PermissionsManagementService {

    private static final List<String> defaultGroupOrganizerPermissions =
            Arrays.asList(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER, BasePermissions.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
                          BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER, BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                          BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, BasePermissions.GROUP_PERMISSION_DELINK_SUBGROUP);

    private static final List<String> defaultCommitteeMemberPermissions =
            Arrays.asList(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER, BasePermissions.GROUP_PERMISSION_FORCE_ADD_MEMBER,
                          BasePermissions.GROUP_PERMISSION_SEE_MEMBER_DETAILS, BasePermissions.GROUP_PERMISSION_CREATE_SUBGROUP);

    // todo: add GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY
    private static final List<String> defaultOrdinaryMemberPermissions =
            Arrays.asList(BasePermissions.GROUP_PERMISSION_CREATE_GROUP_MEETING, BasePermissions.GROUP_PERMISSION_CREATE_GROUP_VOTE,
                          BasePermissions.GROUP_PERMISSION_READ_UPCOMING_EVENTS, BasePermissions.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
                          BasePermissions.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);

    @Autowired
    private PermissionRepository permissionRepository;

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
        // todo: find a way to replace this with a single query call, otherwise group set up will kill us
        Set<Permission> permissionsSet = new HashSet<>();
        for (String name : permissionNames) {
            permissionsSet.add(permissionRepository.findByName(name));
        }
        return permissionsSet;
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
}
