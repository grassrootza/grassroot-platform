package za.org.grassroot.integration.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.NotificationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Created by luke on 2016/06/14.
 */
@Service
public class UnreadNotificationHandlerImpl implements UnreadNotificationHandler {

    private static final Logger logger = LoggerFactory.getLogger(UnreadNotificationHandler.class);

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private MessageSendingManager messageSendingManager;

    @Override
    @Transactional
    public void processUnreadNotifications() {
        logger.info("Processing unread notifications ...");
        Instant time = Instant.now();
        Instant timeToCheck = Instant.now().minus(10, ChronoUnit.MINUTES);

        // need to only check for those attempt, else may send before user has chance to view
        // note : do the check on read, not viewed on android, because we want to preserve that as false but mark to read on SMS send (to avoid repeat deliveries)
        List<Notification> unreadNotifications = notificationRepository
                .findFirst100ByReadFalseAndAttemptCountGreaterThanAndLastAttemptTimeGreaterThan(0, timeToCheck);
        if (unreadNotifications.size() > 0) {
            logger.info("Sending {} unread notifications", unreadNotifications.size());
        }

        for (Notification notification : unreadNotifications) {
            logger.info("Routing notification to SMS ...");
            messageSendingManager.sendMessage(UserMessagingPreference.SMS.name(), notification);
        }
    }

}
