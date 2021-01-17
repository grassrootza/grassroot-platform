package za.org.grassroot.core.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.*;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import static org.springframework.data.jpa.domain.Specification.where;
import static za.org.grassroot.core.specifications.GroupSpecifications.*;

/**
 * @author Lesetse Kimwaga
 */
@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
public class GroupRepositoryTest {

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
        Group group1 = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, user1));
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("new Meeting").setStartDateTime(Instant.now().plus(1L, ChronoUnit.DAYS)).setUser(user1).setParent(group1).setEventLocation("limpopo").createMeeting());

        assertThat(eventRepository.count(), is(1L));
        assertNotNull(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false));
        assertThat(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false).size()
                , is(1));
        assertTrue(group1.getUpcomingEvents(Predicate.isEqual(newEvent), false).contains(newEvent));

        Group group2 = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, user1));
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
        assertEquals(eventFromDb.getParent().getUid(), group2.getUid());
    }

    @Test
    public void shouldSaveTodoReminder() {
        User userToCreate = userRepository.save(new User("3456", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));
        Todo newTask = todoRepository.save(new Todo(userToCreate, groupToCreate, TodoType.ACTION_REQUIRED,
                "discuss way forward", Instant.now().plus(1, ChronoUnit.DAYS)));


        assertThat(todoRepository.count(), is(1L));
        assertNotNull(groupToCreate.getDescendantTodos());
        assertThat(groupToCreate.getDescendantTodos().size(), is(1));
        assertTrue(groupToCreate.getDescendantTodos().contains(newTask));

        Group groupToCreate1 = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));
        groupToCreate1.addDescendantTodo(newTask);
        newTask.setParent(groupToCreate1);
        groupRepository.save(groupToCreate1);
        todoRepository.save(newTask);

        Group groupFromDb = groupRepository.findOneByUid(groupToCreate1.getUid());
        assertNotNull(groupFromDb.getDescendantTodos());
        assertThat(groupFromDb.getDescendantTodos().size(), is(1));


        Todo todoFromDb = todoRepository.findOneByUid(newTask.getUid());
        assertNotNull(todoFromDb.getParent());
        assertEquals(todoFromDb.getParent().getUid(), groupToCreate1.getUid());
    }


    @Test
    public void shouldSaveDefaultImage() {
        User userToCreate = userRepository.save(new User("12345", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        groupToCreate.setDefaultImage(GroupDefaultImage.SOCIAL_MOVEMENT);
        assertEquals(groupToCreate.getDefaultImage(), GroupDefaultImage.SOCIAL_MOVEMENT);

        Group groupFromDB = groupRepository.findOneByUid(groupToCreate.getUid());
        assertNotNull(groupFromDB);
        assertEquals(groupToCreate.getDefaultImage(), GroupDefaultImage.SOCIAL_MOVEMENT);

    }

    @Test
    public void ShouldSaveImageUrl() {

        User userToCreate = userRepository.save(new User("12345", null, null));
        Group groupToCreate = groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        groupToCreate.setImageUrl("http");
        assertEquals("http", groupToCreate.getImageUrl());
        Group groupFromDb = groupRepository.findOne(where(hasImageUrl("http"))).get();
        assertNotNull(groupFromDb);
        assertEquals("http", groupToCreate.getImageUrl());
    }

    @Test
    public void shouldAddRole() {

        assertThat(groupRepository.count(), is(0L));
        User userRole = new User("56789", null, null);
        userRepository.save(userRole);

        User userRole1 = new User("56780", null, null);
        userRepository.save(userRole1);

        User userRole2 = new User("56788", null, null);
        userRepository.save(userRole2);

        Group groupToCreate = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userRole);
        groupToCreate.addMember(userRole, GroupRole.ROLE_COMMITTEE_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate.addMember(userRole1, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate.addMember(userRole2, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);

        Membership membership = userRole.getMembership(groupToCreate);
        log.info("first membership: {}", membership);

        assertEquals(userRole.getMembership(groupToCreate).getRole(), GroupRole.ROLE_COMMITTEE_MEMBER);
        assertEquals(userRole1.getMembership(groupToCreate).getRole(), GroupRole.ROLE_ORDINARY_MEMBER);
        assertEquals(userRole2.getMembership(groupToCreate).getRole(), GroupRole.ROLE_GROUP_ORGANIZER);

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
    }

    @Test
    public void shouldAddMembers() {

        assertThat(groupRepository.count(), is(0L));

        User userAdd = new User("56789", null, null);
        userRepository.save(userAdd);

        User userAdd1 = new User("56788", null, null);
        userRepository.save(userAdd1);

        List<User> usersToAdd = Arrays.asList(userAdd, userAdd1);

        Group groupToAdd = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userAdd);
        groupToAdd.addMembers(usersToAdd, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToAdd);

        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToAdd);
        assertTrue(userAdd.isMemberOf(groupToAdd));
        assertTrue(userAdd1.isMemberOf(groupToAdd));
        assertThat(groupToAdd.getMembers().size(), is(2));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertThat(groupFromDb.getMembers().size(),is(2));
        assertTrue(userAdd.isMemberOf(groupFromDb));
        assertTrue(userAdd1.isMemberOf(groupFromDb));
    }

    @Test
    public  void shouldCollectGroupMembers() {
        assertThat(groupRepository.count(),is(0L));

        User userToRetrieve = new User("56789", null, null);
        userRepository.save(userToRetrieve);

        User userToRetrieve1 = new User("45678", null, null);
        userRepository.save(userToRetrieve1);

        List<User> usersToCollect = Arrays.asList(userToRetrieve, userToRetrieve1);

        Group groupToCollect = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToRetrieve);
        groupToCollect.addMembers(usersToCollect, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCollect);

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(groupToCollect);
        assertTrue(userToRetrieve.isMemberOf(groupToCollect));
        assertTrue(userToRetrieve1.isMemberOf(groupToCollect));
        assertThat(groupToCollect.getMemberships().size(),is(2));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertTrue(userToRetrieve.isMemberOf(groupFromDb));
        assertTrue(userToRetrieve1.isMemberOf(groupFromDb));
        assertEquals("56789", userToRetrieve.getMembership(groupFromDb).getUser().getPhoneNumber());
        assertEquals("45678", userToRetrieve1.getMembership(groupFromDb).getUser().getPhoneNumber());
        assertThat(groupFromDb.getMemberships().size(),is(2));

    }

    @Test
    public void removeMemberships() {
        assertThat(groupRepository.count(),is(0L));

        User userToRemove = new User("56789", null, null);
        userRepository.save(userToRemove);

        User userToRemove1 = new User("56788", null, null);
        userRepository.save(userToRemove1);
        List<User> userNumber = Arrays.asList(userToRemove,userToRemove1);

        Group groupToCreate = new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, userToRemove);
        groupToCreate.addMembers(userNumber, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(),is(1L));

        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupToCreate);
        groupFromDb.removeMember(userToRemove);
        groupFromDb.removeMember(userToRemove1);
        groupRepository.save(groupFromDb);

        Group groupFromDb2 = groupRepository.findById(groupFromDb.getId()).get();
        assertNotNull(groupFromDb2);
        assertTrue(groupFromDb2.getMembers().isEmpty());
        assertThat(groupFromDb2.getMembers().size(),is(0));
    }

    @Test
    public void shouldAddChildGroup() throws Exception {
        assertThat(groupRepository.count(),is(0L));
        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);
        Group groupToAddParent = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate);
        groupRepository.save(groupToAddParent);

        Group groupToAddChild = new Group("TestGroup1", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate, groupToAddParent);
        groupRepository.save(groupToAddChild);

        assertThat(groupRepository.count(),is(2L));
        assertNotNull(groupToAddParent);

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
    public void shouldBeDiscoverable() {

       assertThat(groupRepository.count(),is(0L));
       User userToDiscover = new User("56789", null, null);
       userRepository.save(userToDiscover);
       Group groupToDiscover = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToDiscover);
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
    public void shouldSaveDefaultLanguage() {
        assertThat(groupRepository.count(),is(0L));
        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);

        Group groupDefaultLanguage = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate);
        groupRepository.save(groupDefaultLanguage);

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(groupDefaultLanguage);
        groupDefaultLanguage.setDefaultLanguage("EN");
        groupRepository.save(groupDefaultLanguage);
    }


    @Test
    public void shouldSaveGroupReminder() {
        assertThat(groupRepository.count(),is(0L));

        User userToCreate = new User("56789", null, null);
        userRepository.save(userToCreate);

        Group groupToValidate = new Group("Test", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate);
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
    public void shouldSaveGroupDescription() {
        User userToCreate = userRepository.save(new User("56789", null, null));
        Group testGroup = groupRepository.save(new Group("testGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));

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
        Group testGroup = groupRepository.save(new Group("testGroup", GroupPermissionTemplate.DEFAULT_GROUP, newUser));
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now().plus(1L, ChronoUnit.DAYS)).setUser(newUser).setParent(testGroup).setEventLocation("somewhere in soweto").createMeeting());

        assertThat(eventRepository.count(), is(1L));
        assertNotNull(testGroup.getEvents());
        assertThat(testGroup.getEvents().size(), is(1));
        assertTrue(testGroup.getEvents().contains(newEvent));

        Group testGroup2 = groupRepository.save(new Group("testGroup2", GroupPermissionTemplate.DEFAULT_GROUP, newUser));
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
        Group testGroup = groupRepository.save(new Group("testGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));
        Event createEvent = eventRepository.save(new MeetingBuilder().setName("new Event").setStartDateTime(Instant.now().plus(
                1L, ChronoUnit.DAYS)).setUser(userToCreate).setParent(testGroup).setEventLocation("limpopo").createMeeting());

        assertThat(eventRepository.count(),is(1L));
        assertNotNull(testGroup.getDescendantEvents());
        assertThat(testGroup.getDescendantEvents().size(),is(1));
        assertTrue(testGroup.getDescendantEvents().contains(createEvent));

        Group testGroup1 = groupRepository.save(new Group("testGroup1", GroupPermissionTemplate.DEFAULT_GROUP, userToCreate));
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
    public void shouldSaveJoinApprove() {
        User newUser = userRepository.save(new User("56789", null, null));
        Group newGroup =groupRepository.save(new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, newUser));

        assertThat(groupRepository.count(),is(1L));
        assertNotNull(newGroup);
       newGroup.setJoinApprover(newUser);
       assertTrue(newGroup.getJoinApprover().equals(newUser));

       Group groupFromDb = groupRepository.findOneByUid(newGroup.getUid());
       assertNotNull(groupFromDb.getUid());
       assertTrue(groupFromDb.getJoinApprover().equals(newUser));

    }
    @Test
    public void shouldSaveAndRetrieveGroupData() {

        assertThat(groupRepository.count(), is(0L));

        User userToDoTests = new User("56789", null, null);
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToDoTests);
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1L));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
        assertThat(groupFromDb.getCreatedByUser().getPhoneNumber(), is("56789"));
    }
    @Test
    public void shouldSaveWithAddedMember() {
        assertThat(groupRepository.count(), is(0L));
        User userForTest = userRepository.save(new User("0814441111", null, null));
        Group groupToCreate = groupRepository.save(new Group("testGroup", GroupPermissionTemplate.DEFAULT_GROUP, userForTest));
        groupToCreate.addMember(userForTest, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupToCreate = groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        assertThat(groupToCreate.getMemberships().size(), is(1));
        assertThat(groupToCreate.getMembers().contains(userForTest), is(true));
    }

    @Test
    public void shouldSaveAndFindByCreatedUser() {

        User userToDoTests = new User("100001", null, null);
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", GroupPermissionTemplate.DEFAULT_GROUP, userToDoTests);
        assertNull(groupToCreate.getId());
        assertNotNull(groupToCreate.getUid());
        groupRepository.save(groupToCreate);
        // Group groupFromDb = groupRepository.findByCreatedByUser(userToDoTests).iterator().next();
        // log.info(groupFromDb.toString());
        // assertNotNull(groupFromDb);
        // assertEquals(userToDoTests.getId(), groupFromDb.getCreatedByUser().getId());
    }

    @Test
    public void shouldFindLastCreatedGroupForUser() {

        User userToDoTests = new User("100002", null, null);
        userRepository.save(userToDoTests);

        Group group1 = new Group("TestGroup1", GroupPermissionTemplate.DEFAULT_GROUP, userToDoTests);
        groupRepository.save(group1);

        Group group2 = new Group("TestGroup2", GroupPermissionTemplate.DEFAULT_GROUP, userToDoTests);
        Group savedGroup2 = groupRepository.save(group2);
        Group groupFromDb = groupRepository.findFirstByCreatedByUserAndActiveTrueOrderByIdDesc(userToDoTests);
        log.debug("latest group........." + groupFromDb.toString());
        assertEquals(savedGroup2.getId(), groupFromDb.getId());
    }

    @Test
    public void shouldSaveParentRelationship() {
        User user = userRepository.save(new User("1111111111", null, null));
        Group ga = groupRepository.save(new Group("ga", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Group ga1 = groupRepository.save(new Group("ga1", GroupPermissionTemplate.DEFAULT_GROUP, user, ga));
        assertEquals(ga.getId(), ga1.getParent().getId());

    }

    @Test
    public void shouldReturnLevel1ChildrenByRepositoryQuery() {
        User user = userRepository.save(new User("2222222222", null, null));
        Group gb = groupRepository.save(new Group("gb", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Group gb1 = groupRepository.save(new Group("gb1", GroupPermissionTemplate.DEFAULT_GROUP, user, gb));
        Group gb2 = groupRepository.save(new Group("gb2", GroupPermissionTemplate.DEFAULT_GROUP, user, gb));
        List<Group> children = groupRepository.findAll(where(
                hasParent(gb)).and(isActive()));;
        assertEquals(2,children.size());
    }

    @Test
    public void shouldReturnByEntityGet() {
        User user = userRepository.save(new User("3333333330", null, null));
        Group gc = groupRepository.save(new Group("gc", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Group gc1 = groupRepository.save(new Group("gc1", GroupPermissionTemplate.DEFAULT_GROUP, user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", GroupPermissionTemplate.DEFAULT_GROUP, user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", GroupPermissionTemplate.DEFAULT_GROUP, user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", GroupPermissionTemplate.DEFAULT_GROUP, user, gc1));

        Group gcUpdated = groupRepository.findById(gc.getId()).get();
        Group gc1Updated = groupRepository.findById(gc1.getId()).get();
        Group gc2Updated = groupRepository.findById(gc2.getId()).get();

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
        Group group = groupRepository.save(new Group("token", GroupPermissionTemplate.DEFAULT_GROUP, user1));
        String realToken = genToken();
        Integer fakeToken = Integer.parseInt(realToken) - 10;
        group.setGroupTokenCode(realToken);
        groupRepository.save(group);
        Optional<Group> groupFromDb1 = groupRepository.findOne(hasJoinCode(String.valueOf(realToken)));
        Optional<Group> groupFromDb2 = groupRepository.findOne(hasJoinCode(String.valueOf(fakeToken)));
        assertTrue(groupFromDb1.isPresent());
        assertFalse(groupFromDb2.isPresent());
        assertEquals(groupFromDb1.get(), group);
    }

    @Test
    public void shouldUseAndExtendToken() {
        User user = userRepository.save(new User("3335551111", null, null));
        Group group = groupRepository.save(new Group("tg", GroupPermissionTemplate.DEFAULT_GROUP, user));
        String token = genToken();
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);
        Instant testDate3 = Instant.now().plus(36L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Optional<Group> group1 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1)));
        Optional<Group> group2 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate3)));
        assertTrue(group1.isPresent());
        assertEquals(group1.get(), group);
        assertFalse(group2.isPresent());

        group.setTokenExpiryDateTime(testDate3);
        groupRepository.save(group);
        Optional<Group> group3 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate2)));
        assertTrue(group3.isPresent());
        assertEquals(group3.get(), group);
    }

    @Test
    public void shouldCloseToken() {
        User user = userRepository.save(new User("3335550000", null, null));
        Group group = groupRepository.save(new Group("tg", GroupPermissionTemplate.DEFAULT_GROUP, user));
        String token = genToken();
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Group group1 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1))).get();
        assertNotNull(group1);
        assertEquals(group, group1);

        group.setTokenExpiryDateTime(Instant.now());
        Optional<Group> group2 = groupRepository.findOne(where(hasJoinCode(token)).and(joinCodeExpiresAfter(testDate1)));
        assertFalse(group2.isPresent());

    }

    @Test
    public void shouldSetInactive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000", null, null));
        Group group = groupRepository.save(new Group("gc", GroupPermissionTemplate.DEFAULT_GROUP, user));
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
        Group group1 = new Group("gc1", GroupPermissionTemplate.DEFAULT_GROUP, user);
        Group group2 = new Group("gc2", GroupPermissionTemplate.DEFAULT_GROUP, user);
        group1.addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group2.addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
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
        List<Group> testGroups = Arrays.asList(new Group("gc1", GroupPermissionTemplate.DEFAULT_GROUP, user), new Group("gc2", GroupPermissionTemplate.DEFAULT_GROUP, user), new Group("gc3", GroupPermissionTemplate.DEFAULT_GROUP, user), new Group("gc4", GroupPermissionTemplate.DEFAULT_GROUP, user));
        for (Group group : testGroups)
            group.addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        testGroups = groupRepository.saveAll(testGroups);
        assertThat(groupRepository.count(), is(4L));
        Page<Group> pageTest1 = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user,
                PageRequest.of(0, 3));
        assertThat(pageTest1.hasNext(), is(true));
        testGroups.get(0).setActive(false);
        groupRepository.save(testGroups.get(0));
        Page<Group> activeGroups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user,
                PageRequest.of(0,3));
        assertFalse(activeGroups.hasNext());
    }

    @Test
    public void shouldHaveValidGroupToken() {

        User user = userRepository.save(new User("4879342", null, null));
        Group groupToken = groupRepository.save(new Group(
                "Token", GroupPermissionTemplate.DEFAULT_GROUP, user));
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
        Group paidGroup = new Group("paidGroup", GroupPermissionTemplate.DEFAULT_GROUP, user);
        paidGroup.setPaidFor(true);
        Group group1 = groupRepository.save(paidGroup);
        Group group2 = groupRepository.save(new Group("unpaidGroup", GroupPermissionTemplate.DEFAULT_GROUP, user));
        assertTrue(group1.isPaidFor());
        assertFalse(group2.isPaidFor());
    }

    @Test
    public void shouldFindByPermission() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0801113456", null, null));
        Group group1 = new Group("group1", GroupPermissionTemplate.DEFAULT_GROUP, user);
        Group group2 = new Group("group2", GroupPermissionTemplate.DEFAULT_GROUP, user);

        group1.addMember(user, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group2.addMember(user, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);

        groupRepository.save(group1);
        groupRepository.save(group2);

        assertEquals(1, groupRepository.countActiveGroupsWhereMemberHasPermission(user, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        assertEquals(2, groupRepository.countActiveGroupsWhereMemberHasPermission(user, Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS));
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
        Group tg1 = groupRepository.save(new Group("tg1", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Group tg2 = groupRepository.save(new Group("tg2", GroupPermissionTemplate.DEFAULT_GROUP, user));
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

        userRepository.save(user1);
        userRepository.save(user2);

        Group group1 = new Group("test", GroupPermissionTemplate.DEFAULT_GROUP, user1);
        group1.addMember(user1, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group1.addMember(user2, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
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

    @Test
    public void testGroupMemberProvinceStats() {
        User createdByUser = new User("0993234439", null, null);
        User groupUser1 = new User("0993234451", null, null);
        User groupUser2 = new User("0993234452", null, null);
        User groupUser3 = new User("0993234453", null, null);
        User groupUser4 = new User("0993234454", null, null);
        User groupUser5 = new User("0993234455", null, null);
        createdByUser.setProvince(Province.ZA_MP);
        groupUser1.setProvince(Province.ZA_MP);
        groupUser2.setProvince(Province.ZA_MP);
        groupUser3.setProvince(Province.ZA_LP);
        groupUser4.setProvince(Province.ZA_GP);
        userRepository.save(createdByUser);
        userRepository.save(groupUser1);
        userRepository.save(groupUser2);
        userRepository.save(groupUser3);
        userRepository.save(groupUser4);
        userRepository.save(groupUser5);
        Group group = new Group("test", GroupPermissionTemplate.DEFAULT_GROUP, createdByUser);
        group.addMember(createdByUser, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_AT_CREATION, null);
        group.addMember(groupUser1, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group.addMember(groupUser2, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group.addMember(groupUser3, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group.addMember(groupUser4, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group.addMember(groupUser5, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);
        List<Object[]> groupStats = groupRepository.getGroupProvinceStats(group.getUid());
        assertEquals(4, groupStats.size());
        Map<String, Long> data = groupStats.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(province ->
                        Objects.isNull(province[0])? "unspecified": ((Province)province[0]).name(), province -> (Long)province[1]));
        assertEquals(Optional.of(3L).get(), Optional.ofNullable(data.get(Province.ZA_MP.name())).get());
        assertEquals(Optional.of(1L).get(), Optional.ofNullable(data.get(Province.ZA_GP.name())).get());
        assertEquals(Optional.of(1L).get(), Optional.ofNullable(data.get(Province.ZA_LP.name())).get());
        assertEquals(Optional.of(1L).get(), Optional.ofNullable(data.get("unspecified")).get());
    }

}


