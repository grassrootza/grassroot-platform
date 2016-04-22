package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EventLogRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventLogRepository eventLogRepository;


    @Test
    public void shouldSaveAndRetrieveEventLogEventNotification() throws Exception {

        User user = userRepository.save(new User("001111111"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        User user2 = userRepository.save(new User("00111112"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting",Instant.now(),  user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the test meeting"));
        List<EventLog> list = eventLogRepository.findByEventLogTypeAndEventAndUser(EventLogType.EventNotification, event, user);
        assertEquals(1, list.size());
        EventLog dbEventLog = list.get(0);
        assertEquals(event.getId(), dbEventLog.getEvent().getId());
        assertEquals("you are hereby invited to the test meeting", dbEventLog.getMessage());
    }

    @Test
    public void shouldSaveAndNotRetrieveEventLogEventNotification() throws Exception {
        User user = userRepository.save(new User("001111113"));
        Group group = groupRepository.save(new Group("test eventlog 2", user));
        User user2 = userRepository.save(new User("00111114"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 2", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.EventReminder, "you are reminded about the test meeting"));
        List<EventLog> list = eventLogRepository.findByEventLogTypeAndEventAndUser(EventLogType.EventReminder,event,user);
        assertEquals(0, list.size());
    }

    @Test
    public void shouldSayNotificationSent() throws Exception {
        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test eventlog 3", user));
        User user2 = userRepository.save(new User("00111116"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 3", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are invited to test meeting 3"));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.notificationSent(event, user).toString()));
    }

    @Test
    public void shouldSayNotificationNotSent() throws Exception {
        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test eventlog 4", user));
        User user2 = userRepository.save(new User("00111116"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 4", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.EventNotification, "you are invited to test meeting 4"));
        assertEquals(false, Boolean.parseBoolean(eventLogRepository.notificationSent(event, user).toString()));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.notificationSent(event, user2).toString()));
    }

    @Test
    public void shouldSayReminderSent() throws Exception {
        User user = userRepository.save(new User("001111117"));
        User user2 = userRepository.save(new User("00111118"));
        Group group = new Group("test eventlog 5", user);
        group.addMember(user);
        group.addMember(user2);
        groupRepository.save(group);
        Event event = eventRepository.save(new Meeting("test meeting 5", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.EventReminder, "you are hereby reminded about test meeting 5"));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.reminderSent(event, user).toString()));
    }

    @Test
    public void shouldSayReminderNotSent() throws Exception {

        User user = userRepository.save(new User("001111119"));
        Group group = groupRepository.save(new Group("test meeting 6", user));
        User user2 = userRepository.save(new User("00111110"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 6", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.EventReminder, "you are reminded about test meeting 6"));
        assertEquals(false, Boolean.parseBoolean(eventLogRepository.reminderSent(event, user).toString()));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.reminderSent(event, user2).toString()));
    }

    @Test
    public void shouldReturnMinutesForEvent() {
        User user = userRepository.save(new User("001111120"));
        Group group = groupRepository.save(new Group("test minutes 1", user));
        User user2 = userRepository.save(new User("001111121"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 7", Instant.now(), user, group, "someLoc"));
        EventLog elog1 = eventLogRepository.save(new EventLog(user, event, EventLogType.EventMinutes, "item 1"));
        EventLog elog2 = eventLogRepository.save(new EventLog(user, event, EventLogType.EventMinutes, "item 2"));
        EventLog enot = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "notification message"));
    }

    @Test
    public void shouldReturnThatUserRSVPNo() {
        User user = userRepository.save(new User("0121234567"));
        Group group = groupRepository.save(new Group("RSVP group",user));
        Event event = eventRepository.save(new Meeting("répondez s'il vous plaît", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.EventRSVP, "No"));
        assertEquals(true,eventLogRepository.rsvpNoForEvent(event,user));
    }
    @Test
    public void shouldReturnFalseForRSVPNo() {
        User user = userRepository.save(new User("0121234577"));
        Group group = groupRepository.save(new Group("RSVP group 2",user));
        Event event = eventRepository.save(new Meeting("répondez s'il vous plaît duo", Instant.now(), user,group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.EventRSVP, "Yes"));
        assertEquals(false,eventLogRepository.rsvpNoForEvent(event,user));
    }
}
