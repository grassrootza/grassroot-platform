package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.enums.EventLogType;

import javax.transaction.Transactional;
import java.time.Instant;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class EventLogRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Test
    public void shouldSaveAndRetrieveEventLogEventNotification() throws Exception {
        assertEquals(0, eventLogRepository.count());
        User user = userRepository.save(new User("001111111", null, null));
        Group group = groupRepository.save(new Group("test eventlog", user));
        User user2 = userRepository.save(new User("00111112", null, null));
        group.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        assertEquals(1, eventLogRepository.count());
        EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.CREATED);
        assertNotNull(eventLog);
        assertTrue(eventLog.getEvent().equals(event));
    }

    @Test
    public void shouldSaveAndNotRetrieveEventLogEventNotification() throws Exception {
        assertEquals(0, eventLogRepository.count());
        User user = userRepository.save(new User("001111113", null, null));
        Group group = groupRepository.save(new Group("test eventlog 2", user));
        User user2 = userRepository.save(new User("00111114", null, null));
        group.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 2").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        eventLogRepository.save(new EventLog(user2, event, EventLogType.REMINDER));
        assertEquals(1, eventLogRepository.count());
        EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.REMINDER);
        assertNull(eventLog);
        EventLog eventLog1 = eventLogRepository.findByEventAndUserAndEventLogType(event, user2, EventLogType.REMINDER);
        assertNotNull(eventLog1);
    }



    @Test
    public void shouldSayReminderSent() throws Exception {
        User user = userRepository.save(new User("001111117", null, null));
        User user2 = userRepository.save(new User("00111118", null, null));
        Group group = new Group("test eventlog 5", user);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        group.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 5").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = new EventLog(user, event, EventLogType.REMINDER);
        eventLogRepository.save(eventLog);
        Boolean reminderSent = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.REMINDER) != null;
        assertEquals(true, Boolean.parseBoolean(reminderSent.toString()));
    }

    @Test
    public void shouldSayReminderNotSent() throws Exception {

        User user = userRepository.save(new User("001111119", null, null));
        Group group = groupRepository.save(new Group("test meeting 6", user));
        User user2 = userRepository.save(new User("00111110", null, null));
        group.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 6").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        Boolean aBoolean = eventLogRepository.findByEventAndUserAndEventLogType(event, user2, EventLogType.REMINDER) != null;
        assertEquals(false, Boolean.parseBoolean(aBoolean.toString()));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.REMINDER));
        Boolean sent = eventLogRepository.findByEventAndUserAndEventLogType(event, user2, EventLogType.REMINDER) != null;
        assertEquals(true, Boolean.parseBoolean(sent.toString()));
    }

    @Test
    public void shouldStoreCancelledStatus() {
        User user = userRepository.save(new User("001111120", null, null));
        Group group = groupRepository.save(new Group("test minutes 1", user));
        User user2 = userRepository.save(new User("001111121", null, null));
        group.addMember(user2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 7").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        event.setCanceled(true);
        eventRepository.save(event);
        eventLogRepository.save(new EventLog(user, event, EventLogType.CANCELLED));
        assertEquals(true, (eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.CANCELLED)) != null);
        assertEquals(false, (eventLogRepository.findByEventAndUserAndEventLogType(event, user2, EventLogType.CANCELLED) != null));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.CANCELLED));
        assertEquals(true, (eventLogRepository.findByEventAndUserAndEventLogType(event, user2, EventLogType.CANCELLED) != null));
    }
}
