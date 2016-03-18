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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EventRepositoryTest {

    private final static Logger log = LoggerFactory.getLogger(EventRepositoryTest.class);

    @Autowired
    EventRepository eventRepository;

    @Autowired
    MeetingRepository meetingRepository;

    @Autowired
    VoteRepository voteRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;


    @Test
    public void shouldSaveAndRetrieveEventData() throws Exception {

        assertThat(eventRepository.count(), is(0L));

        User userToDoTests = new User("55555");
        userRepository.save(userToDoTests);

        Group groupToDoTests = new Group("Test Group", userToDoTests);
        groupRepository.save(groupToDoTests);

        Timestamp testStartDateTime = Timestamp.valueOf("2015-08-18 10:00:00.0");

        Meeting eventToCreate = new Meeting("", testStartDateTime, userToDoTests, groupToDoTests, "The testing location");

        assertNull(eventToCreate.getId());
        assertNull(eventToCreate.getCreatedDateTime());

        eventRepository.save(eventToCreate);

        assertThat(userRepository.count(), is(1l));

        Event eventFromDb = eventRepository.findAll().iterator().next();

        assertNotNull(eventFromDb.getId());
        assertNotNull(eventFromDb.getCreatedDateTime());

        assertThat(eventFromDb.getAppliesToGroup().getGroupName(), is("Test Group"));
        assertThat(eventFromDb.getCreatedByUser().getPhoneNumber(), is("55555"));
        assertThat(eventFromDb.getEventStartDateTime(), is(testStartDateTime));
    }

    @Test
    public void shouldReturnEventsForGroupAfterDate() {
        User user = userRepository.save(new User("0827654321"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -10);
        Date pastDate = cal.getTime();
        Event pastEvent = eventRepository.save(new Meeting("past event", new Timestamp(pastDate.getTime()), user, group,"someLoc", false));
        cal.add(Calendar.MINUTE, 20);
        Date futureDate = cal.getTime();
        pastEvent = eventRepository.save(pastEvent);
        Event futureEvent = eventRepository.save(new Meeting("future event", new Timestamp(futureDate.getTime()), user,group,"someLoc"));
        futureEvent = eventRepository.save(futureEvent);
        // cancelled event
        Event futureEventCan = eventRepository.save(new Meeting("future event cancelled", new Timestamp(futureDate.getTime()), user, group, "someLocation"));
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
        Meeting event = (Meeting) eventRepository.save(new Meeting("duplicate event test", Timestamp.from(Instant.now()), user, group, "someLoc"));
        event.setEventLocation("dup location");
        Event event2 = eventRepository.save(event);
        assertEquals(event.getId(),event2.getId());

    }

    @Test
    public void shouldFindOneFutureVote() {
        User user = userRepository.save(new User("0831111112"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Date expiry = DateTimeUtil.roundHourUp(DateTimeUtil.addHoursToDate(new Date(), 1));
        Timestamp expiryTS = new Timestamp(expiry.getTime());

        Event vote = eventRepository.save(new Vote("testing vote query", expiryTS, user, group, true));

        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
        assertEquals(1,list.size());

    }

    @Test
    public void shouldNotFindOneFutureVote() {
        User user = userRepository.save(new User("0831111113"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Date expiry = DateTimeUtil.roundHourDown(new Date());
        Timestamp expiryTS = new Timestamp(expiry.getTime());
        Event vote = eventRepository.save(new Vote("testing vote query", expiryTS, user, group, true));
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
        assertEquals(0,list.size());
    }

    @Test
    public void shouldNotFindOneFutureVoteBecauseMeeting() {
        User user = userRepository.save(new User("0831111114"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Date expiry = DateTimeUtil.roundHourUp(DateTimeUtil.addHoursToDate(new Date(), 1));
        Timestamp expiryTS = new Timestamp(expiry.getTime());

        Event vote = eventRepository.save(new Vote("testing vote query", expiryTS, user, group, true));
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(new Date());
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

        Event event1 = eventRepository.save(new Meeting("test", Timestamp.from(Instant.now()), user2, group, "someLoc"));

        Group group2 = groupRepository.save(new Group("tg2", user2));
        group2.addMember(user2);
        group2 = groupRepository.save(group2);
        Event event2 = eventRepository.save(new Meeting("test2", Timestamp.from(Instant.now()), user2, group2, "someLoc"));

        List<Event> events = eventRepository.findByAppliesToGroupMembershipsUser(user1);
        List<Event> events2 = eventRepository.findByAppliesToGroupMembershipsUser(user2);

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
        group.addMember(user);
        group = groupRepository.save(group);

        Event event1 = eventRepository.save(new Meeting("test", Timestamp.valueOf(LocalDateTime.now().plusWeeks(1L)), user, group, "someLoc"));
        event1 = eventRepository.save(event1);

        Event event2 = eventRepository.save(new Vote("test2", Timestamp.from(Instant.now()), user, group));
        event2.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)));
        event2 = eventRepository.save(event2);

        Event event3 = eventRepository.save(new Meeting("test3", Timestamp.valueOf(LocalDateTime.now().plusDays(2L)), user, group, "someLoc"));
        event3.setCanceled(true);
        event3 = eventRepository.save(event3);
    }

    @Test
    public void shouldFindEventsByGroupBetweenTimestamps() {

        assertThat(eventRepository.count(), is(0L));
        User user = userRepository.save(new User("0813330000"));
        Group group1 = groupRepository.save(new Group("tg1", user));
        Group group2 = groupRepository.save(new Group("tg2", user));

        Event event1 = eventRepository.save(new Meeting("test", Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)), user, group1, "someLoc"));
        event1 = eventRepository.save(event1);

        Event event2 = eventRepository.save(new Meeting("test2", Timestamp.valueOf(LocalDateTime.now().minusWeeks(5L)), user, group1, "someLoc"));
        event2 = eventRepository.save(event2);

        Event event3 = eventRepository.save(new Vote("test3", Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)), user, group1));
        event3 = eventRepository.save(event3);

        Event event4 = eventRepository.save(new Meeting("test4", Timestamp.valueOf(LocalDateTime.now().minusWeeks(1L)), user, group2, "someLoc"));
        event4 = eventRepository.save(event4);

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp oneMonthBack = Timestamp.valueOf(LocalDateTime.now().minusMonths(1L));
        Timestamp twoMonthsBack = Timestamp.valueOf(LocalDateTime.now().minusMonths(2L));

        List<Meeting> test1 = meetingRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group1, oneMonthBack, now);
        List<Meeting> test2 = meetingRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group1, twoMonthsBack, oneMonthBack);
        List<Vote> test3 = voteRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group1, oneMonthBack, now);
        List<Meeting> test4 = meetingRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group2, oneMonthBack, now);
        List<Event> test5 = eventRepository.
                findByAppliesToGroupAndEventStartDateTimeBetween(group1, oneMonthBack, now, new Sort(Sort.Direction.ASC, "EventStartDateTime"));

        assertNotNull(test1);
        assertEquals(test1, Collections.singletonList(event1));
        assertNotNull(test2);
        assertEquals(test2, Collections.singletonList(event2));
        assertNotNull(test3);
        assertEquals(test3, Collections.singletonList(event3));
        assertNotNull(test4);
        assertEquals(test4, Collections.singletonList(event4));
        assertNotNull(test5);
        assertEquals(test5, Arrays.asList(event1, event3));

    }

    @Test
    public void countUpcomingEventsShouldWork() {

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

        Event event1 = new Meeting("count check", Timestamp.valueOf(LocalDateTime.now().plusDays(2L)), user, group, "someLoc");
        Event event2 = new Meeting("count check 2", Timestamp.valueOf(LocalDateTime.now().minusDays(2L)), user, group2, "someLoc");

        event1 = eventRepository.save(event1);
        event2 = eventRepository.save(event2);

        int numberUpcomingEvents1 = eventRepository.countFutureEvents(user.getId());
        assertThat(numberUpcomingEvents1, is(1));
        int numberUpcomingEvents2 = eventRepository.countFutureEvents(user2.getId());
        assertThat(numberUpcomingEvents2, is(0));

    }

}
