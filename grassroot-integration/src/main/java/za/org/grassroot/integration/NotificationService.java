package za.org.grassroot.integration;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.NotificationStatus;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;

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

	List<Notification> fetchSentOrBetterSince(String userUid, Instant sentSince, UserMessagingPreference deliveryChannel);

	void updateNotificationsViewedAndRead(Set<String> notificationUids);

	int countUnviewedAndroidNotifications(String targetUid);

	void markNotificationAsDelivered(String notificationUid);

    Notification loadBySeningKey(String sendingKey);

	List<Notification> loadRecentFailedNotificationsInGroup(LocalDateTime from, LocalDateTime to, Group group);

	void updateNotificationStatus(String notificationUid, NotificationStatus status, String errorMessage, String messageSendKey);

}
