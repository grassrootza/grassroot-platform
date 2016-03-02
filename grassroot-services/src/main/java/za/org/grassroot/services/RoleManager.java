package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
public class RoleManager implements RoleManagementService {

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
    public Set<Role> createGroupRoles(String groupUid) {
        // todo: make sure these are batch processing by controlling session
        Role organizer = roleRepository.save(new Role(BaseRoles.ROLE_GROUP_ORGANIZER, groupUid));
        Role committee = roleRepository.save(new Role(BaseRoles.ROLE_COMMITTEE_MEMBER, groupUid));
        Role ordinary = roleRepository.save(new Role(BaseRoles.ROLE_ORDINARY_MEMBER, groupUid));
        roleRepository.flush();
        return new HashSet<>(Arrays.asList(organizer, committee, ordinary));
    }

    @Override
    public Map<String, Role> fetchGroupRoles(String groupUid) {
        Map<String, Role> groupRoles = new HashMap<>();
        groupRoles.put(BaseRoles.ROLE_ORDINARY_MEMBER,
                       roleRepository.findByNameAndGroupUid(BaseRoles.ROLE_ORDINARY_MEMBER, groupUid));
        groupRoles.put(BaseRoles.ROLE_COMMITTEE_MEMBER,
                       roleRepository.findByNameAndGroupUid(BaseRoles.ROLE_COMMITTEE_MEMBER, groupUid));
        groupRoles.put(BaseRoles.ROLE_GROUP_ORGANIZER,
                       roleRepository.findByNameAndGroupUid(BaseRoles.ROLE_GROUP_ORGANIZER, groupUid));
        return groupRoles;
    }

    @Override
    public Role fetchGroupRole(String roleName, String groupUid) {
        return roleRepository.findByNameAndGroupUid(roleName, groupUid);
    }

    @Override
    public Role fetchGroupRole(String roleName, Group group) {
        return fetchGroupRole(roleName, group.getUid());
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
    public Role getUserRoleInGroup(User user, Group group) {

        // note: this is currently used just to display user's role in a view already secured, hence redundant here (but keep eye)
        /* if (!groupManagementService.isUserInGroup(group, user))
            throw new RuntimeException("Get user role in group: Error! User not in group"); */

        return getUserRoleInGroup(user, group.getId()); // if the user has not been given a role in the group, this is what we return
    }

    @Override
    public Role getUserRoleInGroup(User user, Long groupId) {
        return user.getMemberships().stream()
                .filter(membership -> membership.getGroup().getId().equals(groupId))
                .map(Membership::getRole)
                .findFirst().orElse(null);
    }

    @Override
    public boolean doesUserHaveRoleInGroup(User user, Group group, String roleName) {
        Role role = fetchGroupRoleByName(roleName);
        return (getUserRoleInGroup(user, group) == role);
    }
}
