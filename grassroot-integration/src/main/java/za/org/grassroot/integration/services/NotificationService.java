package za.org.grassroot.integration.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

import java.time.Instant;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationService {

    Notification loadNotification(String uid);

    Notification createNotification(User user, EventLog eventLog, NotificationType notificationType, String message, Instant createdDateTime);

    Page<Notification> getUserNotifications(User user,int pageNumber, int pageSize);

    Notification createNotification(User user, LogBookLog logBookLog, NotificationType notificationType, String message, Instant createdDateTime);

    void updateNotificationReadStatus(String notificationUid, boolean read);

    void updateNotificationDeliveryStatus(String notificationUid, boolean delivered);

    void resendNotDelivered();
}
