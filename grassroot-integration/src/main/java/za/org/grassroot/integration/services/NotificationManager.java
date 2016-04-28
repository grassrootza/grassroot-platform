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

import java.time.Instant;
import java.util.*;

/**
 * Created by paballo on 2016/04/07.
 */

@Service
public class NotificationManager implements NotificationService{
    private final Logger logger = LoggerFactory.getLogger(NotificationManager.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MessageSendingService messageSendingService;

    private static final Set<Class<? extends EventNotification>> eventLogTypesToIncludeInList = Collections.unmodifiableSet(
            Sets.newHashSet(EventInfoNotification.class, EventChangedNotification.class));

    @Override
    @Transactional
    public Notification loadNotification(String uid) {
        Objects.nonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(User user, int pageNumber, int pageSize) {
        return notificationRepository.findByTarget(user, new PageRequest(pageNumber,pageSize));
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
