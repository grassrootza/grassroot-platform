package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

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
        addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, group.getCreatedByUser());
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Added roles to members, total time took %d msecs", endTime - startTime));
        log.info("Exiting the resetGroupToDefault method ...");
    }

    @Override
    public void addDefaultRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser) {
        // todo: throw a fit if roleName is not standard

        Role role;

        // log.info("Flushing addingToUser roles ... starting with them as ... " + addingToUser.getRoles());
        addingToUser = flushUserRolesInGroup(addingToUser, group);
        // log.info("User roles flushed, now with ... " + addingToUser.getRoles());

        if (fetchGroupRole(roleName, group) == null) {
            // create the role with default permissions and add it to the group
            role = new Role(roleName, group.getId(), group.getGroupName());
            log.info("Created this new role: " + role.describe());
            role.setPermissions(permissionsManagementService.defaultPermissionsGroupRole(roleName));
            role = roleRepository.save(role);
            // log.info("Role saved as ... " + role.describe());
            group.addRole(role);
            groupManagementService.saveGroup(group,true, String.format("Added role %s to group",role.getName()),dontKnowTheUser);
            addingToUser.addRole(role);
            userManagementService.save(addingToUser);
        } else {
            // role exists, just make sure it has a set of permissions and add it to addingToUser and group
            // todo: work out what to do if role has a non-BaseRoles name and permissions set is empty (throw a fit)
            role = fetchGroupRole(roleName, group);
            log.info("Retrieved the following role: " + role.describe());
            if (role.getPermissions() == null || role.getPermissions().isEmpty())
                role.setPermissions(permissionsManagementService.defaultPermissionsGroupRole(role.getName()));
            role = roleRepository.save(role);
            group.addRole(role);
            groupManagementService.saveGroup(group,true, String.format("Added role %s to group",role.getName()),dontKnowTheUser);
            log.info("Okay, group saved, about to save role ..." + role.describe());
            log.info("At present, addingToUser has these roles ... " + addingToUser.getRoles());
            addingToUser = userManagementService.save(addingToUser);
            log.info("After DB save, addingToUser has these roles ... " + addingToUser.getRoles());
            addingToUser.addRole(role);
            addingToUser = userManagementService.save(addingToUser);
            log.info("After role addition and DB save, addingToUser has these roles ... " + addingToUser.getRoles());
        }

        // now that we have a role with the right set of permissions, finish off by wiring up access control
        groupAccessControlManagementService.addUserGroupPermissions(group, addingToUser, callingUser, role.getPermissions());

    }

    @Override
    public void addDefaultRoleToGroupAndUser(String roleName, Group group, User user) {
        addDefaultRoleToGroupAndUser(roleName, group, user, null);
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





}
