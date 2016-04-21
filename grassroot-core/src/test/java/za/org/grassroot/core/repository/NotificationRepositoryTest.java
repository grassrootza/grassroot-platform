package za.org.grassroot.core.repository;

import org.junit.Assert;
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
import za.org.grassroot.core.enums.NotificationType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Created by paballo on 2016/04/11.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Test
    public void shouldSaveAndRetrieveNotifications() throws Exception{

       assertThat(notificationRepository.count(), is(0L));
        User user = userRepository.save(new User("08488754097"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        Event event = eventRepository.save(new Meeting("test meeting",Instant.now(),  user, group, "someLoc"));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the test meeting", null));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433", Instant.now()));
        notificationRepository.save(new Notification(user,eventLog,gcmRegistration,false,false,NotificationType.EVENT,Instant.now()));
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        assertEquals(notifications.get(0).getEventLog(), eventLog);

    }

    @Test
    public void shouldFindByUser() throws Exception{
        User user = userRepository.save(new User("0848835097"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        Event event = eventRepository.save(new Meeting("test meeting",Instant.now(),  user, group, "someLoc"));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the test meeting", null));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433", Instant.now()));
        notificationRepository.save(new Notification(user,eventLog,gcmRegistration,false,false, NotificationType.EVENT,Instant.now()));
        List<Notification> notifications = notificationRepository.findByUser(user);
        Assert.assertThat(notifications.size(), is(1));

    }



}
