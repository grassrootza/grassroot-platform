package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@EnableTransactionManagement
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupManagementServiceTest {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void shouldDetectLoop() {
        User user = userRepository.save(new User("0824444441"));
        Group g1 = groupRepository.save(new Group("g1",user));
        Group g2 = groupRepository.save(new Group("g2",user,g1));
        Assert.assertEquals(true,groupManagementService.isGroupAlsoParent(g1,g2));
    }
    @Test
    public void shouldNotDetectLoop() {
        User user = userRepository.save(new User("0824444442"));
        Group g1 = groupRepository.save(new Group("g1",user));
        Group g2 = groupRepository.save(new Group("g2",user,g1));
        Group g3 = groupRepository.save(new Group("g3",user));
        Assert.assertEquals(false,groupManagementService.isGroupAlsoParent(g3,g2));
    }

}
