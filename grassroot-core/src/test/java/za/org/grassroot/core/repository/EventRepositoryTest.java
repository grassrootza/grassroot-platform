package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EventRepositoryTest {

    private final static Logger log = LoggerFactory.getLogger(EventRepositoryTest.class);

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;


    @Test
    public void shouldSaveAndRetrieveEventData() throws Exception {

        assertThat(eventRepository.count(), is(0L));

        Event eventToCreate = new Event();
        User userToDoTests = new User();
        Group groupToDoTests = new Group();

        userToDoTests.setPhoneNumber("55555");
        userRepository.save(userToDoTests);
        groupToDoTests.setGroupName("Test Group");
        groupToDoTests.setCreatedByUser(userToDoTests);
        groupRepository.save(groupToDoTests);

        Timestamp testStartDateTime = Timestamp.valueOf("2015-08-18 10:00:00.0");

        eventToCreate.setCreatedByUser(userToDoTests);
        eventToCreate.setAppliesToGroup(groupToDoTests);
        eventToCreate.setEventLocation("The testing location");
        eventToCreate.setEventStartDateTime(testStartDateTime);
        assertNull(eventToCreate.getId());
        assertNull(eventToCreate.getCreatedDateTime());

        eventRepository.save(eventToCreate);

        assertThat(userRepository.count(), is(1l));

        Event eventFromDb = eventRepository.findAll().iterator().next();

        assertNotNull(eventFromDb.getId());
        assertNotNull(eventFromDb.getCreatedDateTime());
        assertNotNull(eventFromDb.getEventLocation());

        assertThat(eventFromDb.getEventLocation(), is("The testing location"));
        assertThat(eventFromDb.getAppliesToGroup().getGroupName(), is("Test Group"));
        assertThat(eventFromDb.getCreatedByUser().getPhoneNumber(), is("55555"));
        assertThat(eventFromDb.getEventStartDateTime(), is(testStartDateTime));
    }

    @Test
    public void testMinimumEqual() {
        Event e1 = new Event();
        EventDTO e2 = new EventDTO();
        assertEquals(true, e1.minimumEquals(e2));
        e1.setEventLocation("location");
        assertEquals(false, e1.minimumEquals(e2));
        e2.setEventLocation(e1.getEventLocation());
        assertEquals(true, e1.minimumEquals(e2));
        e1.setName("name");
        assertEquals(false, e1.minimumEquals(e2));
        e2.setName(e1.getName());
        assertEquals(true, e1.minimumEquals(e2));
        /*e1.setDateTimeString("31th 7pm");
        assertEquals(false, e1.minimumEquals(e2));*/
        e2.setDateTimeString(e1.getDateTimeString());
        assertEquals(true, e1.minimumEquals(e2));
        e1.setEventStartDateTime(new Timestamp(new Date().getTime()));
        assertEquals(false, e1.minimumEquals(e2));
        e2.setEventStartDateTime(e1.getEventStartDateTime());
        assertEquals(true, e1.minimumEquals(e2));

    }

    @Test
    public void shouldReturnEventsForGroupAfterDate() {
        User user = userRepository.save(new User("0827654321"));
        Group group = groupRepository.save(new Group("events for group test",user));
        Event pastEvent = eventRepository.save(new Event("past event",user,group,false));
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -10);
        Date pastDate = cal.getTime();
        cal.add(Calendar.MINUTE, 20);
        Date futureDate = cal.getTime();
        pastEvent.setEventStartDateTime(new Timestamp(pastDate.getTime()));
        pastEvent = eventRepository.save(pastEvent);
        Event futureEvent = eventRepository.save(new Event("future event",user,group,false));
        futureEvent.setEventStartDateTime(new Timestamp(futureDate.getTime()));
        futureEvent = eventRepository.save(futureEvent);
        // cancelled event
        Event futureEventCan = eventRepository.save(new Event("future event cancelled",user,group,false));
        futureEventCan.setEventStartDateTime(new Timestamp(futureDate.getTime()));
        futureEventCan.setCanceled(true);
        futureEventCan = eventRepository.save(futureEventCan);

        //
        List<Event> list = eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, new Date(), false);
        assertEquals(1, list.size());
        assertEquals("future event", list.get(0).getName());
    }

    @Test
    public void shouldReturnSameObjectOnSecondUpdate() {
        User user = userRepository.save(new User("085551234","test dup event user"));
        Group group = groupRepository.save(new Group("test dup event",user));
        Event event = eventRepository.save(new Event("duplicate event test",user,group));
        event.setEventLocation("dup location");
        Event event2 = eventRepository.save(event);
        assertEquals(event.getId(),event2.getId());

    }

    @Test
    public void shouldFindOneFutureVote() {
        User user = userRepository.save(new User("0831111112"));
        Event vote = eventRepository.save(new Event(user, EventType.Vote,true));
        vote.setName("testing vote query");
        Date expiry = DateTimeUtil.roundHourUp(DateTimeUtil.addHoursToDate(new Date(), 1));
        Timestamp expiryTS = new Timestamp(expiry.getTime());
        vote.setEventStartDateTime(expiryTS);
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
        assertEquals(1,list.size());

    }

    @Test
    public void shouldNotFindOneFutureVote() {
        User user = userRepository.save(new User("0831111113"));
        Event vote = eventRepository.save(new Event(user, EventType.Vote,true));
        vote.setName("testing vote query");
        Date expiry = DateTimeUtil.roundHourDown(new Date());
        Timestamp expiryTS = new Timestamp(expiry.getTime());
        vote.setEventStartDateTime(expiryTS);
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
        assertEquals(0,list.size());

    }

    @Test
    public void shouldNotFindOneFutureVoteBecauseMeeting() {
        User user = userRepository.save(new User("0831111114"));
        Event vote = eventRepository.save(new Event(user, EventType.Meeting,true));
        vote.setName("testing vote query");
        Date expiry = DateTimeUtil.roundHourUp(DateTimeUtil.addHoursToDate(new Date(), 1));
        Timestamp expiryTS = new Timestamp(expiry.getTime());
        vote.setEventStartDateTime(expiryTS);
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
        assertEquals(0,list.size());

    }

    @Test
    public void shouldIdentifyEventTypeVote() {
        User user = userRepository.save(new User("0831111115"));
        Event vote = eventRepository.save(new Event(user, EventType.Vote, true));
        assertEquals(EventType.Vote,vote.getEventType());

    }

    @Test
    public void shouldFindEventsByUser() {

        assertThat(eventRepository.count(), is(0L));
        User user1 = userRepository.save(new User("0831111115"));
        User user2 = userRepository.save(new User("0831111116"));

        Group group = groupRepository.save(new Group("tg1", user1));
        group = groupRepository.save(group.addMember(user1));
        group = groupRepository.save(group.addMember(user2));
        Event event1 = eventRepository.save(new Event("test", user2, group));

        Group group2 = groupRepository.save(new Group("tg2", user2));
        group2 = groupRepository.save(group2.addMember(user2));
        Event event2 = eventRepository.save(new Event("test2", user2, group2));

        List<Event> events = eventRepository.findByAppliesToGroupGroupMembers(user1);
        List<Event> events2 = eventRepository.findByAppliesToGroupGroupMembers(user2);

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
    public void ShouldFindEventsByUserAndTimeStamp() {

        assertThat(eventRepository.count(), is(0L));
        User user = userRepository.save(new User("0831111115"));
        Group group = groupRepository.save(new Group("tg1", user));
        group = groupRepository.save(group.addMember(user));

        Event event1 = eventRepository.save(new Event("test", user, group));
        event1.setEventType(EventType.Meeting);
        event1.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().plusWeeks(1L)));
        event1 = eventRepository.save(event1);

        Event event2 = eventRepository.save(new Event("test2", user, group));
        event2.setEventType(EventType.Vote);
        event2.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)));
        event2 = eventRepository.save(event2);

        Event event3 = eventRepository.save(new Event("test3", user, group));
        event3.setEventType(EventType.Meeting);
        event3.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().plusDays(2L)));
        event3.setCanceled(true);
        event3 = eventRepository.save(event3);

        List<Event> events1 = eventRepository.
                findByAppliesToGroupGroupMembersAndEventTypeAndEventStartDateTimeGreaterThanAndCanceled(user, EventType.Meeting, new Date(), false);

        assertFalse(events1.isEmpty());
        assertThat(events1.size(), is(1));
        assertTrue(events1.contains(event1));
        assertFalse(events1.contains(event2));
        assertFalse(events1.contains(event3));

        List<Event> events2 = eventRepository.
                findByAppliesToGroupGroupMembersAndEventTypeAndEventStartDateTimeLessThanAndCanceled(user, EventType.Vote, new Date(), false);

        assertFalse(events2.isEmpty());
        assertThat(events2.size(), is(1));
        assertTrue(events2.contains(event2));
        assertFalse(events2.contains(event1));
        assertFalse(events2.contains(event3));

        List<Event> events3 = eventRepository.
                findByAppliesToGroupGroupMembersAndEventTypeAndEventStartDateTimeGreaterThanAndCanceled(user, EventType.Vote, new Date(), false);
        assertTrue(events3.isEmpty());

        List<Event> events4 = eventRepository.
                findByAppliesToGroupGroupMembersAndEventTypeAndEventStartDateTimeLessThanAndCanceled(user, EventType.Meeting, new Date(), false);
        assertTrue(events4.isEmpty());
    }

    @Test
    public void shouldFindEventsByGroupBetweenTimestamps() {

        assertThat(eventRepository.count(), is(0L));
        User user = userRepository.save(new User("0813330000"));
        Group group1 = groupRepository.save(new Group("tg1", user));
        Group group2 = groupRepository.save(new Group("tg2", user));

        Event event1 = eventRepository.save(new Event("test", user, group1));
        event1.setEventType(EventType.Meeting);
        event1.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)));
        event1 = eventRepository.save(event1);

        Event event2 = eventRepository.save(new Event("test2", user, group1));
        event2.setEventType(EventType.Meeting);
        event2.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(5L)));
        event2 = eventRepository.save(event2);

        Event event3 = eventRepository.save(new Event("test3", user, group1));
        event3.setEventType(EventType.Vote);
        event3.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)));
        event3 = eventRepository.save(event3);

        Event event4 = eventRepository.save(new Event("test4", user, group2));
        event4.setEventType(EventType.Meeting);
        event4.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)));
        event4 = eventRepository.save(event4);

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp oneMonthBack = Timestamp.valueOf(LocalDateTime.now().minusMonths(1L));
        Timestamp twoMonthsBack = Timestamp.valueOf(LocalDateTime.now().minusMonths(2L));

        List<Event> test1 = eventRepository.
                findByAppliesToGroupAndEventTypeAndEventStartDateTimeBetween(group1, EventType.Meeting, oneMonthBack, now);
        List<Event> test2 = eventRepository.
                findByAppliesToGroupAndEventTypeAndEventStartDateTimeBetween(group1, EventType.Meeting, twoMonthsBack, oneMonthBack);
        List<Event> test3 = eventRepository.
                findByAppliesToGroupAndEventTypeAndEventStartDateTimeBetween(group1, EventType.Vote, oneMonthBack, now);
        List<Event> test4 = eventRepository.
                findByAppliesToGroupAndEventTypeAndEventStartDateTimeBetween(group2, EventType.Meeting, oneMonthBack, now);
        List<Event> test5 = eventRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group1, oneMonthBack, now, new Sort(Sort.Direction.ASC, "EventStartDateTime"));

        assertNotNull(test1);
        assertEquals(test1, Arrays.asList(event1));
        assertNotNull(test2);
        assertEquals(test2, Arrays.asList(event2));
        assertNotNull(test3);
        assertEquals(test3, Arrays.asList(event3));
        assertNotNull(test4);
        assertEquals(test4, Arrays.asList(event4));
        assertNotNull(test5);
        assertEquals(test5, Arrays.asList(event1, event3));

    }

}
