package za.org.grassroot.core.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

/**
 * @author Lesetse Kimwaga
 */

@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
public class UserGroupRoleTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Ignore
    public void testCreateGroupRoles() throws Exception {
        User user = new User("27729100003", null, null);
        user.setFirstName("Java");
        user.setLastName("Pablo");

        user = userRepository.save(user);

        Group group1 = new Group("Red Devils", GroupPermissionTemplate.DEFAULT_GROUP, user);
        Group group2 = new Group("Code Nation", GroupPermissionTemplate.DEFAULT_GROUP, user);

        group1.addMember(user, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group2.addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);

        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);

        assertThat(user.getAuthorities(), hasSize(2));
//        assertThat(user.getAuthorities(), CoreMatchers.hasItem(group1Role1));
//        assertThat(user.getAuthorities(), hasItem(Matchers.<GrantedAuthority>hasProperty("authority", equalTo("GROUP_ROLE_GROUP_ADMINISTRATOR_GROUP_ID_" + group1.getId()))));

    }
}
