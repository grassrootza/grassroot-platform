package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupLogType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.GroupSpecifications.*;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GroupRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(GroupRepositoryTest.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Test
    public void shouldSaveUpComingEvents() {

        User user1 = userRepository.save(new User("3456", null, null));
        Group group1 = groupRepository.save(new Group("Test Group", user1));
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("new Meeting").setStartDateTime(Instant.now().plus(1L, ChronoUnit.DAYS)).setUser(user1).setParent(group1).setEventLocation("limpopo").createMeeting());

        assertThat(eventRepository.count(), is(1L));
        assertNotNull(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false));
        assertThat(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false).size()
                , is(1));
        assertTrue(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false).contains(newEvent));

        Group group2 = groupRepository.save(new Group("Test Group", user1));
        group2.getUpcomingEvents(Predicate.isEqual(newEvent), false);

        newEvent.setParent(group2);
        groupRepository.save(group2);
        eventRepository.save(newEvent);

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getUpcomingEvents(Predicate.isEqual(newEvent), false));
        assertThat(groupFromDb.getUpcomingEvents(Predicate.isEqual(newEvent), false).size()
                , is(1));

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getParent());
        assertTrue(eventFromDb.getParent().getUid().equals(group2.getUid()));
    }

    @Test
    public void shouldSaveTodoReminder() {
        User userToCreate = userRepository.save(new User("3456", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", userToCreate));
        Todo newTask = todoRepository.save(new Todo(userToCreate, groupToCreate, TodoType.ACTION_REQUIRED,
                "discuss way forward", Instant.now().plus(1, ChronoUnit.DAYS)));


        assertThat(todoRepository.count(), is(1L));
        assertNotNull(groupToCreate.getDescendantTodos());
        assertThat(groupToCreate.getDescendantTodos().size(), is(1));
        assertTrue(groupToCreate.getDescendantTodos().contains(newTask));

        Group groupToCreate1 = groupRepository.save(new Group("Test Group", userToCreate));
        groupToCreate1.addDescendantTodo(newTask);
        newTask.setParent(groupToCreate1);
        groupRepository.save(groupToCreate1);
        todoRepository.save(newTask);

        Group groupFromDb = groupRepository.findOneByUid(groupToCreate1.getUid());
        assertNotNull(groupFromDb.getDescendantTodos());
        assertThat(groupFromDb.getDescendantTodos().size(), is(1));


        Todo todoFromDb = todoRepository.findOneByUid(newTask.getUid());
        assertNotNull(todoFromDb.getParent());
        assertTrue(todoFromDb.getParent().getUid().equals(groupToCreate1.getUid()));
    }


    @Test
    public void shouldSaveDefaultImage() {
        User userToCreate = userRepository.save(new User("12345", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", userToCreate));

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        groupToCreate.setDefaultImage(GroupDefaultImage.SOCIAL_MOVEMENT);
        assertTrue(groupToCreate.getDefaultImage().equals(GroupDefaultImage.SOCIAL_MOVEMENT));

        Group groupFromDB = groupRepository.findOneByUid(groupToCreate.getUid());
        assertNotNull(groupFromDB);
        assertTrue(groupToCreate.getDefaultImage().equals(GroupDefaultImage.SOCIAL_MOVEMENT));

    }

    @Test
    public void ShouldSaveImageUrl() {

        User userToCreate = userRepository.save(new User("12345", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", userToCreate));
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        groupToCreate.setImageUrl("http");
        assertTrue(groupToCreate.getImageUrl().equals("http"));
        Group groupFromDb = groupRepository.findOne(where(hasImageUrl("http")));
        assertNotNull(groupFromDb);
        assertTrue(groupToCreate.getImageUrl().equals("http"));
    }

    @Test
    public void shouldAddRole() throws Exception {

        assertThat(groupRepository.count(), is(0L));
        User userRole = new User("56789", null, null);
        userRepository.save(userRole);

        User userRole1 = new User("56780", null, null);
        userRepository.save(userRole1);

        User userRole2 = new User("56788", null, null);
        userRepository.save(userRole2);

        Group groupToCreate = new Group("TestGroup", userRole);
        groupToCreate.addMember(userRole, BaseRoles.ROLE_COMMITTEE_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate.addMember(userRole1, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate.addMember(userRole2, BaseRoles.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        assertFalse(groupToCreate.getGroupRoles().isEmpty());
        assertThat(groupToCreate.getGroupRoles().size(), is(3));

        Membership membership = groupToCreate.getMembership(userRole);
        log.info("first membership: {}", membership);

        assertTrue(groupToCreate.getMembership(userRole).getRole().getName().equals(BaseRoles.ROLE_COMMITTEE_MEMBER));
        assertTrue(groupToCreate.getMembership(userRole1).getRole().getName().equals(BaseRoles.ROLE_ORDINARY_MEMBER));
        assertTrue(groupToCreate.getMembership(userRole2).getRole().getName().equals(BaseRoles.ROLE_GROUP_ORGANIZER));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertFalse(groupFromDb.getGroupRoles().isEmpty());
        assertThat(groupFromDb.getGroupRoles().size(),is(3));

    }

    @Test
    public void shouldAddMembers() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        User userAdd = new User("56789", null, null);
        userRepository.save(userAdd);

        User userAdd1 = new User("56788", null, null);
        userRepository.save(userAdd1);

        List<User> usersToAdd = Arrays.asList(userAdd, userAdd1);

        Group groupToAdd = new Group("TestGroup", userAdd);
        groupToAdd.addMembers(usersToAdd, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToAdd);

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToAdd);
        assertTrue(groupToAdd.hasMember(userAdd));
        assertTrue(groupToAdd.hasMember(userAdd1));
        assertThat(groupToAdd.getMembers().size(), is(2));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertThat(groupFromDb.getMembers().size(),is(2));
        assertTrue(groupFromDb.hasMember(userAdd));
        assertTrue(groupFromDb.hasMember(userAdd1));
    }

    @Test
    public  void shouldCollectGroupMembers() throws Exception {
        assertThat(groupRepository.count(),is(0L));

        User userToRetrieve = new User("56789", null, null);
        userRepository.save(userToRetrieve);

        User userToRetrieve1 = new User("45678", null, null);
        userRepository.save(userToRetrieve1);

        List<User> usersToCollect = Arrays.asList(userToRetrieve, userToRetrieve1);

        Group groupToCollect = new Group("TestGroup",userToRetrieve);
        groupToCollect.addMembers(usersToCollect, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCollect);

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(groupToCollect);
        assertTrue(groupToCollect.hasMember(userToRetrieve));
        assertTrue(groupToCollect.hasMember(userToRetrieve1));
        assertThat(groupToCollect.getMemberships().size(),is(2));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertTrue(groupFromDb.hasMember(userToRetrieve));
        assertTrue(groupFromDb.hasMember(userToRetrieve1));
        assertTrue(groupFromDb.getMembership(userToRetrieve).getUser().getPhoneNumber().equals("56789"));
        assertTrue(groupFromDb.getMembership(userToRetrieve1).getUser().getPhoneNumber().equals("45678"));
        assertThat(groupFromDb.getMemberships().size(),is(2));

    }

    @Test
    public void removeMemberships() throws Exception {
        assertThat(groupRepository.count(),is(0L));

        User userToRemove = new User("56789", null, null);
        userRepository.save(userToRemove);

        User userToRemove1 = new User("56788", null, null);
        userRepository.save(userToRemove1);
        List<User> userNumber = Arrays.asList(userToRemove,userToRemove1);

        Group groupToCreate = new Group("Test Group",userToRemove);
        groupToCreate.addMembers(userNumber, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(),is(1L));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupToCreate);
        groupFromDb.removeMember(userToRemove);
        groupFromDb.removeMember(userToRemove1);
        groupRepository.save(groupFromDb);

        Group groupFromDb2 = groupRepository.findOne(groupFromDb.getId());
        assertNotNull(groupFromDb2);
        assertTrue(groupFromDb2.getMembers().isEmpty());
        assertThat(groupFromDb2.getMembers().size(),is(0));
    }

    @Test
    public void shouldAddChildGroup() throws Exception {
        assertThat(groupRepository.count(),is(0L));
        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);
        Group groupToAddParent = new Group("TestGroup",userToCreate);
        groupRepository.save(groupToAddParent);

        Group groupToAddChild = new Group("TestGroup1",userToCreate,groupToAddParent);
        groupRepository.save(groupToAddChild);

        assertThat(groupRepository.count(),is(2L));
        assertNotNull(groupToAddParent);

        groupToAddChild.setParent(groupToAddParent);
        groupToAddParent.addChildGroup(groupToAddChild);
        assertNotNull(groupToAddParent.getDirectChildren());
        groupRepository.save(groupToAddParent);

        Group groupFromDbParent = groupRepository.findOneByUid(groupToAddParent.getUid());
        assertNotNull(groupFromDbParent);
        groupFromDbParent.getDirectChildren();
        assertThat(groupFromDbParent.getDirectChildren().size(),is(1));

        Group groupFromDbChild = groupRepository.findOneByUid(groupToAddChild.getUid());
        assertNotNull(groupFromDbChild);
        assertThat(groupFromDbChild.getParent().getDirectChildren().size(),is(1));

    }

    @Test
    public void shouldBeDiscoverable() throws  Exception {

       assertThat(groupRepository.count(),is(0L));
       User userToDiscover = new User("56789", null, null);
       userRepository.save(userToDiscover);
       Group groupToDiscover = new Group("TestGroup",userToDiscover);
       groupRepository.save(groupToDiscover);

       assertThat(groupRepository.count(),is(1L));

       groupToDiscover.setGroupName("TestGroup");
       assertTrue(groupToDiscover.getGroupName().equals("TestGroup"));
       assertTrue(groupToDiscover.isDiscoverable());
       groupRepository.save(groupToDiscover);

       Group groupFromDb = groupRepository.findAll().iterator().next();
       assertNotNull(groupFromDb);
       groupFromDb.setGroupName("TestGroup");
       assertTrue(groupFromDb.getGroupName().equals("TestGroup"));
       assertTrue(groupFromDb.isDiscoverable());

    }


    @Test
    public void shouldSaveDefaultLanguage() throws Exception{
        assertThat(groupRepository.count(),is(0L));
        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);

        Group groupDefaultLanguage = new Group("TestGroup",userToCreate);
        groupRepository.save(groupDefaultLanguage);

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(groupDefaultLanguage);
        groupDefaultLanguage.setDefaultLanguage("EN");
        groupRepository.save(groupDefaultLanguage);
    }


    @Test
    public void shouldSaveGroupReminder() throws  Exception {
        assertThat(groupRepository.count(),is(0L));

        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);

        Group groupToValidate = new Group("Test",userToCreate);
        groupRepository.save(groupToValidate);

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(groupToValidate);
        groupToValidate.setReminderMinutes(48*60);
        assertThat(groupToValidate.getReminderMinutes(),is(48*60));
        groupRepository.save(groupToValidate);

        Group groupFromDb = groupRepository.findOneByUid(groupToValidate.getUid());
        assertNotNull(groupFromDb);
        assertThat(groupFromDb.getReminderMinutes(),is(48*60));

    }

    @Test
    public void shouldSaveGroupDescription() throws  Exception {
        User userToCreate = userRepository.save(new User("56789", null, null));
        Group testGroup = groupRepository.save(new Group("testGroup",userToCreate));

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(testGroup);
        testGroup.setDescription("Group");
        assertTrue(testGroup.getDescription().equals("Group"));
        groupRepository.save(testGroup);

        Group groupFromDB = groupRepository.findOneByUid(testGroup.getUid());
        assertNotNull(groupFromDB.getUid());
        assertTrue(groupFromDB.getDescription().equals("Group"));

    }

    @Test
    public void shouldSaveChildEvents() {

        User newUser = userRepository.save(new User("12345", null, null));
        Group testGroup = groupRepository.save(new Group("testGroup", newUser));
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now().plus(1L, ChronoUnit.DAYS)).setUser(newUser).setParent(testGroup).setEventLocation("somewhere in soweto").createMeeting());

        assertThat(eventRepository.count(), is(1L));
        assertNotNull(testGroup.getEvents());
        assertThat(testGroup.getEvents().size(), is(1));
        assertTrue(testGroup.getEvents().contains(newEvent));

        Group testGroup2 = groupRepository.save(new Group("testGroup2", newUser));
        testGroup2.addChildEvent(newEvent);
        newEvent.setParent(testGroup2);
        groupRepository.save(testGroup2);
        eventRepository.save(newEvent);

        Group testGroup2FromDb = groupRepository.findOneByUid(testGroup2.getUid());
        assertNotNull(testGroup2FromDb.getEvents());
        assertThat(testGroup.getEvents().size(), is(1));

        Event eventBackFromDb = eventRepository.findOneByUid(newEvent.getUid());
        assertNotNull(eventBackFromDb.getParent());
        assertTrue(eventBackFromDb.getParent().getUid().equals(testGroup2.getUid()));

    }

    @Test
    public void shouldAddDescendantEvents() {

        User userToCreate = userRepository.save(new User("56789", null, null));
        Group testGroup = groupRepository.save(new Group("testGroup",userToCreate));
        Event createEvent = eventRepository.save(new MeetingBuilder().setName("new Event").setStartDateTime(Instant.now().plus(
                1L, ChronoUnit.DAYS)).setUser(userToCreate).setParent(testGroup).setEventLocation("limpopo").createMeeting());

        assertThat(eventRepository.count(),is(1L));
        assertNotNull(testGroup.getDescendantEvents());
        assertThat(testGroup.getDescendantEvents().size(),is(1));
        assertTrue(testGroup.getDescendantEvents().contains(createEvent));

        Group testGroup1 = groupRepository.save(new Group("testGroup1",userToCreate));
        testGroup1.addDescendantEvent(createEvent);
        createEvent.setParent(testGroup1);
        //testGroup1.addChildGroup(testGroup);
        groupRepository.save(testGroup1);
        eventRepository.save(createEvent);

        Group groupFromDB = groupRepository.findOneByUid(testGroup1.getUid());
        assertNotNull(groupFromDB.getDescendantEvents());
        assertThat(testGroup.getDescendantEvents().size(),is(1));

        Event eventFromDB = eventRepository.findOneByUid(createEvent.getUid());
        assertNotNull(eventFromDB.getParent());
        assertTrue(createEvent.getParent().getUid().equals(testGroup1.getUid()));

    }

    @Test
    public void shouldSaveJoinApprove() throws Exception {
        User newUser = userRepository.save(new User("56789", null, null));
        Group newGroup =groupRepository.save(new Group("Test Group",newUser));

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(newGroup);
       newGroup.setJoinApprover(newUser);
       assertTrue(newGroup.getJoinApprover().equals(newUser));

       Group groupFromDb = groupRepository.findOneByUid(newGroup.getUid());
       assertNotNull(groupFromDb.getUid());
       assertTrue(groupFromDb.getJoinApprover().equals(newUser));

    }
    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        User userToDoTests = new User("56789", null, null);
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", userToDoTests);
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
        User userForTest = userRepository.save(new User("0814441111", null, null));
        Group groupToCreate = groupRepository.save(new Group("testGroup", userForTest));
        groupToCreate.addMember(userForTest, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate = groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        assertThat(groupToCreate.getMemberships().size(), is(1));
        assertThat(groupToCreate.getMembers().contains(userForTest), is(true));
    }

    @Test
    public void shouldSaveAndFindByCreatedUser() throws Exception {

        User userToDoTests = new User("100001", null, null);
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", userToDoTests);
        assertNull(groupToCreate.getId());
        assertNotNull(groupToCreate.getUid());
        groupRepository.save(groupToCreate);
        // Group groupFromDb = groupRepository.findByCreatedByUser(userToDoTests).iterator().next();
        // log.info(groupFromDb.toString());
        // assertNotNull(groupFromDb);
        // assertEquals(userToDoTests.getId(), groupFromDb.getCreatedByUser().getId());
    }

    @Test
    public void shouldFindLastCreatedGroupForUser() throws Exception {

        User userToDoTests = new User("100002", null, null);
        userRepository.save(userToDoTests);

        Group group1 = new Group("TestGroup1", userToDoTests);
        groupRepository.save(group1);

        Group group2 = new Group("TestGroup2", userToDoTests);
        Group savedGroup2 = groupRepository.save(group2);
        Group groupFromDb = groupRepository.findFirstByCreatedByUserAndActiveTrueOrderByIdDesc(userToDoTests);
        log.debug("latest group........." + groupFromDb.toString());
        assertEquals(savedGroup2.getId(), groupFromDb.getId());
    }

    @Test
    public void shouldSaveParentRelationship() {
        User user = userRepository.save(new User("1111111111", null, null));
        Group ga = groupRepository.save(new Group("ga", user));
        Group ga1 = groupRepository.save(new Group("ga1", user, ga));
        assertEquals(ga.getId(), ga1.getParent().getId());

    }

    @Test
    public void shouldReturnLevel1ChildrenByRepositoryQuery() {
        User user = userRepository.save(new User("2222222222", null, null));
        Group gb = groupRepository.save(new Group("gb", user));
        Group gb1 = groupRepository.save(new Group("gb1", user, gb));
        Group gb2 = groupRepository.save(new Group("gb2", user, gb));
        List<Group> children = groupRepository.findAll(where(
                hasParent(gb)).and(isActive()));;
        assertEquals(2,children.size());
    }

    @Test
    public void shouldReturnByEntityGet() {
        User user = userRepository.save(new User("3333333330", null, null));
        Group gc = groupRepository.save(new Group("gc", user));
        Group gc1 = groupRepository.save(new Group("gc1", user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", user, gc1));

        Group gcUpdated = groupRepository.findOne(gc.getId());
        Group gc1Updated = groupRepository.findOne(gc1.getId());
        Group gc2Updated = groupRepository.findOne(gc2.getId());

        assertNotNull(gcUpdated);
        assertNotNull(gcUpdated.getDirectChildren());
        assertEquals(2, gcUpdated.getDirectChildren().size());
        assertTrue(gcUpdated.getDirectChildren().contains(gc1));
        assertTrue(gcUpdated.getDirectChildren().contains(gc2));
        assertFalse(gcUpdated.getDirectChildren().contains(gc1a));
        assertFalse(gcUpdated.getDirectChildren().contains(gc1b));
        assertEquals(2, gc1Updated.getDirectChildren().size());
        assertEquals(0, gc2Updated.getDirectChildren().size());
    }

    private String genToken() {
        int maxCodeInt = (int) Math.pow(10, 4);
        int rndValue = new Random().nextInt(maxCodeInt);
        return String.format("%04d", rndValue);
    }

    @Test
    public void shouldCreateAndUseToken() {
        User user1 = userRepository.save(new User("3331118888", null, null));
        Group group = groupRepository.save(new Group("token", user1));
        String realToken = genToken();
        Integer fakeToken = Integer.parseInt(realToken) - 10;
        group.setGroupTokenCode(realToken);
        groupRepository.save(group);
        Group groupFromDb1 = groupRepository.findOne(hasJoinCode(String.valueOf(realToken)));
        Group groupFromDb2 = groupRepository.findOne(hasJoinCode(String.valueOf(fakeToken)));
        assertNotNull(groupFromDb1);
        assertNull(groupFromDb2);
        assertEquals(groupFromDb1, group);
    }

    @Test
    public void shouldUseAndExtendToken() {
        User user = userRepository.save(new User("3335551111", null, null));
        Group group = groupRepository.save(new Group("tg", user));
        String token = genToken();
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);
        Instant testDate3 = Instant.now().plus(36L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Group group1 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1)));
        Group group2 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate3)));
        assertNotNull(group1);
        assertEquals(group1, group);
        assertNull(group2);

        group.setTokenExpiryDateTime(testDate3);
        groupRepository.save(group);
        Group group3 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate2)));
        assertNotNull(group3);
        assertEquals(group3, group);
    }

    @Test
    public void shouldCloseToken() {
        User user = userRepository.save(new User("3335550000", null, null));
        Group group = groupRepository.save(new Group("tg", user));
        String token = genToken();
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Group group1 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1)));
        assertNotNull(group1);
        assertEquals(group, group1);

        group.setTokenExpiryDateTime(Instant.now());
        Group group2 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1)));
        assertNull(group2);

    }

    @Test
    public void shouldSetInactive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000", null, null));
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
        User user = userRepository.save(new User("3331115555", null, null));
        Group group1 = new Group("gc1", user);
        Group group2 = new Group("gc2", user);
        group1.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group2.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);
        group2.setActive(false);
        group2 = groupRepository.save(group2);
        List<Group> list2 = groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user);
        assertThat(list2.size(), is(1));
        assertTrue(list2.contains(group1));
        assertFalse(list2.contains(group2));
    }

    @Test
    public void shouldReturnPagesCreatedBy() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000", null, null));
        List<Group> testGroups = Arrays.asList(new Group("gc1", user), new Group("gc2", user), new Group("gc3", user), new Group("gc4", user));
        for (Group group : testGroups)
            group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        testGroups = groupRepository.save(testGroups);
        assertThat(groupRepository.count(), is(4L));
        Page<Group> pageTest1 = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user, new PageRequest(0, 3));
        assertThat(pageTest1.hasNext(), is(true));
        testGroups.get(0).setActive(false);
        groupRepository.save(testGroups.get(0));
        Page<Group> activeGroups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user, new PageRequest(0,3));
        assertFalse(activeGroups.hasNext());
    }

    @Test
    public void shouldHaveValidGroupToken() throws Exception {

        User user = userRepository.save(new User("4879342", null, null));
        Group groupToken = groupRepository.save(new Group(
                "Token",user));
        assertNotNull(groupToken.getUid());

        Instant tokenTime = Instant.now().plus(10,
                ChronoUnit.DAYS);

        groupToken.setTokenExpiryDateTime(tokenTime);
        groupToken.setGroupTokenCode("1234");
        assertTrue(groupToken.hasValidGroupTokenCode());
        assertThat(groupToken.getTokenExpiryDateTime(),
                is(tokenTime));
        assertThat(groupToken.getGroupTokenCode(),is("1234"));

        Group groupDb = groupRepository.findAll().iterator().next();
        assertThat(groupDb.getTokenExpiryDateTime(),is(tokenTime));

        Group group = groupRepository.findAll().iterator().next();
        Instant expiredTime = Instant.now().minus(9,ChronoUnit.DAYS);
        group.setGroupTokenCode(null);
        group.setTokenExpiryDateTime(expiredTime);
        groupRepository.save(group);

        Group newGroupDb = groupRepository.findAll().iterator().next();
        assertThat(newGroupDb.getGroupTokenCode(),is(nullValue()));
        assertThat(newGroupDb.getTokenExpiryDateTime(),is(expiredTime));

    }


    @Test
    public void shouldSaveAndRetrievePaidFor() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331113333", null, null));
        Group paidGroup = new Group("paidGroup", user);
        paidGroup.setPaidFor(true);
        Group group1 = groupRepository.save(paidGroup);
        Group group2 = groupRepository.save(new Group("unpaidGroup", user));
        assertTrue(group1.isPaidFor());
        assertFalse(group2.isPaidFor());
    }

    @Test
    public void shouldFindByPermission() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0801113456", null, null));
        Group group1 = new Group("group1", user);
        Group group2 = new Group("group2", user);

        group1.getRole(BaseRoles.ROLE_GROUP_ORGANIZER).addPermission(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        group1.getRole(BaseRoles.ROLE_GROUP_ORGANIZER).addPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        group2.getRole(BaseRoles.ROLE_ORDINARY_MEMBER).addPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

        group1.addMember(user, BaseRoles.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group2.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);

        groupRepository.save(group1);
        groupRepository.save(group2);

        assertEquals(1, groupRepository.countActiveGroupsWhereUserHasPermission(user, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        assertEquals(2, groupRepository.countActiveGroupsWhereUserHasPermission(user, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
    }

   /* @Test
    public void shouldFindByDiscoverable() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0881110000"));
        Group testGroup1 = groupRepository.save(new Group("test group 1", user));
        Group testGroup2 = groupRepository.save(new Group("other test", user));
        testGroup1.setDiscoverable(true);
        testGroup1 = groupRepository.save(testGroup1);

        List<Group> firstList = groupRepository.findDiscoverableGroupsWithNameOrTaskTextWithoutMember("test", true);
        assertNotNull(firstList);
        assertThat(firstList.size(), is(1));
        assertTrue(firstList.contains(testGroup1));
        assertFalse(firstList.contains(testGroup2));

        List<Group> secondList = groupRepository.findDiscoverableGroupsWithNameOrTaskTextWithoutMember("1", true);
        assertNotNull(secondList);
        assertThat(secondList.size(), is(1));
        assertTrue(secondList.contains(testGroup1));
        assertFalse(secondList.contains(testGroup2));
    }*/

    @Test
    public void shouldFindWhereGroupJoinUsed() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0801110000", null, null));
        User user2 = userRepository.save(new User("0801110001", null, null));
        Group tg1 = groupRepository.save(new Group("tg1", user));
        Group tg2 = groupRepository.save(new Group("tg2", user));
        groupLogRepository.save(new GroupLog(tg1, user2, GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
                                             user2, null, null, "test"));
        List<Group> groups = groupRepository.findGroupsWhereJoinCodeUsedBetween(Instant.now().minus(1, ChronoUnit.MINUTES),
                                                                                Instant.now());
        assertNotNull(groups);
        assertTrue(groups.contains(tg1));
        assertFalse(groups.contains(tg2));
    }

    @Test
    public void tempTestCountSizeMembers() {
        assertThat(groupRepository.count(), is(0L));
        User user1 = new User("56789", null, null);
        User user2 = new User("12345", null, null);
        Group group1 = new Group("test", user1);
        group1.addMember(user1, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group1.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group1);
        assertThat(groupRepository.count(), is(1L));

        List<Group> groups1 = groupRepository.findBySizeAbove(3);
        assertTrue(groups1.isEmpty());

        List<Group> groups2 = groupRepository.findBySizeAbove(1);
        assertFalse(groups2.isEmpty());
        assertTrue(groups2.contains(group1));

        List<Group> groups3 = groupRepository.findByTasksMoreThan(0);
        assertTrue(groups3.isEmpty());
    }

}


