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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@Transactional
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

    private final String testUserBase = "081000555";
    private final String testGroupBase = "test group ";

    /*
    Testing parent detection
     */
    @Test
    public void shouldDetectLoop() {
        User user = userRepository.save(new User("0824444441"));
        Group g1 = groupRepository.save(new Group("g1", user));
        Group g2 = groupRepository.save(new Group("g2", user, g1));
        assertEquals(true, groupManagementService.isGroupAlsoParent(g1, g2));
    }

    @Test
    public void shouldNotDetectLoop() {
        User user = userRepository.save(new User("0824444442"));
        Group g1 = groupRepository.save(new Group("g1", user));
        Group g2 = groupRepository.save(new Group("g2", user, g1));
        Group g3 = groupRepository.save(new Group("g3", user));
        assertEquals(false, groupManagementService.isGroupAlsoParent(g3, g2));
    }

    /*
    Testing group member addition and group consolidation
     */

    @Test
    @Rollback
    public void shouldNotDuplicateMembers() {
        User user = userManagementService.loadOrSaveUser(testUserBase + "0");
        User user2 = userManagementService.loadOrSaveUser(testUserBase + "1");

        // todo: swithc these to "true" on roles once set up a user with authorities
        Group group = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "1"), false);
        assertThat(group.getGroupMembers().size(), is(2));
        Group group2 = groupManagementService.addGroupMember(group, user2, user.getId(), false);
        assertThat(group2.getGroupMembers().size(), is(2));
    }

    @Test
    @Rollback
    public void shouldAddMultipleNumbersToGroup() {
        User user = userManagementService.loadOrSaveUser("0810001111");
        Group group = groupManagementService.createNewGroup(user, "test group", false);
        log.info("ZOG: Group created ..." + group.toString());
        groupManagementService.addNumbersToGroup(group.getId(), Arrays.asList("0810001111", "0810001112", "0810001113", "0810001114"), user, false);
        log.info("ZOG: Group now looks like ... " + group.toString() + "... with groupMembers ... " + group.getGroupMembers().toString());
        assertNotNull(group.getGroupMembers());
        assertEquals(4, group.getGroupMembers().size());
        // further tests, e.g., that members contains the users created, stretch persistence lucky-streak to its breaking point
    }

    @Test
    @Rollback
    public void shouldSetGroupInactive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "1");
        Group group = groupManagementService.createNewGroup(user, testGroupBase + "1", false);
        Group group2 = groupManagementService.createNewGroup(user, testGroupBase + "2", false);
        groupManagementService.setGroupInactive(group, user);

        // todo: do a 'find by active' and count here instead
        Group groupFromDb = groupManagementService.loadGroup(group.getId());
        assertNotNull(groupFromDb);
        assertFalse(groupFromDb.isActive());
    }

    @Test
    @Rollback
    public void shouldOnlyReturnCreatedGroups() {
        assertThat(groupRepository.count(), is(0L));
        User user1 = userManagementService.loadOrSaveUser(testUserBase + "1");
        User user2 = userManagementService.loadOrSaveUser(testUserBase + "2");
        Group group1 = groupManagementService.createNewGroup(user1, testGroupBase + "1", false);
        Group group2 = groupManagementService.createNewGroup(user2, testGroupBase + "2", false);
        groupManagementService.addGroupMember(group1, user1, user1.getId(), false);
        groupManagementService.addGroupMember(group2, user1, user1.getId(), false);
        assertTrue(group2.getGroupMembers().contains(user1));
        List<Group> list1 = groupManagementService.getActiveGroupsPartOf(user1);
        List<Group> list2 = groupManagementService.getCreatedGroups(user1);
        assertNotEquals(list1, list2);
        assertThat(list1.size(), is(2));
        assertThat(list2.size(), is(1));
    }

    @Test
    @Rollback
    public void shouldMergeGroups() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "2");

        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3", testUserBase + "4", testUserBase + "5"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "6", testUserBase + "7"), false);

        assertThat(group1.getGroupMembers().size(), is(4));
        assertTrue(group1.isActive());
        assertThat(group2.getGroupMembers().size(), is(3));

        groupManagementService.mergeGroups(group1, group2, user.getId());
        assertNotNull(group1);
        assertNotNull(group2);
        assertTrue(group1.isActive());
        assertFalse(group2.isActive());
        assertThat(group1.getGroupMembers().size(), is(6));
        assertTrue(group1.getGroupMembers().contains(userManagementService.findByInputNumber(testUserBase + "6")));
    }

    @Test
    @Rollback
    public void shouldNotFindInactiveGroups() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "3");

        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "4"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "5"), false);

        List<Group> list1 = groupManagementService.getActiveGroupsPartOf(user);
        assertThat(list1.size(), is(2));
        assertTrue(list1.contains(group1));
        assertTrue(list1.contains(group2));

        groupManagementService.setGroupInactive(group2, user);

        List<Group> list2 = groupManagementService.getActiveGroupsPartOf(user);
        assertThat(list2.size(), is(1));
        assertTrue(list2.contains(group1));
        assertFalse(list2.contains(group2));
    }

    @Test
    @Rollback
    public void mergeSpecificOrderShouldWorkForSmallerGroup() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "0");

        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3", testUserBase + "4", testUserBase + "5"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "6", testUserBase + "7"), false);

        groupManagementService.mergeGroupsSpecifyOrder(group2, group1, true, user.getId());

        assertNotNull(group1);
        assertNotNull(group2);
        assertFalse(group1.isActive());
        assertTrue(group2.isActive());
        assertThat(group2.getGroupMembers().size(), is(6));
        assertTrue(group2.getGroupMembers().contains(userManagementService.findByInputNumber(testUserBase + "3")));
    }

    @Test
    @Rollback
    public void groupMergeShouldLeaveActiveIfFlagged() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "1");
        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "2"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3"), false);
        groupManagementService.mergeGroups(group1, group2, false, user.getId());
        assertNotNull(group1);
        assertNotNull(group2);
        assertTrue(group1.isActive());
        assertTrue(group2.isActive());
        assertThat(groupManagementService.getActiveGroupsPartOf(user).size(), is(2));
    }

    @Test
    @Rollback
    public void getMergeCandidatesShouldReturnAccurately() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrSaveUser(testUserBase + "1");
        User user2 = userManagementService.loadOrSaveUser(testUserBase + "2");

        Group group1 = groupManagementService.createNewGroup(user, testGroupBase + "1", false);
        Group group2 = groupManagementService.createNewGroup(user, testGroupBase + "2", false);
        Group group3 = groupManagementService.createNewGroup(user, testGroupBase + "3", false);
        Group group4 = groupManagementService.createNewGroup(user2, Arrays.asList(testUserBase + "1"), false);

        assertThat(groupRepository.count(), is(4L));

        List<Group> list = groupManagementService.getActiveGroupsPartOf(user);
        List<Group> list1 = groupManagementService.getCreatedGroups(user);
        List<Group> list2 = groupManagementService.getMergeCandidates(user, group1.getId());

        assertTrue(list.contains(group4));
        assertFalse(list2.contains(group4));

        assertTrue(list1.contains(group1));
        assertFalse(list2.contains(group1));

        assertThat(list2.size(), is(2));
        assertTrue(list2.contains(group2));
        assertTrue(list2.contains(group3));
    }

    @Test
    public void shouldCreateSubGroup() {

        User userProfile = userManagementService.createUserProfile(new User("111111111", "aap1"));

        Group level1 = groupManagementService.createNewGroup(userProfile, Arrays.asList("111111112", "111111113"), false);
        Group level2 = groupManagementService.createSubGroup(userProfile, level1, "level2 group");
        assertEquals(level2.getParent().getId(), level1.getId());
    }

    //@Test
    public void shouldReturnGroupAndSubGroups() {

        User userProfile = userManagementService.createUserProfile(new User("111111111", "aap1"));

        Group level1 = groupManagementService.createNewGroup(userProfile, Arrays.asList("111111112", "111111113"), false);
        Group level2 = groupManagementService.createSubGroup(userProfile, level1, "level2 group");
        assertEquals(level2.getParent().getId(), level1.getId());
        List<Group> children = groupManagementService.getSubGroups(level1);
        assertEquals(1, children.size());
        TestTransaction.end();
        TestTransaction.start();
        List<Group> list = groupManagementService.findGroupAndSubGroupsById(level1.getId());
        assertEquals(2, list.size());
    }


}
