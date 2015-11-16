package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


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
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again", user, group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the drinking again meeting"));
        List<EventLog> list = eventLogRepository.findByEventLogTypeAndEventAndUser(EventLogType.EventNotification, event, user);
        assertEquals(1, list.size());
        EventLog dbEventLog = list.get(0);
        assertEquals(event.getId(), dbEventLog.getEvent().getId());
        assertEquals("you are hereby invited to the drinking again meeting", dbEventLog.getMessage());


    }

    @Test
    public void shouldSaveAndNotRetrieveEventLogEventNotification() throws Exception {

        User user = userRepository.save(new User("001111113"));
        Group group = groupRepository.save(new Group("test eventlog 2", user));
        User user2 = userRepository.save(new User("00111114"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 2", user, group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the drinking again meeting"));
        List<EventLog> list = eventLogRepository.findByEventLogTypeAndEventAndUser(EventLogType.EventReminder,event,user);
        assertEquals(0, list.size());


    }
    @Test
    public void shouldSayNotificationSent() throws Exception {

        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test eventlog 3", user));
        User user2 = userRepository.save(new User("00111116"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 3", user, group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the drinking again meeting"));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.notificationSent(event, user).toString()));


    }
    @Test
    public void shouldSayNotificationNotSent() throws Exception {

        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test eventlog 3", user));
        User user2 = userRepository.save(new User("00111116"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 3", user, group));
        assertEquals(false, Boolean.parseBoolean(eventLogRepository.notificationSent(event, user).toString()));


    }

    @Test
    public void shouldSayReminderSent() throws Exception {

        User user = userRepository.save(new User("001111117"));
        Group group = groupRepository.save(new Group("test eventlog 4", user));
        User user2 = userRepository.save(new User("00111118"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 4", user, group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventReminder, "you are hereby reminded about the drinking again meeting"));
        assertEquals(true, Boolean.parseBoolean(eventLogRepository.reminderSent(event, user).toString()));


    }
    @Test
    public void shouldSayReminderNotSent() throws Exception {

        User user = userRepository.save(new User("001111119"));
        Group group = groupRepository.save(new Group("test eventlog 4", user));
        User user2 = userRepository.save(new User("00111110"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 4", user, group));
        assertEquals(false, Boolean.parseBoolean(eventLogRepository.reminderSent(event, user).toString()));


    }

    @Test
    public void shouldReturnMinutesForEvent() {
        User user = userRepository.save(new User("001111120"));
        Group group = groupRepository.save(new Group("test minutes 1", user));
        User user2 = userRepository.save(new User("001111121"));
        group.getGroupMembers().add(user2);
        Event event = eventRepository.save(new Event("drinking again 5", user, group));
        EventLog elog1 = eventLogRepository.save(new EventLog(user,event,EventLogType.EventMinutes,"item 1"));
        EventLog elog2 = eventLogRepository.save(new EventLog(user,event,EventLogType.EventMinutes,"item 2"));
        EventLog enot = eventLogRepository.save(new EventLog(user,event,EventLogType.EventNotification,"notification message"));
        List<EventLog> list = eventLogRepository.findByEventLogTypeAndEventOrderByIdAsc(EventLogType.EventMinutes, event);
        assertEquals(2, list.size());
        assertEquals("item 1",list.get(0).getMessage());

    }

    @Test
    public void shouldReturnThatUserRSVPNo() {
        User user = userRepository.save(new User("0121234567"));
        Group group = groupRepository.save(new Group("RSVP group",user));
        Event event = eventRepository.save(new Event("répondez s'il vous plaît",user,group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user,event,EventLogType.EventRSVP, EventRSVPResponse.NO.toString()));
        assertEquals(true,eventLogRepository.rsvpNoForEvent(event,user));

    }
    @Test
    public void shouldReturnFalseForRSVPNo() {
        User user = userRepository.save(new User("0121234577"));
        Group group = groupRepository.save(new Group("RSVP group 2",user));
        Event event = eventRepository.save(new Event("répondez s'il vous plaît duo",user,group));
        EventLog eventLog = eventLogRepository.save(new EventLog(user,event,EventLogType.EventRSVP, "#$*&^#& off"));
        assertEquals(false,eventLogRepository.rsvpNoForEvent(event,user));

    }

}
