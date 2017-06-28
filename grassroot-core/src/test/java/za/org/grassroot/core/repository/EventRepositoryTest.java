package za.org.grassroot.core.repository;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;


@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Test
    public void shouldSaveAndRetrieveEventData() throws Exception {

        assertThat(eventRepository.count(), is(0L));

        User userToDoTests = new User("55555");
        userRepository.save(userToDoTests);
        Group groupToDoTests = new Group("Test Group", userToDoTests);
        groupRepository.save(groupToDoTests);
        Instant meetingStartDateTime = LocalDateTime.of(2015, 8, 18,
                10, 0).toInstant(ZoneOffset.UTC);
        Meeting eventToCreate = new MeetingBuilder().setName("").setStartDateTime(meetingStartDateTime).setUser(userToDoTests).setParent(groupToDoTests).setEventLocation("The testing location").createMeeting();

        assertNull(eventToCreate.getId());
        assertNotNull(eventToCreate.getUid());
        eventRepository.save(eventToCreate);

        assertThat(userRepository.count(), is(1l));
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertNotNull(eventFromDb.getCreatedDateTime());
        assertThat(eventFromDb.getAncestorGroup().getGroupName(), is("Test Group"));
        assertThat(eventFromDb.getCreatedByUser().getPhoneNumber(), is("55555"));
        assertThat(eventFromDb.getEventStartDateTime(), is(meetingStartDateTime));
    }

    @Test
    public void shouldSetNoReminderSent() throws Exception {
        User newUser = userRepository.save(new User("12345"));
        Group newGroup = groupRepository.save(new Group("Test Group", newUser));
        Instant meetingTime = Instant.now().plus(1, ChronoUnit.MINUTES);

        Event newEvent = new MeetingBuilder().setName("new meeting").setStartDateTime(meetingTime).setUser(newUser).setParent(newGroup).setEventLocation("soweto").createMeeting();

        assertNull(newEvent.getId());
        assertNotNull(newEvent.getUid());
        newEvent.setNoRemindersSent(12);
        eventRepository.save(newEvent);

        assertThat(eventRepository.count(), is(1L));
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertThat(eventFromDb.getNoRemindersSent(), is(12));

    }

    @Test
    public void shouldSetEventPublic() {
        User newUser = userRepository.save(new User("12345"));
        Group newGroup = groupRepository.save(new Group("Test Group", newUser));
        Instant presentTime = Instant.now().plus(12, ChronoUnit.DAYS);
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("new Meeting").setStartDateTime(presentTime).setUser(newUser).setParent(newGroup).setEventLocation("soweto").createMeeting());

        assertNotNull(newEvent.getId());
        assertNotNull(newEvent.getUid());
        newEvent.setPublic(true);
        assertTrue(newEvent.isPublic());
        eventRepository.save(newEvent);

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertTrue(eventFromDb.isPublic());
    }


    @Test
    public void shouldCheckScheduleReminderActive() {
        User userToCreate = userRepository.save(new User("123456"));
        Group groupToCreate = groupRepository.save(new Group("Test Group", userToCreate));
        groupRepository.save(groupToCreate);
        Instant reminderTime = Instant.now().plus(8L, ChronoUnit.HOURS);

        Meeting meetingToCreate = new MeetingBuilder().setName("discussion").setStartDateTime(reminderTime).setUser(userToCreate).setParent(groupToCreate).setEventLocation("jozi").createMeeting();
        assertNull(meetingToCreate.getId());
        assertNotNull(meetingToCreate.getUid());
        meetingToCreate.setScheduledReminderActive(true);
        assertTrue(meetingToCreate.isScheduledReminderActive());
        eventRepository.save(meetingToCreate);

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertTrue(eventFromDb.isScheduledReminderActive());
    }

    @Test
    public void shouldGetDeadlineTime() {

        User user = userRepository.save(new User("09876"));
        Group group = groupRepository.save(new Group("New Event", user));
        Instant presentTime = Instant.now().plus(1L, ChronoUnit.HOURS);
        Event event = eventRepository.save(new MeetingBuilder().setName("new Ideas").setStartDateTime(presentTime).setUser(user).setParent(group).setEventLocation("soweto").createMeeting());

        assertNotNull(event.getId());
        assertNotNull(event.getUid());
        eventRepository.save(event);
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertTrue(eventFromDb.getDeadlineTime().equals(presentTime));
    }



    @Test
    public void shouldGetTodo() {
        User user = userRepository.save(new User("098765"));
        Group group = groupRepository.save(new Group("testing events", user));
        Instant timer = Instant.now().plus(10L, ChronoUnit.HOURS);
        Event event = eventRepository.save(new Vote("", timer, user, group, false,
                ""));
        assertNotNull(event.getId());
        assertNotNull(event.getUid());
        eventRepository.save(event);

        Event todoFromDB = eventRepository.findAll().iterator().next();
        assertNotNull(todoFromDB.getId());
        assertThat(todoFromDB.getAncestorGroup().getGroupName(), is("testing events"));
        assertThat(todoFromDB.getCreatedByUser().getPhoneNumber(), is("098765"));
        assertFalse(todoFromDB.getTodos().iterator().hasNext());
    }

    @Test
    public void ShouldGetTodoReminderMinutes() {
        User user = userRepository.save(new User("098765"));
        Group group = groupRepository.save(new Group("Test ", user));
        Instant currentReminder = Instant.now().plus(20, ChronoUnit.HOURS);
        Event eventReminder = eventRepository.save(new Vote("", currentReminder, user, group));
        assertNotNull(eventReminder.getUid());
        eventReminder.setReminderType(EventReminderType.CUSTOM);
        assertThat(eventReminder.getReminderType(), is(EventReminderType.CUSTOM));
        eventReminder.setCustomReminderMinutes(10);
        assertThat(eventReminder.getCustomReminderMinutes(), is(10));
        eventRepository.save(eventReminder);

        assertThat(eventRepository.count(), is(1L));
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getId());
        assertThat(eventFromDb.getAncestorGroup().getGroupName(), is("Test "));
        assertThat(eventFromDb.getReminderType(), is(EventReminderType.CUSTOM));
        assertThat(eventFromDb.getCustomReminderMinutes(), is(10));
        assertThat(eventFromDb.getTodoReminderMinutes(), is(10));

        Event event = eventRepository.findAll().iterator().next();
        event.setCustomReminderMinutes(0);
        assertThat(event.getCustomReminderMinutes(),is(0));


    }



    @Test
    public void shouldSaveAndFetchAssignedMemberCollection() {
        User user = userRepository.save(new User("098765"));
        Group group = groupRepository.save(new Group("Test", user));
        Set<User> userList = new HashSet<>();

        Instant eventTime = Instant.now().plus(2L, ChronoUnit.DAYS);
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("Discussion").setStartDateTime(eventTime).setUser(user).setParent(group).setEventLocation("Soweto").createMeeting());

        assertNotNull(newEvent.getUid());
        userList.add(user);
        newEvent.putAssignedMembersCollection(userList);
        assertTrue(newEvent.fetchAssignedMembersCollection().equals(userList));
        eventRepository.save(newEvent);

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertTrue(eventFromDb.fetchAssignedMembersCollection().equals(userList));

    }

    @Test
    public void shouldCheckIfDisabledAndGroupConfigured() {
        User userToCheck = userRepository.save(new User("0887608"));
        Group groupToCheck = groupRepository.save(new Group("Group", userToCheck));
        groupToCheck.setReminderMinutes(12);
        assertThat(groupToCheck.getReminderMinutes(), is(12));
        groupToCheck = groupRepository.save(groupToCheck);

        Instant startTime = Instant.now().plus(2, ChronoUnit.DAYS);
        Event event = eventRepository.save(new MeetingBuilder().setName("").setStartDateTime(startTime).setUser(userToCheck).setParent(groupToCheck).setEventLocation("").createMeeting());

        assertNotNull(event.getUid());
        event.setReminderType(EventReminderType.GROUP_CONFIGURED);
        event.setScheduledReminderActive(false);
        eventRepository.save(event);

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertThat(eventFromDb.getAncestorGroup().getGroupName(), is("Group"));
        assertThat(eventFromDb.getReminderType(), is(EventReminderType.GROUP_CONFIGURED));
        assertFalse(eventFromDb.isScheduledReminderActive());
    }

    @Test
    public void shouldGetAllMembers() throws Exception {
        User users = userRepository.save(new User("0763490"));
        User users1 = userRepository.save(new User("07634"));
        User users2 = userRepository.save(new User("0763423"));

        Group groups = groupRepository.save(new Group("Events Test", users));
        groups.addMember(users);
        groups.addMember(users1);
        groups.addMember(users2);

        groups = groupRepository.save(groups);
        Instant startTime = Instant.now().plus(10, ChronoUnit.MINUTES);
        Event event = eventRepository.save(new Vote("", startTime, users,
                groups, true, "welcoming new members"));

        assertNotNull(event.getUid());
        assertTrue(event.isAllGroupMembersAssigned());

        eventRepository.save(event);
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertThat(eventFromDb.getAncestorGroup().getMembersWithChildrenIncluded().size(), is(3));
        assertThat(eventFromDb.getAncestorGroup().getMembers().size(), is(3));
        assertThat(eventFromDb.getAssignedMembers().size(),is(0));


    }

    @Test
    public void shouldCheckScheduledReminderTime() {
        User newUser = userRepository.save(new User("12345"));
        Group newGroup = groupRepository.save(new Group("Events", newUser));

        Instant newTime = Instant.now().plus(14, ChronoUnit.MINUTES);
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("Welcome new Members").setStartDateTime(newTime).setUser(newUser).setParent(newGroup).setEventLocation("Polokwane").createMeeting());

        assertNotNull(newEvent.getUid());
        assertThat(newEvent.getScheduledReminderTime(), is(nullValue()));
        eventRepository.save(newEvent);
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertThat(eventFromDb.getEventStartDateTime(), is(newTime));
        assertThat(eventFromDb.getScheduledReminderTime(), is(nullValue()));

    }

    @Test
    public void giveScheduledReminderValueAndRestrictToDayTime() {
        User users = userRepository.save(new User(""));
        Group groups = groupRepository.save(new Group("", users));
        Instant scheduleTime = Instant.now().plus(2, ChronoUnit.DAYS);
        Event newEvent = eventRepository.save(new MeetingBuilder().setName("").setStartDateTime(scheduleTime).setUser(users).setParent(groups).setEventLocation("").createMeeting());
        assertNotNull(newEvent.getUid());
        newEvent.setReminderType(EventReminderType.CUSTOM);
        newEvent.setCustomReminderMinutes(12);
        assertTrue(newEvent.getReminderType().equals(EventReminderType.CUSTOM));
        newEvent.updateScheduledReminderTime();
        Instant updateTime = DateTimeUtil.restrictToDaytime(scheduleTime.minus(12, ChronoUnit.MINUTES),
                scheduleTime, DateTimeUtil.getSAST());

        assertThat(newEvent.getScheduledReminderTime(), is(updateTime));
        assertTrue(newEvent.getScheduledReminderTime().isAfter(Instant.now()));
        newEvent.setScheduledReminderActive(false);
        assertFalse(newEvent.isScheduledReminderActive());

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertThat(eventFromDb.getScheduledReminderTime(), is(updateTime));
        assertTrue(eventFromDb.getReminderType().equals(EventReminderType.CUSTOM));
        assertTrue(newEvent.getScheduledReminderTime().isAfter(Instant.now()));
        assertFalse(newEvent.isScheduledReminderActive());

    }



    @Test
    public void shouldSaveVersion() {
        User user = userRepository.save(new User("6734895"));
        Group groups = groupRepository.save(new Group("Test", user));
        Instant time = Instant.now();
        Event event = eventRepository.save(new MeetingBuilder().setName("").setStartDateTime(time).setUser(user).setParent(groups).setEventLocation("").createMeeting());

        assertNotNull(event.getUid());
        event.setVersion(1);
        assertThat(event.getVersion(), is(1));
        eventRepository.save(event);

        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertNotNull(eventFromDb.getUid());
        assertThat(eventFromDb.getVersion(), is(1));
    }

    @Test
    public void shouldSetValueScheduledReminderAndRestrictToDay() {
        User newUser = userRepository.save(new User("098765"));
        Group newGroup = groupRepository.save(new Group("", newUser));
        Instant newTime = Instant.now().plus(48, ChronoUnit.HOURS);

        Event eventCreate = eventRepository.save(new MeetingBuilder().setName("").setStartDateTime(newTime).setUser(newUser).setParent(newGroup).setEventLocation("").createMeeting());
        assertNotNull(eventCreate.getUid());
        newGroup.setReminderMinutes(30);
        assertThat(newGroup.getReminderMinutes(), is(30));
        eventCreate.setReminderType(EventReminderType.GROUP_CONFIGURED);
        assertTrue(eventCreate.getReminderType().equals(EventReminderType.GROUP_CONFIGURED));
        eventCreate.updateScheduledReminderTime();
        Instant timeReminderShouldBe = DateTimeUtil.restrictToDaytime(newTime.minus(30, ChronoUnit.MINUTES),
                newTime, DateTimeUtil.getSAST());
        assertThat(eventCreate.getScheduledReminderTime(), is(timeReminderShouldBe));
        assertTrue(eventCreate.getScheduledReminderTime().isAfter(Instant.now()));
        assertTrue(eventCreate.isScheduledReminderActive());
        Event eventFromDb = eventRepository.findAll().iterator().next();
        assertThat(eventFromDb.getScheduledReminderTime(), is(timeReminderShouldBe));
        assertTrue(eventFromDb.getScheduledReminderTime().isAfter(Instant.now()));

        assertTrue(eventFromDb.isScheduledReminderActive());
        eventFromDb.setReminderType(EventReminderType.DISABLED);
        assertThat(eventFromDb.getReminderType(), is(EventReminderType.DISABLED));
        eventFromDb.updateScheduledReminderTime();
        assertThat(eventFromDb.getScheduledReminderTime(), is(nullValue()));

    }
    @Test
    public void shouldReturnEventsForGroupAfterDate() {
        User user = userRepository.save(new User("27827654321"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant pastDate = Instant.now().minus(10, ChronoUnit.MINUTES);
        Event pastEvent = eventRepository.save(new MeetingBuilder().setName("past event").setStartDateTime(pastDate).setUser(user).setParent(group).setEventLocation("someLoc").setIncludeSubGroups(false).createMeeting());
        eventRepository.save(pastEvent);

        Instant futureDate = Instant.now().plus(20, ChronoUnit.MINUTES);
        Event futureEvent = eventRepository.save(new MeetingBuilder().setName("future event").setStartDateTime(futureDate).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        eventRepository.save(futureEvent);

        Event futureEventCancelled = eventRepository.save(new MeetingBuilder().setName("future event cancelled").setStartDateTime(futureDate).setUser(user).setParent(group).setEventLocation("someLocation").createMeeting());
        futureEventCancelled.setCanceled(true);
        eventRepository.save(futureEventCancelled);

        List<Event> firstSet = eventRepository.findByParentGroupAndCanceledFalse(group);
        List<Event> secondSet = eventRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group,
                Instant.now(), DateTimeUtil.getVeryLongAwayInstant(), new Sort(Sort.Direction.ASC, "EventStartDateTime"));

        Group updatedGroup = groupRepository.findOneByUid(group.getUid());

        assertNotNull(firstSet);
        assertNotNull(secondSet);
        assertEquals(2, firstSet.size());
        assertEquals(1, secondSet.size());

        assertFalse(firstSet.contains(futureEventCancelled));
        assertTrue(firstSet.contains(pastEvent));
        assertTrue(firstSet.contains(futureEvent));
        assertFalse(secondSet.contains(pastEvent));
        assertFalse(secondSet.contains(futureEventCancelled));
        assertTrue(secondSet.contains(futureEvent));

        Set<Event> listUpcoming = updatedGroup.getUpcomingEvents(event -> true, true);
        Set<Event> listAll = updatedGroup.getEvents();
        assertEquals(1, listUpcoming.size());
        assertEquals(3, listAll.size());
        assertEquals("future event", listUpcoming.iterator().next().getName());
    }

    @Test
    public void shouldReturnSameObjectOnSecondUpdate() {
        User user = userRepository.save(new User("085551234","test dup event user"));
        Group group = groupRepository.save(new Group("test dup event",user));
        Meeting event = eventRepository.save(new MeetingBuilder().setName("duplicate event test").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        event.setEventLocation("dup location");
        Event event2 = eventRepository.save(event);
        assertEquals(event.getId(),event2.getId());
    }

    @Test
    public void shouldFindOneFutureVote() {
        User user = userRepository.save(new User("0831111112"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant expiry = Instant.now().plus(1, HOURS).truncatedTo(HOURS);

        Event vote = eventRepository.save(new Vote("testing vote query", expiry, user, group, true));

        eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(Instant.now());
        assertEquals(1,list.size());

    }

    @Test
    public void shouldNotFindOnePastVote() {
        User user = userRepository.save(new User("0831111113"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant expiry = Instant.now().truncatedTo(HOURS);
        Event vote = eventRepository.save(new Vote("testing vote query", expiry, user, group, true));
        eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(Instant.now());
        assertEquals(0,list.size());
    }

    @Test
    public void shouldNotFindMeetingWhenLookingForVote() {
        User user = userRepository.save(new User("0831111114"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant expiry = Instant.now().truncatedTo(HOURS).plus(1, HOURS);

        Event meeting = eventRepository.save(new MeetingBuilder().setName("testing vote query").setStartDateTime(expiry).setUser(user).setParent(group).setEventLocation("somewhere").createMeeting());
        eventRepository.save(meeting);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(Instant.now());
        assertEquals(0,list.size());
    }

    @Test
    public void shouldFindEventsByUser() {

        assertThat(eventRepository.count(), is(0L));
        User user1 = userRepository.save(new User("0831111115"));
        User user2 = userRepository.save(new User("0831111116"));

        Group group = groupRepository.save(new Group("tg1", user1));
        group.addMember(user1);
        group.addMember(user2);
        group = groupRepository.save(group);

        Event event1 = eventRepository.save(new MeetingBuilder().setName("test").setStartDateTime(Instant.now()).setUser(user2).setParent(group).setEventLocation("someLoc").createMeeting());

        Group group2 = groupRepository.save(new Group("tg2", user2));
        group2.addMember(user2);
        group2 = groupRepository.save(group2);
        Event event2 = eventRepository.save(new MeetingBuilder().setName("test2").setStartDateTime(Instant.now()).setUser(user2).setParent(group2).setEventLocation("someLoc").createMeeting());

        List<Event> events = eventRepository.findByParentGroupMembershipsUser(user1);
        List<Event> events2 = eventRepository.findByParentGroupMembershipsUser(user2);

        assertThat(userRepository.count(), is(2L));
        assertThat(groupRepository.count(), is(2L));
        assertThat(eventRepository.count(), is(2L));

        assertFalse(events.isEmpty());
        assertThat(events.size(), is(1));
        assertTrue(events.contains(event1));
        assertFalse(events.contains(event2));

        assertFalse(events2.isEmpty());
        assertThat(events2.size(), is(2));
        assertTrue(events2.contains(event1));
        assertTrue(events2.contains(event2));
    }

    @Test
    public void shouldFindEventsByUserAndTimeStamp() {

        assertThat(eventRepository.count(), is(0L));
        User user = userRepository.save(new User("0831111115"));
        Group group = groupRepository.save(new Group("tg1", user));
        group.addMember(user);
        group = groupRepository.save(group);

        Event event1 = eventRepository.save(new MeetingBuilder().setName("test").setStartDateTime(Instant.now().plus(7, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        eventRepository.save(event1);

        Event event2 = eventRepository.save(new Vote("test2", Instant.now().minus(7, DAYS), user, group));
        eventRepository.save(event2);

        Event event3 = eventRepository.save(new MeetingBuilder().setName("test3").setStartDateTime(Instant.now().plus(7, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        event3.setCanceled(true);
        eventRepository.save(event3);

        List<Event> events = eventRepository.findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(user, Instant.now());
        assertNotNull(events);
        assertEquals(1, events.size());
        assertFalse(events.contains(event2));
        assertFalse(events.contains(event3));
        assertTrue(events.contains(event1));
    }

    @Test
    public void countUpcomingEventsShouldWork() {

        LocalDateTime.now().with(ChronoField.DAY_OF_WEEK, 1);

        assertThat(eventRepository.count(), is(0L));

        User user = userRepository.save(new User("0710001111"));
        User user2 = userRepository.save(new User("0810001111"));
        Group group = groupRepository.save(new Group("tg1", user));
        group.addMember(user);
        group = groupRepository.save(group);
        Group group2 = groupRepository.save(new Group("tg2", user2));

        group2.addMember(user);
        group2.addMember(user2);
        group2 = groupRepository.save(group2);

        Event event1 = new MeetingBuilder().setName("count check").setStartDateTime(Instant.now().plus(2, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting();
        Event event2 = new MeetingBuilder().setName("count check 2").setStartDateTime(Instant.now().minus(2, DAYS)).setUser(user).setParent(group2).setEventLocation("someLoc").createMeeting();

        event1 = eventRepository.save(event1);
        event2 = eventRepository.save(event2);

        int numberUpcomingEvents1 = eventRepository.countByParentGroupMembershipsUserAndEventStartDateTimeGreaterThan(user, Instant.now());
        assertThat(numberUpcomingEvents1, is(1));
        int numberUpcomingEvents2 = eventRepository.countByParentGroupMembershipsUserAndEventStartDateTimeGreaterThan(user2, Instant.now());
        assertThat(numberUpcomingEvents2, is(0));

    }

    @Test
    public void shouldFindCancelledEventsForUser() {

	    assertThat(groupRepository.count(), is(0L));
	    assertThat(userRepository.count(), is(0L));
	    assertThat(eventRepository.count(), is(0L));
	    assertThat(eventLogRepository.count(), is(0L));

	    User user = userRepository.save(new User("0710001111"));
	    User user2 = userRepository.save(new User("0810001111"));

	    Group group = groupRepository.save(new Group("tg1", user));
	    group.addMember(user);
	    group = groupRepository.save(group);
	    Group group2 = groupRepository.save(new Group("tg2", user2));
	    group2.addMember(user);
	    group2.addMember(user2);
	    group2 = groupRepository.save(group2);

	    Instant intervalStart1 = Instant.now().minus(1, ChronoUnit.MINUTES);
	    Instant intervalStart2 = Instant.now().plus(1, ChronoUnit.MINUTES);

	    Event event1 = new MeetingBuilder().setName("count check").setStartDateTime(Instant.now().plus(2, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting();
	    Event event2 = new MeetingBuilder().setName("count check 2").setStartDateTime(Instant.now().minus(2, DAYS)).setUser(user).setParent(group2).setEventLocation("someLoc").createMeeting();

	    event1 = eventRepository.save(event1);
	    event2 = eventRepository.save(event2);

	    eventLogRepository.save(new EventLog(user, event1, EventLogType.CANCELLED));

	    assertThat(eventLogRepository.count(), is(1L));
	    assertThat(eventRepository.count(), is(2L));

	    List<Event> cancelledEvents1 = eventRepository.findByMemberAndCanceledSince(user, intervalStart1);

	    assertFalse(cancelledEvents1.isEmpty());
	    assertThat(cancelledEvents1.size(), is(1));
	    assertTrue(cancelledEvents1.contains(event1));
	    assertFalse(cancelledEvents1.contains(event2));

	    List<Event> cancelledEvents2 = eventRepository.findByMemberAndCanceledSince(user, intervalStart2);

	    assertTrue(cancelledEvents2.isEmpty());

    }

}
