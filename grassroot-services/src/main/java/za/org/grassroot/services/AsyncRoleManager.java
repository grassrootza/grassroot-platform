package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.exception.GroupHasNoRolesException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Created by luke on 2016/02/17.
 */
@Service
@Transactional
@Lazy
public class AsyncRoleManager implements AsyncRoleService {

    private static final Logger log = LoggerFactory.getLogger(AsyncRoleManager.class);

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PermissionsManagementService permissionsManagementService;

    @Autowired
    GroupAccessControlManagementService groupAccessControlManagementService;

    @Autowired
    RoleManagementService roleManagementService;

    @Async
    @Override
    public void resetGroupToDefaultRolesPermissions(Long groupId) {

        log.info("Resetting group to creator as organizer, rest as members ... ");
        Long startTime = System.currentTimeMillis();
        Group group = groupRepository.findOne(groupId);
        List<User> groupMembers = new ArrayList<>(group.getGroupMembers());

        Role ordinaryRole = (fetchGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, groupId) != null) ?
                fetchGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, groupId) :
                new Role(BaseRoles.ROLE_ORDINARY_MEMBER, groupId, group.getGroupName());

        ordinaryRole.setPermissions(permissionsManagementService.defaultOrdinaryMemberPermissions());
        ordinaryRole = roleRepository.save(ordinaryRole);

        for (User member : groupMembers) {
            // log.info("Resetting member ... " + member.nameToDisplay());
            flushUserRolesInGroup(member, group);
            member.addRole(ordinaryRole);
        }

        userRepository.save(groupMembers);

        // note: we only call this from the web application, so don't have to worry about passing the modifying user
        addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, group.getCreatedByUser(), null);
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Added roles to members, total time took %d msecs", endTime - startTime));
        log.info("Exiting the resetGroupToDefault method ...");
    }

    // @Async
    @Override
    public Future<Role> fetchOrCreateGroupRole(String roleName, Group group) {
        Role role = roleRepository.findByNameAndGroupReferenceId(roleName, group.getId());
        if (role == null) {
            group.setGroupRoles(roleManagementService.createGroupRoles(group.getId(), group.getGroupName()));
            group = groupRepository.saveAndFlush(group);
            role = fetchGroupRole(roleName, group.getId());
        }
        return new AsyncResult<>(role);
    }

    // @Async
    @Override
    @Transactional
    public void addDefaultRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser) {

        Role role = fetchGroupRole(roleName, group.getId());
        log.info("Async flushing addingToUser roles ... starting with them as ... " + addingToUser.getRoles());
        addingToUser = flushUserRolesInGroup(addingToUser, group);

        if (role == null) { throw new GroupHasNoRolesException(); }

        log.info("Retrieved the following role: " + role.describe());
        if (role.getPermissions() == null || role.getPermissions().isEmpty())
            role.setPermissions(permissionsManagementService.defaultPermissionsGroupRole(role.getName()));
        role = roleRepository.save(role);

        log.info("At present, addingToUser has these roles ... " + addingToUser.getRoles());
        addingToUser.addRole(role);
        // addingToUser = userRepository.saveAndFlush(addingToUser);
        // log.info("After DB save, addingToUser has these roles ... " + addingToUser.getRoles());

        // now that we have a role with the right set of permissions, finish off by wiring up access control
        groupAccessControlManagementService.addUserGroupPermissions(group, addingToUser, callingUser, role.getPermissions());

    }

    @Override
    public void removeUsersRoleInGroup(User user, Group group) {
        // todo: make sure this is properly flushing throughout (else security leak)
        userRepository.save(flushUserRolesInGroup(user, group));
    }

    @Override
    public Role assignPermissionsToRole(Role role, List<Permission> permissions) {
        return null;
    }

    @Override
    public Role addPermissionToRole(Role role, Permission permission) {
        return null;
    }

    @Override
    public Role removePermissionFromRole(Role role, Permission permission) {
        return null;
    }

    private Role fetchGroupRole(String roleName, Long groupId) {
        return roleRepository.findByNameAndGroupReferenceId(roleName, groupId);
    }

    private User flushUserRolesInGroup(User user, Group group) {
        List<Role> oldRoles = new ArrayList<>(user.getRoles());
        for (Role role: oldRoles) {
            if (role.isGroupRole() && (role.getGroupReferenceId() == group.getId())) {
                log.info("Found a group role to flush! User ... " + user.nameToDisplay() + " ... and role ... " + role.toString());
                user.removeRole(role);
            }
        }
        return user;
    }

}
