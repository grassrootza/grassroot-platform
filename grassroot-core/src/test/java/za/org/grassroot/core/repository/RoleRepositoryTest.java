package za.org.grassroot.core.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;

import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Lesetse Kimwaga
 */

@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
public class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Rollback
    public void testSaveRole() throws Exception {
        Role role = new Role("CREATE_USER", null);
        role = roleRepository.save(role);
        assertThat(role.getId(), notNullValue());
    }

    @Test
    @Rollback
    public void testSaveWithPermissions() throws Exception {
        Role role = new Role("MANAGE_GROUPS", null);
        role.addPermission(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);

        role = roleRepository.save(role);
        assertThat(role.getId(), notNullValue());

        assertThat(role.getPermissions().iterator().next(), notNullValue());
    }

    @Test
    @Rollback
    public void testRemovePermission() throws Exception {

        Role role = new Role("MANAGE_GROUPS", null);
        role.addPermission(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);
        role = roleRepository.save(role);
        assertNotNull(role.getId());

        role.removePermission(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);
        Role rolePersisted = roleRepository.save(role);

        assertThat(rolePersisted.getId(), is(role.getId()));
        assertThat(rolePersisted.getPermissions().contains(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER), is(false));

    }

    @Test
    @Rollback
    public void testDeleteRote() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        Role role = roleRepository.save(new Role("CREATE_USER", null));
        assertThat(roleRepository.count(), is(1L));
        roleRepository.delete(role);
        assertThat(roleRepository.count(), is(0L));
    }

    @Test
    @Rollback
    public void testSaveWithType() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        Role role = new Role("ROLE_ACCOUNT_ADMIN", null);
        roleRepository.save(role);
        assertThat(roleRepository.count(), is(1L));
        Role roleFromDb = roleRepository.findByNameAndRoleType("ROLE_ACCOUNT_ADMIN", Role.RoleType.STANDARD).get(0);
        assertNotNull(roleFromDb);
        assertThat(roleFromDb.getName(), is(role.getName()));
    }

    @Test
    @Rollback
    public void testFindByName() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        String roleName = "CREATE_USER";
        Role role = new Role(roleName, null);
        role = roleRepository.save(role);
        Role roleFromDb = roleRepository.findByName(roleName).get(0);
        assertNotNull(roleFromDb);
        assertThat(roleFromDb.getId(), is(role.getId()));
    }
}
