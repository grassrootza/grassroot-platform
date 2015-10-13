package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.repository.PermissionRepository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class PermissionsManager implements  PermissionsManagementService{

    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    public Permission createPermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    @Override
    public Permission updatePermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    @Override
    public Permission getPermission(Long permissionId) {
        return permissionRepository.findOne(permissionId);
    }

    @Override
    public void deletePermission(Permission permission) {

        permissionRepository.delete(permission);
    }

    @Override
    public List<Permission> getPermissions() {
        return Lists.newArrayList(permissionRepository.findAll());
    }

    @Override
    public Permission findByName(String name) {
        return permissionRepository.findByName(name);
    }
}
