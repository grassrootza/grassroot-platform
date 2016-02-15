package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.RoleRepository;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
@Lazy
public class RoleManager implements  RoleManagementService {

    private final static Logger log = LoggerFactory.getLogger(RoleManager.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private PermissionsManagementService permissionsManagementService;

    @Autowired
    private GroupAccessControlManagementService groupAccessControlManagementService;
    /*
    N.B.

    When we refactor to pass the user doing actions around so that it can be recorded then replace the
    dontKnowTheUser whereever it is used with the actual user
    */
    private final Long dontKnowTheUser = 0L;


    @Override
    public Role createRole(Role role) {
        return roleRepository.save(role);
    }

    /*
    NB: only to be used in tests, to populate in-memory DB
     */
    @Override
    public Role createStandardRole(String roleName) {
        return roleRepository.save(new Role(roleName));
    }

    @Override
    public Role getRole(Long roleId) {
        return roleRepository.findOne(roleId);
    }

    @Override
    public Role updateRole(Role role) {
        return roleRepository.save(role);
    }

    @Override
    public void deleteRole(Role role) {

        roleRepository.delete(role);
    }

    @Override
    public List<Role> getAllRoles() {
        return Lists.newArrayList(roleRepository.findAll());
    }

    @Override
    public Role fetchStandardRoleByName(String name) {
        // log.info("Attempting to fetch a standard role from this role name ... " + name);
        List<Role> roles = roleRepository.findByNameAndRoleType(name, Role.RoleType.STANDARD);
        //we really should have one standard Role
        log.info("Found these roles: " + roles);
         return  !roles.isEmpty()? roles.get(0): null;
    }

    @Override
    public Role fetchGroupRoleByName(String name) {
        // log.info("Fetching group role through this name ... " + name);
        List<Role> roles = roleRepository.findByNameAndRoleType(name, Role.RoleType.GROUP);
        // as above, should have at least one group role
        return !roles.isEmpty() ? roles.get(0) : null;
    }

    @Override
    public List<Role> fetchGroupRoles(Long groupId) {
        return roleRepository.findByGroupReferenceId(groupId);
    }

    @Override
    public Role fetchGroupRole(String roleName, Long groupId) {
        return roleRepository.findByNameAndGroupReferenceId(roleName, groupId);
    }

    @Override
    public Role fetchGroupRole(String roleName, Group group) {
        return fetchGroupRole(roleName, group.getId());
    }

    @Override
    public Integer getNumberStandardRoles() {
        return roleRepository.findByRoleType(Role.RoleType.STANDARD).size();
    }

    @Override
    public User addStandardRoleToUser(Role role, User user) {
        user.addRole(role);
        return userManagementService.save(user);
    }

    @Override
    public User addStandardRoleToUser(String roleName, User user) {
        return addStandardRoleToUser(fetchStandardRoleByName(roleName), user);
    }

    @Override
    public User removeStandardRoleFromUser(Role role, User user) {
        user.removeRole(role);
        return userManagementService.save(user);
    }

    @Override
    public User removeStandardRoleFromUser(String roleName, User user) {
        return removeStandardRoleFromUser(fetchStandardRoleByName(roleName), user);
    }

    @Override
    public Role addRoleToGroup(Role role, Group group) {
        // todo: check for duplicates before doing this
        group.addRole(role);
        groupManagementService.saveGroup(group,true, String.format("Added role %s to group",role.getName()),dontKnowTheUser);
        role.setGroup(group);
        return roleRepository.save(role);
    }

    @Override
    public Role addRoleToGroup(String roleName, Group group) {
        // todo: check for duplicates before doing this
        Role role = (fetchGroupRoleByName(roleName) == null) ?
                roleRepository.save(new Role(roleName)) : fetchGroupRoleByName(roleName);
        return addRoleToGroup(role, group);
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
        userManagementService.save(flushUserRolesInGroup(user, group));
    }

    @Async
    @Override
    public void resetGroupToDefaultRolesPermissions(Long groupId) {
        log.info("Resetting group to creator as organizer, rest as members ... ");
        Long startTime = System.currentTimeMillis();
        Group group = groupManagementService.loadGroup(groupId);
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

        userManagementService.saveList(groupMembers);

        // note: we only call this from the web application, so don't have to worry about passing the modifying user
        addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, group.getCreatedByUser());
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Added roles to members, total time took %d msecs", endTime - startTime));
        log.info("Exiting the resetGroupToDefault method ...");
    }

    @Override
    public User removeGroupRolesFromUser(User user, Group group) {
        user = flushUserRolesInGroup(user, group);
        return userManagementService.save(user);
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

    @Override
    public Role getUserRoleInGroup(User user, Group group) {
        // log.info("Searching for user role in group ...");
        if (!groupManagementService.isUserInGroup(group, user))
            throw new RuntimeException("Get user role in group: Error! User not in group");

        // todo: roles are eagerly loaded so this should not be too expensive, but keep an eye on its performance
        // log.info("Iterating through roles for user ... " + user.nameToDisplay() + " ... in group ... " + group.getGroupName());
        for (Role role : user.getRoles()) {
            if (role.isGroupRole() && (role.getGroupReferenceId() == group.getId())) {
                // log.info("Found the role, returning it as ... " + role);
                return role;
            }
        }

        return null; // if the user has not been given a role in the group, this is what we return
    }

    @Override
    public boolean doesUserHaveRoleInGroup(User user, Group group, String roleName) {
        Role role = fetchGroupRoleByName(roleName);
        return (getUserRoleInGroup(user, group) == role);
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

    /*
    Deprecated
    @Override
    public Role createGroupRole(String roleName, Long groupId, String groupName) {

        Role role = roleRepository.findByNameAndGroupReferenceId(roleName,groupId);

        if(role == null)
        {
            role = roleRepository.save( new Role(roleName,groupId,groupName));
        }
        return role;
    }*/
}
