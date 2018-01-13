package za.org.grassroot.services.integration;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static za.org.grassroot.core.domain.GroupJoinMethod.ADDED_BY_OTHER_MEMBER;
import static za.org.grassroot.services.group.GroupPermissionTemplate.DEFAULT_GROUP;

// major todo: use mocking to restore these to working
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GroupBrokerTest extends AbstractTransactionalJUnit4SpringContextTests {

    private static final Logger log = LoggerFactory.getLogger(GroupBrokerTest.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    private final String testUserBase = "081000555";
    private final String testGroupBase = "test group ";

    /*
    Testing parent detection
     */
    @Test
    public void shouldDetectLoop() {
        User user = userRepository.save(new User("0824444441", null, null));
        Group g1 = groupRepository.save(new Group("g1", user));
        Group g2 = groupRepository.save(new Group("g2", user, g1));
        // todo: add a test that possible parents doesn't include g2 (or however it should be structured
        // assertEquals(true, groupManagementService.isGroupAlsoParent(g1, g2));
    }

    @Test
    public void shouldNotDetectLoop() {
        User user = userRepository.save(new User("0824444442", null, null));
        Group g1 = groupRepository.save(new Group("g1", user));
        Group g2 = groupRepository.save(new Group("g2", user, g1));
        Group g3 = groupRepository.save(new Group("g3", user));
        // assertEquals(false, groupManagementService.isGroupAlsoParent(g3, g2));
    }

    /*
    Testing group member addition and group consolidation
     */

    /* @Test
    @Rollback
    public void shouldNotDuplicateMembers() {
        User user = userManagementService.loadOrCreateUser(testUserBase + "0");
        User user2 = userManagementService.loadOrCreateUser(testUserBase + "1");

        // todo: swithc these to "true" on roles once set up a user with authorities
        Group group = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "1"), false);
        assertThat(group.getMembers().size(), is(2));
        Group group2 = groupManagementService.addGroupMember(group, user2, user.getId(), false);
        assertThat(group2.getMembers().size(), is(2));
    }*/

    // fails because of array columns ...
//    @Test
//    @Rollback
//    public void shouldAddMultipleNumbersToGroup() {
//        User user = userManagementService.loadOrCreateUser("0810001111");
//        Set<MembershipInfo> organizer = Sets.newHashSet(
//                new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, null));
//        Group group = groupBroker.create(user.getUid(), "testGroup", null, organizer, DEFAULT_GROUP, null, null, false);
//        String ordinaryRole = BaseRoles.ROLE_ORDINARY_MEMBER;
//        log.info("ZOG: Group created ..." + group.toString());
//        Set<MembershipInfo> members = Sets.newHashSet(new MembershipInfo("0810001111", ordinaryRole, ""),
//                                                      new MembershipInfo("0810001112", ordinaryRole, ""),
//                                                      new MembershipInfo("0810001113", ordinaryRole, ""),
//                                                      new MembershipInfo("0810001114", ordinaryRole, ""));
//        groupBroker.addMembers(user.getUid(), group.getUid(), members, ADDED_BY_OTHER_MEMBER, false);
//        log.info("ZOG: Group now looks like ... " + group.toString() + "... with groupMembers ... " + group.getMembers());
//        assertNotNull(group.getMembers());
//        assertEquals(4, group.getMembers().size());
//        // further tests, e.g., that members contains the users created, stretch persistence lucky-streak to its breaking point
//    }

    @Test
    @Rollback
    public void shouldDeactivateGroup() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrCreateUser(testUserBase + "1");
        Group group = groupRepository.save(new Group(testGroupBase + "1", user));
        Group group2 = groupRepository.save(new Group(testGroupBase + "2", user));
       // groupBroker.deactivate(user.getUid(), group.getUid(), true);


        // todo: do a 'find by active' and count here instead
        Group groupFromDb = groupRepository.findOneByUid(group.getUid());
        assertNotNull(groupFromDb);
     //   assertFalse(groupFromDb.isActive());
    }

    // as above
//    @Test
//    @Rollback
//    public void shouldOnlyReturnCreatedGroups() {
//        assertThat(groupRepository.count(), is(0L));
//        User user1 = userManagementService.loadOrCreateUser(testUserBase + "1");
//        User user2 = userManagementService.loadOrCreateUser(testUserBase + "2");
//        MembershipInfo member1 = new MembershipInfo(user1.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER,
//                                                    user1.getDisplayName());
//        MembershipInfo member1a = new MembershipInfo(user1.getPhoneNumber(), BaseRoles.ROLE_ORDINARY_MEMBER,
//                                                     user1.getDisplayName());
//        MembershipInfo member2 = new MembershipInfo(user2.getPhoneNumber(), BaseRoles.ROLE_ORDINARY_MEMBER,
//                                                    user2.getDisplayName());
//        MembershipInfo member2a = new MembershipInfo(user2.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER,
//                                                     user2.getDisplayName());
//
//        Group group1 = groupBroker.create(user1.getUid(), testGroupBase + "1", null, Sets.newHashSet(member1, member2), DEFAULT_GROUP, null, null, false);
//        Group group2 = groupBroker.create(user2.getUid(), testGroupBase + "2", null, Sets.newHashSet(member2a, member1a), DEFAULT_GROUP, null, null, false);
//
//        groupBroker.addMembers(user2.getUid(), group2.getUid(), Sets.newHashSet(member1), ADDED_BY_OTHER_MEMBER, false);
//        assertTrue(group2.getMembers().contains(user1));
//        Set<Group> list1 = permissionBroker.getActiveGroupsWithPermission(user1, null);
//        Set<Group> list2 = permissionBroker.getActiveGroupsWithPermission(user1, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
//        assertNotEquals(list1, list2);
//        assertThat(list1.size(), is(2));
//        assertThat(list2.size(), is(1));
//    }

    /*@Test
    @Rollback
    public void shouldMergeGroups() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrCreateUser(testUserBase + "2");

        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3", testUserBase + "4", testUserBase + "5"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "6", testUserBase + "7"), false);

        assertThat(group1.getMembers().size(), is(4));
        assertTrue(group1.isActive());
        assertThat(group2.getMembers().size(), is(3));

        groupManagementService.merge(group1, group2, user.getId());
        assertNotNull(group1);
        assertNotNull(group2);
        assertTrue(group1.isActive());
        assertFalse(group2.isActive());
        assertThat(group1.getMembers().size(), is(6));
        assertTrue(group1.getMembers().contains(userManagementService.findByInputNumber(testUserBase + "6")));
    }*/

    /* @Test
    @Rollback
    public void shouldNotFindInactiveGroups() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrCreateUser(testUserBase + "3");

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
    }*/

    /* @Test
    @Rollback
    public void mergeSpecificOrderShouldWorkForSmallerGroup() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrCreateUser(testUserBase + "0");

        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3", testUserBase + "4", testUserBase + "5"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "6", testUserBase + "7"), false);

        groupManagementService.mergeGroupsSpecifyOrder(group2, group1, true, user.getId());

        assertNotNull(group1);
        assertNotNull(group2);
        assertFalse(group1.isActive());
        assertTrue(group2.isActive());
        assertThat(group2.getMembers().size(), is(6));
        assertTrue(group2.getMembers().contains(userManagementService.findByInputNumber(testUserBase + "3")));
    }*/

    /* @Test
    @Rollback
    public void groupMergeShouldLeaveActiveIfFlagged() {
        assertThat(groupRepository.count(), is(0L));
        User user = userManagementService.loadOrCreateUser(testUserBase + "1");
        Group group1 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "2"), false);
        Group group2 = groupManagementService.createNewGroup(user, Arrays.asList(testUserBase + "3"), false);
        groupManagementService.merge(group1, group2, false, user.getId());
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
        User user = userManagementService.loadOrCreateUser(testUserBase + "1");
        User user2 = userManagementService.loadOrCreateUser(testUserBase + "2");

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
    }*/

/*    @Test
    public void shouldCreateSubGroup() {

        User userProfile = userManagementService.createUserProfile(new User("111111111", "aap1"));

        Group level1 = groupManagementService.createNewGroup(userProfile, Arrays.asList("111111112", "111111113"), false);
        Group level2 = groupManagementService.createSubGroup(userProfile, level1, "level2 group");
        assertEquals(level2.getParent().getId(), level1.getId());
    }*/

    //@Test
    /* public void shouldReturnGroupAndSubGroups() {

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
    }*/


}
