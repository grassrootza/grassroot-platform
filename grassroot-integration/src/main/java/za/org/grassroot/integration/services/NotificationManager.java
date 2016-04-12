package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.NotificationRepository;


import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/04/07.
 */

@Service
public class NotificationManager implements NotificationService{
    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    MessageSendingService messageSendingService;

    @Autowired
    GcmService gcmService;


    @Override
    @Transactional
    public Notification loadNotification(String uid) {
        Objects.nonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional
    public Notification createNotification(User user, EventLog eventLog, NotificationType notificationType, Instant createdDateTime) {
        Objects.nonNull(user);
        Objects.nonNull(eventLog);
        Objects.nonNull(createdDateTime);
        GcmRegistration gcmRegistration = gcmService.loadByUser(user);
        Notification notification = new Notification(user,eventLog,gcmRegistration,false,false,notificationType,createdDateTime);
        return notificationRepository.save(notification);

    }

    @Override
    public Notification createNotification(User user, LogBookLog logBookLog, NotificationType notificationType, Instant createdDateTime) {
        Objects.nonNull(user);
        Objects.nonNull(logBookLog);
        Objects.nonNull(createdDateTime);
        GcmRegistration gcmRegistration = gcmService.loadByUser(user);
        Notification notification = new Notification(user,logBookLog,gcmRegistration,false,false, notificationType, createdDateTime);
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void updateNotificationReadStatus(String notificationUid, boolean read) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        notification.setRead(read);
        notificationRepository.save(notification);

    }

    @Override
    @Transactional
    public void updateNotificationDeliveryStatus(String notificationUid, boolean delivered) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        notification.setDelivered(delivered);
        notificationRepository.save(notification);

    }

    @Override
    @Transactional
    public void resendNotDelivered() {
        Instant fiveMinutesAgo = Instant.now().minusSeconds(301L);
        List<Notification> notifications = notificationRepository.findByCreatedDateTimeLessThanAndDelivered(fiveMinutesAgo,false);

        for(Notification notification: notifications){
            notification.setDelivered(true);
            notificationRepository.save(notification);
            messageSendingService.sendMessage(UserMessagingPreference.SMS.name(),notification);
        }

    }
}
