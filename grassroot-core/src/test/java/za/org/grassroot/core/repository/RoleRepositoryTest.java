package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class RoleRepositoryTest {


    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Rollback
    public void testSaveRole() throws Exception {

        Role role = new Role("CREATE_USER");
        role = roleRepository.save(role);
        assertThat(role.getId(), notNullValue());
    }

    @Test
    @Rollback
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

    @Test
    @Rollback
    public void testRemovePermission() throws Exception {

        Permission permission = new Permission("groups.manage.remove.user");
        Role role = new Role("MANAGE_GROUPS");
        role.addPermission(permission);
        permission = permissionRepository.save(permission);
        role = roleRepository.save(role);
        assertNotNull(role.getId());
        role.getPermissions().remove(permission);
        Role rolePersisted = roleRepository.save(role);
        assertThat(rolePersisted.getId(), is(role.getId()));
        assertThat(rolePersisted.getPermissions().contains(permission), is(false));

    }

    @Test
    @Rollback
    public void testDeleteRote() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        Role role = roleRepository.save(new Role("CREATE_USER"));
        assertThat(roleRepository.count(), is(1L));
        roleRepository.delete(role);
        assertThat(roleRepository.count(), is(0L));
    }

    @Test
    @Rollback
    public void testSaveWithType() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        Role role = new Role("ROLE_ACCOUNT_ADMIN");
        role.setRoleType(Role.RoleType.STANDARD);
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
        Role role = new Role(roleName);
        role = roleRepository.save(role);
        Role roleFromDb = roleRepository.findByName(roleName).get(0);
        assertNotNull(roleFromDb);
        assertThat(roleFromDb.getId(), is(role.getId()));
    }

    @Test
    @Rollback
    public void testSaveWithGroupReference() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        String roleName = "ADD_MEMBER";
        User user = userRepository.save(new User("0812223456"));
        Group group = groupRepository.save(new Group("testGroup", user));
        Role role = roleRepository.save(new Role(roleName, group.getId(), group.getGroupName()));
        Role roleFromDb = roleRepository.findByNameAndGroupReferenceId(roleName, group.getId());
        assertNotNull(roleFromDb);
        assertThat(roleFromDb.getId(), is(role.getId()));
        assertThat(roleFromDb.getName(), is(role.getName()));
        assertThat(roleFromDb.getGroupReferenceName(), is(role.getGroupReferenceName()));
    }

    @Test
    @Rollback
    public void testGroupAssignment() throws Exception {
        assertThat(roleRepository.count(), is(0L));
        String roleName1 = "GROUP_ORGANIZER";
        String roleName2 = "ORDINARY_MEMBER";
        String roleName3 = "COMMITTEE_MEMBER";
        User user = userRepository.save(new User("0801110000"));
        Group group1 = groupRepository.save(new Group("gc1", user));
        Group group2 = groupRepository.save(new Group("gc2", user));

        Role role1a = roleRepository.save(new Role(roleName1, group1.getId(), group1.getGroupName()));
        Role role1b = roleRepository.save(new Role(roleName2, group1.getId(), group1.getGroupName()));
        Role role2a = roleRepository.save(new Role(roleName1, group2.getId(), group2.getGroupName()));
        Role role2b =  roleRepository.save(new Role(roleName2, group2.getId(), group2.getGroupName()));
        Role role2c = roleRepository.save(new Role(roleName3, group2.getId(), group2.getGroupName()));
        assertThat(roleRepository.count(), is(5L));

        Set<Role> group1roles = roleRepository.findByGroupReferenceId(group1.getId());
        Set<Role> group2roles = roleRepository.findByGroupReferenceId(group2.getId());
        assertThat(group1roles.size(), is(2));
        assertThat(group2roles.size(), is(3));
        assertTrue(group1roles.contains(role1a));
        assertTrue(group1roles.contains(role1b));
        assertTrue(group2roles.contains(role2a));
        assertTrue(group2roles.contains(role2b));
        assertTrue(group2roles.contains(role2c));
    }

    @Test
    @Rollback
    public void testGroupAssignmentAfterConstruction() throws Exception {
        assertThat(roleRepository.count(), is(0L));

        roleRepository.save(new Role("GROUP_ORGANIZER"));
        assertThat(roleRepository.count(), is(1L));

        User user = userRepository.save(new User("0811110001"));
        Group group = groupRepository.save(new Group("test Group", user));

        Role roleFromDb1 = roleRepository.findByName("GROUP_ORGANIZER").iterator().next();
        roleFromDb1.setGroup(group);
        roleRepository.save(roleFromDb1);
        assertThat(roleRepository.count(), is(1L)); // check doesn't duplicate

        Role roleFromDb2 = roleRepository.findByNameAndGroupReferenceId("GROUP_ORGANIZER", group.getId());
        assertNotNull(roleFromDb2);
        assertThat(roleFromDb1, is(roleFromDb2));
    }
}
