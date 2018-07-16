package za.org.grassroot.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.notification.TodoNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.DeliveryRoute;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationService {

	Notification loadNotification(String uid);

	Page<Notification> fetchPagedAndroidNotifications(User target, int pageNumber, int pageSize);

	List<Notification> fetchSentOrBetterSince(String userUid, Instant sentSince, DeliveryRoute deliveryChannel);

	void updateNotificationsViewedAndRead(Set<String> notificationUids);

	void markAllUserNotificationsRead(String userUid, Instant sinceTime); // need this else can be enormous query

	int countUnviewedAndroidNotifications(String targetUid);

	List<Notification> loadRecentFailedNotificationsInGroup(LocalDateTime from, LocalDateTime to, Group group);

	List<Notification> fetchUnreadUserNotifications(User target, Instant since, Sort sort);

	List<BroadcastNotification> loadAllNotificationsForBroadcast(Broadcast broadcast);

	List<BroadcastNotification> loadFailedNotificationsForBroadcast(String requestorUid, Broadcast broadcast);

	List<EventNotification> loadFailedNotificationForEvent(String requestorUid, Event event);

	List<TodoNotification> loadFailedNotificationForTodo(String requestorUid, Todo todo);

	long countFailedNotificationForEvent(String requestorUid, String eventUid);

	long countFailedNotificationForTodo(String requestorUid, String eventUid);

}
