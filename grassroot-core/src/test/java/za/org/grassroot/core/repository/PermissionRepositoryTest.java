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
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

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
        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE, 4);
        Permission permission3 = new Permission(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 6);

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);
        permissionRepository.save(permission3);

    }

    @Test
    public void testFindByListOfNames() throws Exception {

        assertThat(permissionRepository.count(), is(0L));

        Permission permission1 = permissionRepository.save(new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 2));
        Permission permission2 = permissionRepository.save(new Permission(BasePermissions.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE, 4));
        Permission permission3 = permissionRepository.save(new Permission(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 6));

        List<String> namesList = Arrays.asList(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                                               BasePermissions.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE);

        List<Permission> allPermissions = (List<Permission>) permissionRepository.findAll();
        List<Permission> permissionsFromQuery = permissionRepository.findByNameIn(namesList);

        assertNotNull(allPermissions);
        assertThat(allPermissions.size(), is(3));
        assertNotNull(permissionsFromQuery);
        assertThat(permissionsFromQuery.size(), is(2));

        assertThat(permissionsFromQuery.contains(permission1), is(true));
        assertThat(permissionsFromQuery.contains(permission2), is(true));
        assertThat(permissionsFromQuery.contains(permission3), is(false));

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
        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE, 2);

        permissionRepository.save(permission1);
        permissionRepository.save(permission2);

    }

}
