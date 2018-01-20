package za.org.grassroot.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
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

	int countUnviewedAndroidNotifications(String targetUid);

	List<Notification> loadRecentFailedNotificationsInGroup(LocalDateTime from, LocalDateTime to, Group group);

	List<Notification> fetchUnreadUserNotifications(User target, Sort sort);

}
