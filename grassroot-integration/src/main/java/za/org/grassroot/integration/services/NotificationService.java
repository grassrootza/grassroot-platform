package za.org.grassroot.integration.services;

import za.org.grassroot.core.domain.*;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationService {

    Notification loadNotification(String uid);

    Notification createNotification(User user, EventLog eventLog, Instant createdDateTime);

    Notification createNotification(User user, LogBookLog logBookLog, Instant createdDateTime);

    void updateNotificationReadStatus(String notificationUid, boolean read);

    void updateNotificationDeliveryStatus(String notificationUid, boolean delivered);

    void resendNotDelivered();
}
