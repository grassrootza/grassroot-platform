package za.org.grassroot.core.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.EventCancelledNotification;
import za.org.grassroot.core.enums.EventLogType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Created by paballo on 2016/04/11.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

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
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433"));
        notificationRepository.save(new EventCancelledNotification(user, "blah", eventLog));
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        assertEquals(notifications.get(0).getEventLog(), eventLog);

    }

    @Test
    public void shouldFindByUser() throws Exception{
        User user = userRepository.save(new User("0848835097"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        Event event = eventRepository.save(new Meeting("test meeting",Instant.now(),  user, group, "someLoc"));
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433"));
        notificationRepository.save(new EventCancelledNotification(user, "blah", eventLog));
        List<Notification> notifications = notificationRepository.findByTargetAndForAndroidTimelineTrueOrderByCreatedDateTimeDesc(user);
        Assert.assertThat(notifications.size(), is(1));

    }



}
