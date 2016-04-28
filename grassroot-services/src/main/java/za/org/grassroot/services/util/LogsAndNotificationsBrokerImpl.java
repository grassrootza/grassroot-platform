package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.integration.services.MessageSendingService;

import java.util.Objects;
import java.util.Set;

@Service
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {
	private final Logger logger = LoggerFactory.getLogger(LogsAndNotificationsBrokerImpl.class);

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private GroupLogRepository groupLogRepository;

	@Autowired
	private UserLogRepository userLogRepository;

	@Autowired
	private EventLogRepository eventLogRepository;

	@Autowired
	private LogBookLogRepository logBookLogRepository;

	@Autowired
	private MessageSendingService messageSendingService;

	@Override
	@Transactional
	@Async
	public void asyncStoreBundle(LogsAndNotificationsBundle bundle) {
		storeBundle(bundle);
	}

	@Override
	@Transactional
	public void storeBundle(LogsAndNotificationsBundle bundle) {
		Objects.requireNonNull(bundle);

		Set<ActionLog> logs = bundle.getLogs();
		logger.info("Storing {} logs", logs.size());
		for (ActionLog log : logs) {
			logger.info("Saving log {}", log);
			saveLog(log);
		}

		Set<Notification> notifications = bundle.getNotifications();
		logger.info("Storing {} notifications", notifications.size());
		for (Notification notification : notifications) {
			logger.info("Saving notification: {}", notification);
			notificationRepository.save(notification);
		}

		// todo: this should be removed eventually - better for some scheduled job(s) to
		// frequently take unprocessed notifications and send them to destinations
		for (Notification notification : notifications) {
			messageSendingService.sendMessage(notification);
		}
	}

	private void saveLog(ActionLog actionLog) {
		if (actionLog instanceof GroupLog) {
			groupLogRepository.save((GroupLog) actionLog);
		}
		if (actionLog instanceof UserLog) {
			userLogRepository.save((UserLog) actionLog);
		}
		if (actionLog instanceof EventLog) {
			eventLogRepository.save((EventLog) actionLog);
		}
		if (actionLog instanceof LogBookLog) {
			logBookLogRepository.save((LogBookLog) actionLog);
		}
	}
}
