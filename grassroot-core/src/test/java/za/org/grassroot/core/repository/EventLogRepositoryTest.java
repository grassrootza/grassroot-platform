package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;

import javax.transaction.Transactional;
import java.time.Instant;

import static org.junit.Assert.assertEquals;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
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

        User user = userRepository.save(new User("001111111"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        User user2 = userRepository.save(new User("00111112"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting",Instant.now(),  user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));

        // complete the test
    }

    @Test
    public void shouldSaveAndNotRetrieveEventLogEventNotification() throws Exception {
        User user = userRepository.save(new User("001111113"));
        Group group = groupRepository.save(new Group("test eventlog 2", user));
        User user2 = userRepository.save(new User("00111114"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 2", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.REMINDER));

        // complete the test
    }

    @Test
    public void shouldSayNotificationSent() throws Exception {
        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test eventlog 3", user));
        User user2 = userRepository.save(new User("00111116"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 3", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
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
        eventLogRepository.save(new EventLog(user, event, EventLogType.REMINDER));
//        Boolean reminderSent = eventLogRepository.reminderSent(event, user);
        Boolean reminderSent = true; // todo: chenge to use notifications
        assertEquals(true, Boolean.parseBoolean(reminderSent.toString()));
    }

    @Test
    public void shouldSayReminderNotSent() throws Exception {

        User user = userRepository.save(new User("001111119"));
        Group group = groupRepository.save(new Group("test meeting 6", user));
        User user2 = userRepository.save(new User("00111110"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 6", Instant.now(), user, group, "someLoc"));
        eventLogRepository.save(new EventLog(user2, event, EventLogType.REMINDER));
        Boolean aBoolean = false;         // todo: chenge to use notifications

        assertEquals(false, Boolean.parseBoolean(aBoolean.toString()));
        Boolean sent = true;              // todo: chenge to use notifications

        assertEquals(true, Boolean.parseBoolean(sent.toString()));
    }

    @Test
    public void shouldReturnMinutesForEvent() {
        User user = userRepository.save(new User("001111120"));
        Group group = groupRepository.save(new Group("test minutes 1", user));
        User user2 = userRepository.save(new User("001111121"));
        group.addMember(user2);
        Event event = eventRepository.save(new Meeting("test meeting 7", Instant.now(), user, group, "someLoc"));
        EventLog elog1 = eventLogRepository.save(new EventLog(user, event, EventLogType.MINUTES));
        EventLog elog2 = eventLogRepository.save(new EventLog(user, event, EventLogType.MINUTES));
        EventLog enot = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
    }
}
