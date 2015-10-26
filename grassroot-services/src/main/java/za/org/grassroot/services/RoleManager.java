package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.repository.RoleRepository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class RoleManager implements  RoleManagementService {

    @Autowired
    private RoleRepository roleRepository;

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
        List<Role> roles = roleRepository.findByNameAndRoleType(name, Role.RoleType.STANDARD);
        //we really should have one standard Role
         return  !roles.isEmpty()? roles.get(0): null;
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
    public Role createGroupRole(String roleName, Long groupId, String groupName) {

        Role role = roleRepository.findByNameAndGroupReferenceId(roleName,groupId);

        if(role == null)
        {
            role = roleRepository.save( new Role(roleName,groupId,groupName));
        }
        return role;
    }
}
