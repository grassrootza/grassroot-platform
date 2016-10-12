package za.org.grassroot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;

@Service
public class BatchedNotificationSenderImpl implements BatchedNotificationSender {
	private final Logger logger = LoggerFactory.getLogger(BatchedNotificationSenderImpl.class);

	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private NotificationService notificationService;

	/**
	 * Processed in non-transacted manner because we want to process each notification in separate transaction.
	 */
	@Override
	public void processPendingNotifications() {
		Instant time = Instant.now();
		List<Notification> notifications = notificationRepository.findFirst50ByNextAttemptTimeBeforeOrderByNextAttemptTimeAsc(time);
		if (notifications.size() > 0) {
			logger.debug("Sending {} registered notification(s)", notifications.size());
		}
		for (Notification notification : notifications) {
			logger.info("notification time" + notification.getNextAttemptTime());
			notificationService.sendNotification(notification.getUid());
		}
	}
}
