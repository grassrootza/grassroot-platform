package za.org.grassroot.core.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.notification.*;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.specifications.NotificationSpecifications;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

/**
 * Created by paballo on 2016/04/11.
 */
@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
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
    private TodoRepository todoRepository;

    @Autowired
    private TodoLogRepository todoLogRepository;

    @Autowired
    private UserLogRepository userLogRepository;

    @Test
    public void shouldSaveAndRetrieveNotifications() {
        assertThat(notificationRepository.count(), is(0L));
        User user = userRepository.save(new User("08488754097", null, null));
        Group group = groupRepository.save(new Group("test eventlog", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        GcmRegistration gcmRegistration = gcmRegistrationRepository.save(new GcmRegistration(user, "33433"));
        notificationRepository.save(new EventCancelledNotification(user, "blah", eventLog));
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        assertEquals(notifications.get(0).getEventLog(), eventLog);
    }

    @Test
    public void shouldCountNotificationsByAncestorGroup() {
        assertThat(notificationRepository.count(), is(0L));
        Instant start = Instant.now();
        User user = userRepository.save(new User("08488754097", null, null));
        Group group = groupRepository.save(new Group("test eventlog", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        notificationRepository.save(new EventInfoNotification(user, "hello", eventLog));
        long notificationCount = notificationRepository.count(NotificationSpecifications.ancestorGroupIs(group));
        assertEquals(1, notificationCount);
        long countTime = notificationRepository.count(NotificationSpecifications.ancestorGroupIsTimeLimited(group, start));
        assertEquals(1, countTime);
        Instant start2 = Instant.now();
        Todo todo = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "do things", Instant.now()));
        TodoLog todoLog = todoLogRepository.save(new TodoLog(TodoLogType.CREATED, user, todo, "hello"));
        notificationRepository.save(new TodoInfoNotification(user, "hello", todoLog));
        long count3 = notificationRepository.count(NotificationSpecifications.ancestorGroupIsTimeLimited(group, start));
        assertEquals(2, count3);
        long count4 = notificationRepository.count(NotificationSpecifications.ancestorGroupIsTimeLimited(group, start2));
        assertEquals(1, count4);
    }

    @Test
    public void shouldFindByUser() {
        User user = userRepository.save(new User("0848835097", null, null));
        user.setMessagingPreference(DeliveryRoute.ANDROID_APP);
        userRepository.save(user);
        Group group = groupRepository.save(new Group("test eventlog", GroupPermissionTemplate.DEFAULT_GROUP, user));
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        notificationRepository.save(new EventCancelledNotification(user, "blah", eventLog));
        Page<Notification> notifications = notificationRepository
                .findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(user, DeliveryRoute.ANDROID_APP, PageRequest.of(0, 1));
        assertThat(notifications.getTotalElements(), is(1L));
    }

    @Test
    public void shouldFetchNotificatonsToDeliver() {
        assertEquals(0, notificationRepository.count());
        User user = userRepository.save(new User("001111115", null, null));
        Group group = groupRepository.save(new Group("test notification 3", GroupPermissionTemplate.DEFAULT_GROUP, user));
        User user2 = userRepository.save(new User("00111116", null, null));
        group.addMember(user2, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Event event = eventRepository.save(new MeetingBuilder().setName("test meeting 3").setStartDateTime(Instant.now()).setUser(user).setParent(group).setEventLocation("someLoc").createMeeting());
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        Notification notification = new EventInfoNotification(user, "test meeting called", eventLog);
        notification.setStatus(NotificationStatus.DELIVERED);
        notificationRepository.save(notification);
        EventLog eventLog1 = eventLogRepository.save(new EventLog(user2, event, EventLogType.CREATED));
        Notification notification1 = new EventInfoNotification(user2, "test meeting called", eventLog1);
        notificationRepository.save(notification1);

        List<Notification> notifications = notificationRepository
                .findAll(NotificationSpecifications.notificationsForSending());
        assertNotNull(notifications);
        assertEquals(1, notifications.size());
        assertFalse(notifications.contains(notification));
        assertTrue(notifications.contains(notification1));
    }

    @Test
    public void shouldSaveAndRetrieveUnreadNotifications() {
        assertEquals(0, notificationRepository.count());
        User user = userRepository.save(new User("0801112345", null, null));
        Group group = groupRepository.save(new Group("test notification", GroupPermissionTemplate.DEFAULT_GROUP, user));

        User user2 = userRepository.save(new User("0701112345", null, null));
        group.addMember(user2, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        UserLog userLog = userLogRepository.save(new UserLog(user2.getUid(), UserLogType.INITIATED_USSD, "welcome to grassroot", UserInterfaceType.USSD));

        Event event = eventRepository.save(new Vote("test notifications", Instant.now().plus(1, ChronoUnit.DAYS), user, group));
        EventLog eventLog1 = eventLogRepository.save(new EventLog(user, event, EventLogType.CREATED));
        EventLog eventLog2 = eventLogRepository.save(new EventLog(user2, event, EventLogType.CREATED));

        Notification notification0 = new WelcomeNotification(user2, "welcome to grassroot", userLog);
        Notification notification1 = new EventInfoNotification(user, "vote on test notifications", eventLog1);
        Notification notification2 = new EventInfoNotification(user2, "vote on test notifications", eventLog2);

        notification1.setDeliveryChannel(DeliveryRoute.ANDROID_APP);
        notification1.updateStatus(NotificationStatus.SENT, true, false, null);
        notification2.setDeliveryChannel(DeliveryRoute.ANDROID_APP);
        notification2.updateStatus(NotificationStatus.SENT, true, false, null);

        notificationRepository.save(notification0);
        notificationRepository.save(notification1);
        notificationRepository.save(notification2);

        List<Notification> notifications1 = notificationRepository
                .findAll(NotificationSpecifications.notificationsForSending());
        List<Notification> notifications2 = notificationRepository
                .findAll(NotificationSpecifications.unreadAndroidNotifications());

        assertEquals(1, notifications1.size());
        assertEquals(2, notifications2.size());
        assertTrue(notifications2.contains(notification2));
        assertFalse(notifications2.contains(notification0));
        assertTrue(notifications2.contains(notification1));
    }

}
