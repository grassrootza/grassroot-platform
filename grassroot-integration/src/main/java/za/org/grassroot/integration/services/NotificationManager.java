package za.org.grassroot.integration.services;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.notification.EventChangedNotification;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.notification.LogBookNotification;
import za.org.grassroot.core.dto.NotificationDTO;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/04/07.
 */

@Service
public class NotificationManager implements NotificationService{
    private final Logger logger = LoggerFactory.getLogger(NotificationManager.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MessageSendingService messageSendingService;

    private static final Set<Class<? extends EventNotification>> eventLogTypesToIncludeInList = Collections.unmodifiableSet(
            Sets.newHashSet(EventInfoNotification.class, EventChangedNotification.class));

    @Override
    @Transactional(readOnly = true)
    public Notification loadNotification(String uid) {
        Objects.nonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getNotificationsByTarget(User target, int pageNumber, int pageSize) {
        return notificationRepository.findByTargetOrderByCreatedDateTimeDesc(target, new PageRequest(pageNumber, pageSize));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> fetchNotificationDTOs(List<String> notificationUids) {
        List<NotificationDTO> notificationDTOs = new ArrayList<>();
        for (String uid : notificationUids) {
            Notification notification = notificationRepository.findByUid(uid);
            if (eventLogTypesToIncludeInList.contains(notification.getClass())) {
                Event event = ((EventNotification) notification).getEvent();
                notificationDTOs.add(new NotificationDTO(notification, event));

            } else if(notification instanceof LogBookNotification){
                LogBook logBook = ((LogBookNotification) notification).getLogBook();
                notificationDTOs.add(new NotificationDTO(notification,logBook));
            }
        }
        return notificationDTOs;
    }

    @Override
    public List<NotificationDTO> fetchNotificationsSince(String userUid, Instant createdSince) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        List<Notification> notifications;
        if (createdSince != null) {
            notifications = notificationRepository.findByTargetAndCreatedDateTimeGreaterThan(user, createdSince);
        } else {
            notifications = notificationRepository.findByTarget(user);
        }

        // todo : sort these
        List<NotificationDTO> notificationDTOs = notifications.stream()
                .filter(NotificationDTO::isNotificationOfTypeForDTO)
                .map(NotificationDTO::convertToDto)
                .collect(Collectors.toList());

        return notificationDTOs;
    }

    @Override
    @Transactional
    public void updateNotificationReadStatus(String notificationUid, boolean read) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        notification.setRead(read);
    }

    @Override
    @Transactional
    public void markNotificationAsDelivered(String notificationUid) {
        Notification notification = notificationRepository.findByUid(notificationUid);
        if (notification != null) {
            notification.markAsDelivered();
        } else {
            logger.info("No notification under UID {}, possibly from another environment", notificationUid);
        }
    }

    @Override
    @Transactional
    public void sendNotification(String notificationUid) {
        Objects.requireNonNull(notificationUid);

        Instant now = Instant.now();

        Notification notification = notificationRepository.findByUid(notificationUid);
        logger.info("Sending notification: {}", notification);

        notification.incrementAttemptCount();
        notification.setLastAttemptTime(now);

        try {
            boolean redelivery = notification.getAttemptCount() > 1;
            if (redelivery) {
                notification.setNextAttemptTime(null); // this practically means we try to redeliver only once
                messageSendingService.sendMessage(UserMessagingPreference.SMS.name(), notification);
            } else {
                // we set next attempt (redelivery) time which will get erased in case delivery gets confirmed in the mean time
                notification.setNextAttemptTime(now.plusSeconds(60 * 15));
                messageSendingService.sendMessage(notification);
            }

        } catch (Exception e) {
            logger.error("Failed to send notification " + notification + ": " + e.getMessage(), e);
        }
    }
}
