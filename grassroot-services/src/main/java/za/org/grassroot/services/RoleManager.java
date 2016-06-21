package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
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
@Lazy
public class RoleManager implements RoleManagementService {

    private final static Logger log = LoggerFactory.getLogger(RoleManager.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserManagementService userManagementService;

    /*
    NB: only to be used in tests, to populate in-memory DB
     */
    @Override
    public Role createStandardRole(String roleName) {
        return roleRepository.save(new Role(roleName));
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
    public Integer getNumberStandardRoles() {
        return roleRepository.findByRoleType(Role.RoleType.STANDARD).size();
    }

    @Override
    public User addStandardRoleToUser(Role role, User user) {
        user.addStandardRole(role);
        return userManagementService.save(user);
    }

    @Override
    public User addStandardRoleToUser(String roleName, User user) {
        return addStandardRoleToUser(fetchStandardRoleByName(roleName), user);
    }
}
