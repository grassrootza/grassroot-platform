package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
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

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@EnableTransactionManagement
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupManagementServiceTest extends AbstractTransactionalJUnit4SpringContextTests {

    private static final Logger log = LoggerFactory.getLogger(GroupManagementServiceTest.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    /*
    Testing parent detection
     */
    @Test
    public void shouldDetectLoop() {
        User user = userRepository.save(new User("0824444441"));
        Group g1 = groupRepository.save(new Group("g1",user));
        Group g2 = groupRepository.save(new Group("g2",user,g1));
        assertEquals(true, groupManagementService.isGroupAlsoParent(g1, g2));
    }

    @Test
    public void shouldNotDetectLoop() {
        User user = userRepository.save(new User("0824444442"));
        Group g1 = groupRepository.save(new Group("g1",user));
        Group g2 = groupRepository.save(new Group("g2",user,g1));
        Group g3 = groupRepository.save(new Group("g3",user));
        assertEquals(false, groupManagementService.isGroupAlsoParent(g3, g2));
    }

    /*
    Testing group member addition and consolidation
     */
    @Test
    @Rollback
    public void shouldAddMultipleNumbersToGroup() {
        User user = userManagementService.loadOrSaveUser("0810001111");
        Group group = groupManagementService.createNewGroup(user, "test group");
        log.info("ZOG: Group created ..." + group.toString());
        groupManagementService.addNumbersToGroup(group.getId(), Arrays.asList("0810001111", "0810001112", "0810001113", "0810001114"));
        log.info("ZOG: Group now looks like ... " + group.toString() + "... with groupMembers ... " + group.getGroupMembers().toString());
        assertNotNull(group.getGroupMembers());
        assertEquals(4, group.getGroupMembers().size());
        // further tests, e.g., that members contains the users created, stretch persistence lucky-streak to its breaking point
    }

}
