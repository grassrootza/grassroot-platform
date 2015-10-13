package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.BasePermissions;
import za.org.grassroot.core.domain.Permission;

import javax.transaction.Transactional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class PermissionRepositoryTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    public void testName() throws Exception {

        Permission permission = new Permission("groups.management.delete.user");
        permission = permissionRepository.save(permission);
        assertThat(permission.getId(), notNullValue());
    }

    @Test
    public void testCreatePermissions() throws Exception {

        Permission permission1 = new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 2);
        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_CREATE_MEMBER_INVITATION, 4);
        Permission permission3 = new Permission(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 6);

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);
        permissionRepository.save(permission3);

    }


    @Test(expected = DataIntegrityViolationException.class)
    public void testCreateUniquePermissionName() throws Exception {

        Permission permission1 = new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 2);
        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 4);

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

    }

    @Test(expected = DataIntegrityViolationException.class)
    public void testCreateUniquePermissionMask() throws Exception {

        Permission permission1 = new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 2);
        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_READ_MEMBER_DETAILS, 2);

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

    }

}
