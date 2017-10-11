package za.org.grassroot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.NotificationStatus;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.NotificationSpecifications;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */

@Service
public class NotificationManager implements NotificationService{
    private final static Logger logger = LoggerFactory.getLogger(NotificationManager.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Notification loadNotification(String uid) {
        Objects.nonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> fetchPagedAndroidNotifications(User target, int pageNumber, int pageSize) {
        return notificationRepository.findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(target, UserMessagingPreference.ANDROID_APP, new PageRequest(pageNumber, pageSize));
    }

    @Override
    public List<Notification> fetchAndroidNotificationsSince(String userUid, Instant createdSince) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        List<Notification> notifications;
        if (createdSince != null) {
            notifications = notificationRepository.findByTargetAndDeliveryChannelAndCreatedDateTimeGreaterThanOrderByCreatedDateTimeDesc(user, UserMessagingPreference.ANDROID_APP, createdSince);
        } else {
            notifications = notificationRepository.findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(user, UserMessagingPreference.ANDROID_APP);
        }

        return notifications;
    }

    @Override
    @Transactional
    public void updateNotificationsViewedAndRead(Set<String> notificationUids) {
        List<Notification> notifications = notificationRepository.findByUidIn(notificationUids);
        notifications.forEach(n -> n.updateStatus(NotificationStatus.READ));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUnviewedAndroidNotifications(String targetUid) {
        User user = userRepository.findOneByUid(targetUid);
        return notificationRepository.countByTargetAndDeliveryChannelAndStatusNot(user, UserMessagingPreference.ANDROID_APP, NotificationStatus.READ);
    }

    @Override
    @Transactional
    public void markNotificationAsDelivered(String notificationUid) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        if (notification != null) {
            notification.updateStatus(NotificationStatus.DELIVERED);
        } else {
            logger.info("No notification under UID {}, possibly from another environment", notificationUid);
        }
    }

    @Override
    public Notification loadBySeningKey(String sendingKey) {
        return notificationRepository.findOne(NotificationSpecifications.getBySendingKey(sendingKey));
    }

    @Override
    @Transactional
    public void updateNotificationStatus(String notificationUid, NotificationStatus status, String errorMessage, String messageSendKey) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        if (notification != null) {
            notification.updateStatus(status);
            if (messageSendKey != null)
                notification.setSendingKey(messageSendKey);
        }
    }
}
