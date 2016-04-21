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
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.time.temporal.ChronoUnit.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EventRepositoryTest {

    private final static Logger log = LoggerFactory.getLogger(EventRepositoryTest.class);

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void shouldSaveAndRetrieveEventData() throws Exception {

        assertThat(eventRepository.count(), is(0L));

        User userToDoTests = new User("55555");
        userRepository.save(userToDoTests);

        Group groupToDoTests = new Group("Test Group", userToDoTests);
        groupRepository.save(groupToDoTests);

        Instant meetingStartDateTime = LocalDateTime.of(2015, 8, 18, 10, 0).toInstant(ZoneOffset.UTC);

        Meeting eventToCreate = new Meeting("", meetingStartDateTime, userToDoTests, groupToDoTests, "The testing location");

        assertNull(eventToCreate.getId());
        assertNotNull(eventToCreate.getUid());

        eventRepository.save(eventToCreate);

        assertThat(userRepository.count(), is(1l));

        Event eventFromDb = eventRepository.findAll().iterator().next();

        assertNotNull(eventFromDb.getId());
        assertNotNull(eventFromDb.getCreatedDateTime());

//        assertThat(eventFromDb.getParentAppliesToGroup().getGroupName(), is("Test Group"));
        assertThat(eventFromDb.getCreatedByUser().getPhoneNumber(), is("55555"));
        assertThat(eventFromDb.getEventStartDateTime(), is(meetingStartDateTime));
    }

    @Test
    public void shouldReturnEventsForGroupAfterDate() {
        User user = userRepository.save(new User("0827654321"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant pastDate = Instant.now().minus(10, MINUTES);
        Event pastEvent = eventRepository.save(new Meeting("past event", pastDate, user, group,"someLoc", false));
        Instant futureDate = pastDate.plus(20, MINUTES);
        pastEvent = eventRepository.save(pastEvent);
        Event futureEvent = eventRepository.save(new Meeting("future event", futureDate, user,group,"someLoc"));
        futureEvent = eventRepository.save(futureEvent);
        Event futureEventCan = eventRepository.save(new Meeting("future event cancelled", futureDate, user, group, "someLocation"));
        futureEventCan.setCanceled(true);
        futureEventCan = eventRepository.save(futureEventCan);

        //
        List<Event> list = eventRepository.findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(group, Instant.now(), false);
        assertEquals(1, list.size());
        assertEquals("future event", list.get(0).getName());
    }

    @Test
    public void shouldReturnSameObjectOnSecondUpdate() {
        User user = userRepository.save(new User("085551234","test dup event user"));
        Group group = groupRepository.save(new Group("test dup event",user));
        Meeting event = (Meeting) eventRepository.save(new Meeting("duplicate event test", Instant.now(), user, group, "someLoc"));
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

        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(Instant.now());
        assertEquals(1,list.size());

    }

    @Test
    public void shouldNotFindOnePastVote() {
        User user = userRepository.save(new User("0831111113"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant expiry = Instant.now().truncatedTo(HOURS);
        Event vote = eventRepository.save(new Vote("testing vote query", expiry, user, group, true));
        vote = eventRepository.save(vote);
        List<Event> list = eventRepository.findAllVotesAfterTimeStamp(Instant.now());
        assertEquals(0,list.size());
    }

    @Test
    public void shouldNotFindMeetingWhenLookingForVote() {
        User user = userRepository.save(new User("0831111114"));
        Group group = groupRepository.save(new Group("events for group test",user));

        Instant expiry = Instant.now().truncatedTo(HOURS).plus(1, HOURS);

        Event meeting = eventRepository.save(new Meeting("testing vote query", expiry, user, group, "somewhere"));
        meeting = eventRepository.save(meeting);
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

        Event event1 = eventRepository.save(new Meeting("test", Instant.now(), user2, group, "someLoc"));

        Group group2 = groupRepository.save(new Group("tg2", user2));
        group2.addMember(user2);
        group2 = groupRepository.save(group2);
        Event event2 = eventRepository.save(new Meeting("test2", Instant.now(), user2, group2, "someLoc"));

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

        Event event1 = eventRepository.save(new Meeting("test", Instant.now().plus(7, DAYS), user, group, "someLoc"));
        event1 = eventRepository.save(event1);

        Event event2 = eventRepository.save(new Vote("test2", Instant.now().minus(7, DAYS), user, group));
        event2 = eventRepository.save(event2);

        Event event3 = eventRepository.save(new Meeting("test3", Instant.now().plus(7, DAYS), user, group, "someLoc"));
        event3.setCanceled(true);
        event3 = eventRepository.save(event3);
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

        Event event1 = new Meeting("count check", Instant.now().plus(2, DAYS), user, group, "someLoc");
        Event event2 = new Meeting("count check 2", Instant.now().minus(2, DAYS), user, group2, "someLoc");

        event1 = eventRepository.save(event1);
        event2 = eventRepository.save(event2);

        int numberUpcomingEvents1 = eventRepository.countByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThan(user, Instant.now());
        assertThat(numberUpcomingEvents1, is(1));
        int numberUpcomingEvents2 = eventRepository.countByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThan(user2, Instant.now());
        assertThat(numberUpcomingEvents2, is(0));

    }

}
