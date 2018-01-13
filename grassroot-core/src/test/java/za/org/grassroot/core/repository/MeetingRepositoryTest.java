package za.org.grassroot.core.repository;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.EventLogType;

import javax.transaction.Transactional;
import java.time.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class MeetingRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(MeetingRepositoryTest.class);

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Test
    public void shouldFindEventsByGroupBetweenTimestamps() {

        assertThat(meetingRepository.count(), is(0L));
        User user = userRepository.save(new User("0813330000", null, null));
        Group group1 = groupRepository.save(new Group("tg1", user));
        Group group2 = groupRepository.save(new Group("tg2", user));

        Event event1 = meetingRepository.save(new MeetingBuilder().setName("test").setStartDateTime(Instant.now().minus(7, DAYS)).setUser(user).setParent(group1).setEventLocation("someLoc").createMeeting());
        Event event2 = meetingRepository.save(new MeetingBuilder().setName("test2").setStartDateTime(Instant.now().minus(5 * 7, DAYS)).setUser(user).setParent(group1).setEventLocation("someLoc").createMeeting());
        Event event3 = voteRepository.save(new Vote("test3", Instant.now().minus(7, DAYS), user, group1));
        Event event4 = meetingRepository.save(new MeetingBuilder().setName("test4").setStartDateTime(Instant.now().minus(7, DAYS)).setUser(user).setParent(group2).setEventLocation("someLoc").createMeeting());

        Instant now = Instant.now();
        Instant oneMonthBack = LocalDateTime.now().minusMonths(1L).toInstant(ZoneOffset.UTC);
        Instant twoMonthsBack = LocalDateTime.now().minusMonths(2L).toInstant(ZoneOffset.UTC);

        List<Meeting> test1 = meetingRepository.
                findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group1, oneMonthBack, now);
        List<Meeting> test2 = meetingRepository.
                findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group1, twoMonthsBack, oneMonthBack);
        List<Vote> test3 = voteRepository.
                findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group1, oneMonthBack, now);
        List<Meeting> test4 = meetingRepository.
                findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group2, oneMonthBack, now);
        List<Event> test5 = eventRepository.
                findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group1, oneMonthBack, now, new Sort(Sort.Direction.ASC, "EventStartDateTime"));

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
    public void findMeetingsForResponseMessagesShouldWork() {

        log.info("Finding meeting responses ...");

        User user = userRepository.save(new User("0710001111", null, null));
        Group group = groupRepository.save(new Group("tg1", user));
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);

        Meeting mtg = new MeetingBuilder().setName("count check").setStartDateTime(Instant.now().plus(2, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting();
        Meeting mtg2 = new MeetingBuilder().setName("count check 2").setStartDateTime(Instant.now().plus(2, DAYS)).setUser(user).setParent(group).setEventLocation("otherLoc").createMeeting();
        mtg.setRsvpRequired(true);
        mtg2.setRsvpRequired(true);

        meetingRepository.save(mtg);
        meetingRepository.save(mtg2);

        assertThat(meetingRepository.count(), is(2L));

        List<Meeting> checkFirst = meetingRepository.meetingsForResponseTotals(Instant.now(), Instant.now().minus(5, MINUTES), Instant.now());
        assertThat(checkFirst.size(), is(2));
        assertThat(checkFirst.contains(mtg), is(true));
        assertThat(checkFirst.contains(mtg2), is(true));

        EventLog eventLog = new EventLog(user, mtg, EventLogType.RSVP_TOTAL_MESSAGE);
        eventLogRepository.save(eventLog);
        assertThat(eventLogRepository.count(), is(1L));

        List<Meeting> checkSecond = meetingRepository.meetingsForResponseTotals(Instant.now(), Instant.now().minus(5, MINUTES), Instant.now());
        assertThat(checkSecond.size(), is(1));
        assertThat(checkSecond.contains(mtg), is(false));
        assertThat(checkSecond.contains(mtg2), is(true));
    }

    @Ignore
    @Test
    public void shouldFindMeetingsForThankYous() {

        assertThat(meetingRepository.count(), is(0L));

        User user = userRepository.save(new User("0710001111", null, null));
        Group group = groupRepository.save(new Group("tg2", user));
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);

        LocalDate yesterday = LocalDate.now().minus(1, DAYS);
        Instant start = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MIN), getSAST());
        Instant end = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MAX), getSAST());

        Meeting mtgYesterday = new MeetingBuilder().setName("check yesterday").setStartDateTime(Instant.now().minus(1, DAYS)).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting();
        mtgYesterday.setRsvpRequired(true);
        Meeting mtgTwoDaysAgo = new MeetingBuilder().setName("two days ago").setStartDateTime(Instant.now().minus(2, DAYS)).setUser(user).setParent(group).setEventLocation("anotherLoc").createMeeting();
        mtgTwoDaysAgo.setRsvpRequired(true);
        Meeting mtgNow = new MeetingBuilder().setName("check today").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("happening now").createMeeting();
        mtgNow.setRsvpRequired(true);

        meetingRepository.save(Arrays.asList(mtgYesterday, mtgTwoDaysAgo, mtgNow));
        List<Meeting> mtgs = meetingRepository.findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(start, end);

        assertThat(meetingRepository.count(), is(3L));
        assertThat(mtgs.size(), is(1));
        assertThat(mtgs.contains(mtgYesterday), is(true));
        assertThat(mtgs.contains(mtgTwoDaysAgo), is(false));
        assertThat(mtgs.contains(mtgNow), is(false));
    }

}
