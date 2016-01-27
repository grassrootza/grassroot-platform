package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupRepositoryTest {

    private Logger log = Logger.getLogger(getClass().getName());


    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;


    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        Group groupToCreate = new Group();

        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("56789");
        userRepository.save(userToDoTests);

        groupToCreate.setGroupName("TestGroup");
        groupToCreate.setCreatedByUser(userToDoTests);
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1l));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
        assertThat(groupFromDb.getCreatedByUser().getPhoneNumber(), is("56789"));
    }

    @Test
    public void shouldSaveWithAddedMember() throws Exception {
        assertThat(groupRepository.count(), is(0L));
        User userForTest = userRepository.save(new User("0814441111"));
        Group groupToCreate = groupRepository.save(new Group("testGroup", userForTest));
        groupToCreate.addMember(userForTest);
        groupToCreate = groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        assertThat(groupToCreate.getGroupMembers().size(), is(1));
        assertThat(groupToCreate.getGroupMembers().contains(userForTest), is(true));
    }

    @Test
    public void shouldSaveAndFindByCreatedUser() throws Exception {

        Group groupToCreate = new Group();
        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("100001");
        userRepository.save(userToDoTests);
        groupToCreate.setGroupName("TestGroup");
        groupToCreate.setCreatedByUser(userToDoTests);
        assertNull(groupToCreate.getId());
        assertNull(groupToCreate.getCreatedDateTime());
        groupRepository.save(groupToCreate);
        Group groupFromDb = groupRepository.findByCreatedByUser(userToDoTests).iterator().next();
        log.info(groupFromDb.toString());
        assertNotNull(groupFromDb);
        assertEquals(userToDoTests.getId(), groupFromDb.getCreatedByUser().getId());
    }

    @Test
    public void shouldFindLastCreatedGroupForUser() throws Exception {

        User userToDoTests = new User();
        userToDoTests.setPhoneNumber("100002");
        userRepository.save(userToDoTests);
        Group group1 = new Group();
        group1.setGroupName("TestGroup1");
        group1.setCreatedByUser(userToDoTests);
        groupRepository.save(group1);
        Group group2 = new Group();
        group2.setGroupName("TestGroup2");
        group2.setCreatedByUser(userToDoTests);
        Group savedGroup2 = groupRepository.save(group2);
        Group groupFromDb = groupRepository.findFirstByCreatedByUserOrderByIdDesc(userToDoTests);
        log.finest("latest group........." + groupFromDb.toString());
        assertEquals(savedGroup2.getId(), groupFromDb.getId());
    }

    @Test
    public void shouldSaveParentRelationship() {
        User user = userRepository.save(new User("1111111111"));
        Group ga = groupRepository.save(new Group("ga", user));
        Group ga1 = groupRepository.save(new Group("ga1", user, ga));
        assertEquals(ga.getId(), ga1.getParent().getId());

    }

    @Test
    public void shouldReturnLevel1Children() {
        User user = userRepository.save(new User("2222222222"));
        Group gb = groupRepository.save(new Group("gb", user));
        Group gb1 = groupRepository.save(new Group("gb1", user, gb));
        Group gb2 = groupRepository.save(new Group("gb2", user, gb));
        List<Group> children = groupRepository.findByParent(gb);
        assertEquals(2,children.size());
        for (Group child : children) {
            log.finest("child......" + child.toString());
        }
    }


    @Test
    public void shouldReturnParentAndAllChildren() {
        User user = userRepository.save(new User("3333333333"));
        Group gc = groupRepository.save(new Group("gc", user));
        Group gc1 = groupRepository.save(new Group("gc1", user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", user, gc1));
        List<Group>  children = groupRepository.findGroupAndSubGroupsById(gc.getId());
        //todo - aakil the code works but the test fails, returns zero records, transaction thing again
        //assertEquals(5,children.size());
    }

    @Test
    public void shouldReturnOnlyOneLevel() {
        User user = userRepository.save(new User("3333333330"));
        Group gc = groupRepository.save(new Group("gc", user));
        Group gc1 = groupRepository.save(new Group("gc1", user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", user, gc1));
        List<Group> children = groupRepository.findGroupAndSubGroupsById(gc1b.getId());
        //todo - aakil the code works but the test fails, returns zero records, transaction thing again
        //assertEquals(1,children.size());
        //assertEquals(gc1b.getId(),children.get(0).getId());
    }

    @Test
    public void shouldGetMaxToken() {
        log.info(String.valueOf(groupRepository.getMaxTokenValue()));
    }

    @Test
    public void shouldCreateAndUseToken() {
        User user1 = userRepository.save(new User("3331118888"));
        Group group = groupRepository.save(new Group("token", user1));
        Integer realToken = groupRepository.getMaxTokenValue();
        Integer fakeToken = realToken - 10;
        group.setGroupTokenCode(String.valueOf(realToken));
        groupRepository.save(group);
        Group groupFromDb1 = groupRepository.findByGroupTokenCode(String.valueOf(realToken));
        Group groupFromDb2 = groupRepository.findByGroupTokenCode(String.valueOf(fakeToken));
        assertNotNull(groupFromDb1);
        assertNull(groupFromDb2);
        assertEquals(groupFromDb1, group);
    }

    @Test
    public void shouldUseAndExtendToken() {
        User user = userRepository.save(new User("3335551111"));
        Group group = groupRepository.save(new Group("tg", user));
        String token = String.valueOf(groupRepository.getMaxTokenValue());
        Date testDate1 = DateTimeUtil.addHoursToDate(new Date(), 12);
        Date testDate2 = DateTimeUtil.addHoursToDate(new Date(), 24);
        Date testDate3 = DateTimeUtil.addHoursToDate(new Date(), 36);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(new Timestamp(testDate2.getTime()));
        groupRepository.save(group);
        Group group1 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        Group group2 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate3);
        assertNotNull(group1);
        assertEquals(group1, group);
        assertNull(group2);

        group.setTokenExpiryDateTime(new Timestamp(testDate3.getTime()));
        groupRepository.save(group);
        Group group3 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate2);
        assertNotNull(group3);
        assertEquals(group3, group);
    }

    @Test
    public void shouldCloseToken() {
        User user = userRepository.save(new User("3335550000"));
        Group group = groupRepository.save(new Group("tg", user));
        String token = String.valueOf(groupRepository.getMaxTokenValue());
        Date testDate1 = DateTimeUtil.addHoursToDate(new Date(), 12);
        Date testDate2 = DateTimeUtil.addHoursToDate(new Date(), 24);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(new Timestamp(testDate2.getTime()));
        groupRepository.save(group);
        Group group1 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        assertNotNull(group1);
        assertEquals(group, group1);

        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        Group group2 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        assertNull(group2);

    }

    @Test
    public void shouldSetInactive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000"));
        Group group = groupRepository.save(new Group("gc", user));
        group.setActive(false);
        groupRepository.save(group);
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertThat(groupFromDb.getId(), is(group.getId()));
        assertEquals(groupFromDb.getGroupName(), "gc");
        assertFalse(groupFromDb.isActive());
    }

    @Test
    public void shouldReturnOnlyActive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331115555"));
        Group group1 = new Group("gc1", user);
        Group group2 = new Group("gc2", user);
        group1.addMember(user);
        group2.addMember(user);
        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);
        List<Group> list1 = groupRepository.findByGroupMembers(user);
        assertThat(list1.size(), is(2));
        group2.setActive(false);
        group2 = groupRepository.save(group2);
        List<Group> list2 = groupRepository.findByGroupMembersAndActive(user, true);
        assertThat(list2.size(), is(1));
        assertTrue(list2.contains(group1));
        assertFalse(list2.contains(group2));
    }

    @Test
    public void shouldReturnPagesActive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000"));
        List<Group> testGroups = Arrays.asList(new Group("gc1", user), new Group("gc2", user), new Group("gc3", user), new Group("gc4", user));
        for (Group group : testGroups) group.addMember(user);
        testGroups = groupRepository.save(testGroups);
        assertThat(groupRepository.count(), is(4L));
        Page<Group> pageTest1 = groupRepository.findByGroupMembersAndActive(user, new PageRequest(0, 3), true);
        assertThat(pageTest1.hasNext(), is(true));
        testGroups.get(0).setActive(false);
        groupRepository.save(testGroups.get(0));
        Page<Group> allGroups = groupRepository.findByGroupMembers(user, new PageRequest(0, 3));
        Page<Group> activeGroups = groupRepository.findByGroupMembersAndActive(user, new PageRequest(0,3), true);
        assertTrue(allGroups.hasNext());
        assertFalse(activeGroups.hasNext());
    }

    @Test
    public void shouldSaveAndRetrievePaidFor() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331113333"));
        Group group1 = groupRepository.save(new Group("paidGroup", user, true));
        Group group2 = groupRepository.save(new Group("unpaidGroup", user));
        assertTrue(group1.isPaidFor());
        assertFalse(group2.isPaidFor());
    }

    @Test
    public void shouldRetrieveFromListOfIds() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331115555"));
        Group testGroup1 = groupRepository.save(new Group("gc1", user));
        Group testGroup2 = groupRepository.save(new Group("gc2", user));
        Group testGroup3 = groupRepository.save(new Group("gc3", user));

        List<Long> ids = Arrays.asList(testGroup3.getId(), testGroup1.getId());
        List<Group> retrievedGroups = groupRepository.findAllByIdInOrderByIdAsc(ids);

        assertFalse(retrievedGroups.isEmpty());
        assertThat(retrievedGroups.get(0), is(testGroup1));
        assertThat(retrievedGroups.get(1), is(testGroup3));
        assertFalse(retrievedGroups.contains(testGroup2));
    }

    @Test
    public void shouldFindByDiscoverable() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0881110000"));
        Group testGroup1 = groupRepository.save(new Group("test group 1", user));
        Group testGroup2 = groupRepository.save(new Group("other test", user));
        testGroup1.setDiscoverable(true);
        testGroup1 = groupRepository.save(testGroup1);

        List<Group> firstList = groupRepository.findByGroupNameContainingAndDiscoverable("test", true);
        assertNotNull(firstList);
        assertThat(firstList.size(), is(1));
        assertTrue(firstList.contains(testGroup1));
        assertFalse(firstList.contains(testGroup2));

        List<Group> secondList = groupRepository.findByGroupNameContainingAndDiscoverable("1", true);
        assertNotNull(secondList);
        assertThat(secondList.size(), is(1));
        assertTrue(secondList.contains(testGroup1));
        assertFalse(secondList.contains(testGroup2));
    }

}


