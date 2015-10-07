package za.org.grassroot.core.repository;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;


import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.*;

import javax.transaction.Transactional;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class UserGroupRoleTest {


    @Autowired
    private RoleRepository       roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private GroupRepository      groupRepository;
    @Autowired
    private UserRepository       userRepository;

    @Test
    public void testName() throws Exception {

        User user = new User("27729100003");
        user.setFirstName("Java");
        user.setLastName("Pablo");

        user = userRepository.save(user);

        Group group1 = new Group("Red Devils", user);
        Group group2 = new Group("Code Nation", user);

        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);

        Role group1Role1 = new Role("GROUP_MANAGER", group1.getId(), group1.getGroupName());

        group1Role1 = roleRepository.save(group1Role1);
        assertThat(group1Role1.getRoleType(), equalTo(Role.RoleType.GROUP));
        assertThat(group1Role1.getAuthority(), equalTo("GROUP_ROLE_GROUP_MANAGER_GROUP_ID_" + group1.getId() ));


        Permission permission1 = new Permission("UN_SUBSCRIBE_GROUP_MEMBER");
        permission1 = permissionRepository.save(permission1);

        group1Role1.addPermission(permission1);
        group1Role1 = roleRepository.save(group1Role1);

        group1.getGroupRoles().add(group1Role1);
        // group2.getGroupRoles().add(role1);


        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);

        user.addRole(group1.getGroupRoles());
        user.addRole(group2.getGroupRoles());

        user = userRepository.save(user);

        assertThat(user.getAuthorities(), hasSize(2));

        assertThat(user.getAuthorities(), CoreMatchers.hasItem(group1Role1));

        assertThat(user.getAuthorities(), hasItem(Matchers.<GrantedAuthority>hasProperty("authority", equalTo("GROUP_ROLE_GROUP_MANAGER_GROUP_ID_" + group1.getId()))));
//        assertThat(user.getAuthorities(), hasItem(Matchers.<GrantedAuthority>hasProperty("authority", equalTo("PERMISSION_UN_SUBSCRIBE_GROUP_MEMBER"))));


    }
}
