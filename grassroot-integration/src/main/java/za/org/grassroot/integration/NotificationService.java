package za.org.grassroot.integration;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationService {

	Notification loadNotification(String uid);

	Page<Notification> fetchPagedAndroidNotifications(User target, int pageNumber, int pageSize);

	List<Notification> fetchAndroidNotificationsSince(String userUid, Instant createdSince);

	void updateNotificationsViewedAndRead(Set<String> notificationUids);

	int countUnviewedAndroidNotifications(String targetUid);

	void markNotificationAsDelivered(String notificationUid);

}
