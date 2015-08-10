package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import javax.transaction.Transactional;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
public class RoleRepositoryTest {


    @Autowired
    private  RoleRepository roleRepository;

    @Autowired
    private  PermissionRepository permissionRepository;

    @Test
    public void tesSaveRole() throws Exception {

        Role role = new Role("CREATE_USER");
        role = roleRepository.save(role);
        assertThat(role.getId(), notNullValue());
    }

    @Test
    public void testSaveWithPermissions() throws Exception {

        Permission permission = new Permission("groups.manage.remove.user");

        Role role = new Role("MANAGE_GROUPS");
        role.addPermission(permission);

        permission = permissionRepository.save(permission);

        role = roleRepository.save(role);
        assertThat(role.getId(), notNullValue());

        permission  = role.getPermissions().iterator().next();
        assertThat(permission, notNullValue());
        assertThat(permission.getId(), notNullValue());


    }
}
