package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.RoleRepository;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
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
        log.info("Attempting to fetch a standard role from this role name ... " + name);
        List<Role> roles = roleRepository.findByNameAndRoleType(name, Role.RoleType.STANDARD);
        //we really should have one standard Role
        log.info("Found these roles: " + roles);
         return  !roles.isEmpty()? roles.get(0): null;
    }

    @Override
    public Role fetchGroupRoleByName(String name) {
        log.info("Fetching group role through this name ... " + name);
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
        groupManagementService.saveGroup(group);
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
    public void addDefaultRoleToGroupAndUser(String roleName, Group group, User user) {
        // todo: throw a fit if roleName is not standard

        Role role;

        log.info("Flushing user roles ... starting with them as ... " + user.getRoles());
        user = flushUserRolesInGroup(user, group);
        log.info("User roles flushed, now with ... " + user.getRoles());

        if (fetchGroupRole(roleName, group) == null) {
            // create the role with default permissions and add it to the group
            role = new Role(roleName, group.getId(), group.getGroupName());
            log.info("Created this new role: " + role.describe());
            role.setPermissions(permissionsManagementService.defaultPermissionsGroupRole(roleName));
            role = roleRepository.save(role);
            log.info("Role saved as ... " + role.describe());
            group.addRole(role);
            groupManagementService.saveGroup(group);
            user.addRole(role);
            userManagementService.save(user);
        } else {
            // role exists, just make sure it has a set of permissions and add it to user and group
            // todo: work out what to do if role has a non-BaseRoles name and permissions set is empty (throw a fit)
            role = fetchGroupRole(roleName, group);
            log.info("Retrieved the following role: " + role.toString());
            if (role.getPermissions() == null || role.getPermissions().isEmpty())
                role.setPermissions(permissionsManagementService.defaultPermissionsGroupRole(role.getName()));
            role = roleRepository.save(role);
            group.addRole(role);
            groupManagementService.saveGroup(group);
            user.addRole(role);
            userManagementService.save(user);
        }

        // now that we have a role with the right set of permissions, finish off by wiring up access control
        groupAccessControlManagementService.addUserGroupPermissions(group, user, role.getPermissions());

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
        log.info("Searching for user role in group ...");
        if (!groupManagementService.isUserInGroup(group, user))
            throw new RuntimeException("Get user role in group: Error! User not in group");

        // todo: roles are eagerly loaded so this should not be too expensive, but keep an eye on its performance
        log.info("Iterating through roles for user ... " + user.nameToDisplay() + " ... in group ... " + group.getGroupName());
        for (Role role : user.getRoles()) {
            if (role.isGroupRole() && (role.getGroupReferenceId() == group.getId())) {
                log.info("Found the role, returning it as ... " + role);
                return role;
            }
        }

        return null; // if the user has not been given a role in the group, this is what we return
    }

    private User flushUserRolesInGroup(User user, Group group) {
        List<Role> oldRoles = new ArrayList<>(user.getRoles());
        for (Role role: oldRoles) {
            if (role.isGroupRole() && (role.getGroupReferenceId() == group.getId()))
                user.removeRole(role);
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
