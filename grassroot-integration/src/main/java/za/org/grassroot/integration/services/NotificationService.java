package za.org.grassroot.integration.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.NotificationDTO;

import java.util.List;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationService {

	Notification loadNotification(String uid);

	Page<Notification> getUserNotifications(User user, int pageNumber, int pageSize);

	List<NotificationDTO> fetchNotificationDTOs(List<String> notificationUids);

	void updateNotificationReadStatus(String notificationUid, boolean read);

	void updateNotificationDeliveryStatus(String notificationUid, boolean delivered);

	void resendNotDelivered();
}
