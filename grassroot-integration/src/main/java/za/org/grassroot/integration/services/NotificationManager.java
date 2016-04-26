package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.NotificationDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.NotificationRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by paballo on 2016/04/07.
 */

@Service
public class NotificationManager implements NotificationService{

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    private MessageSendingService messageSendingService;

    private static final Set<EventLogType> eventLogTypesToIncludeInList =
            Collections.unmodifiableSet(Stream.of(EventLogType.EventNotification, EventLogType.EventChange,
                                                  EventLogType.EventNotification).collect(Collectors.toSet()));

    @Override
    @Transactional
    public Notification loadNotification(String uid) {
        Objects.nonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(User user, int pageNumber, int pageSize) {
        return notificationRepository.findByUser(user, new PageRequest(pageNumber,pageSize));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> fetchNotificationDTOs(List<String> notificationUids) {
        List<NotificationDTO> notificationDTOs = new ArrayList<>();
        for (String uid : notificationUids) {
            Notification notification = notificationRepository.findByUid(uid);
            if (notification.getNotificationType().equals(NotificationType.EVENT)) {
                EventLog eventLog = notification.getEventLog();
                if (eventLog.getEvent() != null && eventLogTypesToIncludeInList.contains(eventLog.getEventLogType()))
                    notificationDTOs.add(new NotificationDTO(notification, eventLog.getEvent()));
            } else if(notification.getNotificationType().equals(NotificationType.LOGBOOK)){
                LogBook logBook = logBookRepository.findOne(notification.getLogBookLog().getLogBook().getId());
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
