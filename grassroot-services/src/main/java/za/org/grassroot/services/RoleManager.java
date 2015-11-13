package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.RoleRepository;

import javax.transaction.Transactional;
import java.util.List;

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

    @Override
    public Role createRole(Role role) {
        return roleRepository.save(role);
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
        return roleRepository.findByNameAndGroupReferenceId(roleName,groupId);
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
    public Group addRoleToGroup(Role role, Group group) {
        group.addRole(role);
        return groupManagementService.saveGroup(group);
    }

    @Override
    public Group addRoleToGroup(String roleName, Group group) {
        return addRoleToGroup(fetchGroupRoleByName(roleName), group);
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
