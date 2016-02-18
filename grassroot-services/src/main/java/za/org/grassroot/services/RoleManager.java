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
import java.util.*;

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
        List<Role> roles = roleRepository.findByNameAndRoleType(name, Role.RoleType.GROUP);
        return !roles.isEmpty() ? roles.get(0) : null;
    }

    @Override
    public Set<Role> createGroupRoles(Long groupId, String groupName) {
        // todo: make sure these are batch processing by controlling session
        Role organizer = roleRepository.save(new Role(BaseRoles.ROLE_GROUP_ORGANIZER, groupId, groupName));
        Role committee = roleRepository.save(new Role(BaseRoles.ROLE_COMMITTEE_MEMBER, groupId, groupName));
        Role ordinary = roleRepository.save(new Role(BaseRoles.ROLE_ORDINARY_MEMBER, groupId, groupName));
        roleRepository.flush();
        return new HashSet<>(Arrays.asList(organizer, committee, ordinary));
    }

    @Override
    public Set<Role> fetchGroupRoles(Long groupId) {
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
    public User removeGroupRolesFromUser(User user, Group group) {
        user = flushUserRolesInGroup(user, group);
        return userManagementService.save(user);
    }

    @Override
    public Role getUserRoleInGroup(User user, Group group) {

        if (!groupManagementService.isUserInGroup(group, user))
            throw new RuntimeException("Get user role in group: Error! User not in group");

        for (Role role : user.getRoles()) {
            if (role.isGroupRole() && (role.getGroupReferenceId() == group.getId())) {
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

}
