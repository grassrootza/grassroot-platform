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
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.notification.EventCancelledNotification;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.notification.WelcomeNotification;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

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

    @Autowired
    private UserLogRepository userLogRepository;

    @Test
    public void shouldSaveAndRetrieveNotifications() throws Exception{

        assertThat(notificationRepository.count(), is(0L));
        User user = userRepository.save(new User("08488754097"));
        Group group = groupRepository.save(new Group("test eventlog", user));
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
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
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433"));
        notificationRepository.save(new EventCancelledNotification(user, "blah", eventLog));
        List<Notification> notifications = notificationRepository.findByTargetAndForAndroidTimelineTrueOrderByCreatedDateTimeDesc(user);
        Assert.assertThat(notifications.size(), is(1));
    }

    @Test
    public void shouldFetchNotificatonsToDeliver() throws Exception {
        assertEquals(0, notificationRepository.count());
        User user = userRepository.save(new User("001111115"));
        Group group = groupRepository.save(new Group("test notification 3", user));
        User user2 = userRepository.save(new User("00111116"));
        group.addMember(user2);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 3").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        Notification notification = new EventInfoNotification(user, "test meeting called", eventLog);
        notification.markAsDelivered();
        notificationRepository.save(notification);
        EventLog eventLog1 = eventLogRepository.save(new EventLog(user2, event, EventLogType.CREATED));
        Notification notification1 = new EventInfoNotification(user2, "test meeting called", eventLog1);
        notification1.setNextAttemptTime(Instant.now().minus(60, ChronoUnit.SECONDS));
        notificationRepository.save(notification1);

        List<Notification> notifications = notificationRepository.findFirst75ByNextAttemptTimeBeforeOrderByNextAttemptTimeAsc(Instant.now());
        assertNotNull(notifications);
        assertEquals(1, notifications.size());
        assertFalse(notifications.contains(notification));
        assertTrue(notifications.contains(notification1));
    }

    @Test
    public void shouldSaveAndRetrieveUnreadNotifications() throws Exception {
        assertEquals(0, notificationRepository.count());
        User user = userRepository.save(new User("0801112345"));
        Group group = groupRepository.save(new Group("test notification", user));

        User user2 = userRepository.save(new User("0701112345"));
        group.addMember(user2);
        UserLog userLog = userLogRepository.save(new UserLog(user2.getUid(), UserLogType.INITIATED_USSD, "welcome to grassroot", UserInterfaceType.USSD));

        Event event = eventRepository.save(new Vote("test notifications", Instant.now().plus(1, ChronoUnit.DAYS), user, group));
        EventLog eventLog1 = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        EventLog eventLog2 = eventLogRepository.save(new EventLog(user2, event, EventLogType.CREATED));

        Notification notification0 = new WelcomeNotification(user2, "welcome to grassroot", userLog);
        Notification notification1 = new EventInfoNotification(user, "vote on test notifications", eventLog1);
        Notification notification2 = new EventInfoNotification(user2, "vote on test notifications", eventLog2);

        notification0.setNextAttemptTime(Instant.now().plus(2, ChronoUnit.DAYS));

        notification1.setLastAttemptTime(Instant.now());
        notification1.markAsDelivered();
        notification1.incrementAttemptCount();
        notification1.markReadAndViewed();

        notification2.markAsDelivered();
        notification2.incrementAttemptCount();
        notification2.setLastAttemptTime(Instant.now());

        notificationRepository.save(notification0);
        notificationRepository.save(notification1);
        notificationRepository.save(notification2);

        List<Notification> notifications1 = notificationRepository.findFirst75ByNextAttemptTimeBeforeOrderByNextAttemptTimeAsc(Instant.now());
        List<Notification> notifications2 = notificationRepository
                .findFirst100ByReadFalseAndAttemptCountGreaterThanAndLastAttemptTimeGreaterThan(0, Instant.now().minus(10, ChronoUnit.MINUTES));

        assertEquals(0, notifications1.size());
        assertEquals(1, notifications2.size());
        assertTrue(notifications2.contains(notification2));
        assertFalse(notifications2.contains(notification0));
        assertFalse(notifications2.contains(notification1));
    }

}
