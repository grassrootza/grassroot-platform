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
}
