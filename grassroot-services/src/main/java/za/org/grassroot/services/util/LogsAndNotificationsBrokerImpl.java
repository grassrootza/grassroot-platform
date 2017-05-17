package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.*;

import java.util.Objects;
import java.util.Set;

@Service
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {
	private final Logger logger = LoggerFactory.getLogger(LogsAndNotificationsBrokerImpl.class);

	private final NotificationRepository notificationRepository;
	private final GroupLogRepository groupLogRepository;
	private final UserLogRepository userLogRepository;
	private final EventLogRepository eventLogRepository;
	private final TodoLogRepository todoLogRepository;
	private final AccountLogRepository accountLogRepository;
	private final LiveWireLogRepository liveWireLogRepository;

	@Autowired
	public LogsAndNotificationsBrokerImpl(NotificationRepository notificationRepository, GroupLogRepository groupLogRepository,
										  UserLogRepository userLogRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, AccountLogRepository accountLogRepository, LiveWireLogRepository liveWireLogRepository) {
		this.notificationRepository = notificationRepository;
		this.groupLogRepository = groupLogRepository;
		this.userLogRepository = userLogRepository;
		this.eventLogRepository = eventLogRepository;
		this.todoLogRepository = todoLogRepository;
		this.accountLogRepository = accountLogRepository;
		this.liveWireLogRepository = liveWireLogRepository;
	}

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
		if (!logs.isEmpty()) {
			logger.info("Storing {} logs", logs.size());
		}
		for (ActionLog log : logs) {
			logger.info("Saving log {}", log);
			saveLog(log);
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			logger.info("Storing {} notifications", notifications.size());
		}

		for (Notification notification : notifications) {
			notificationRepository.save(notification);
		}
	}

    @Override
    public long countNotifications(Specifications<Notification> specifications) {
        return notificationRepository.count(specifications);
    }

    private void saveLog(ActionLog actionLog) {
		if (actionLog instanceof GroupLog) {
			groupLogRepository.save((GroupLog) actionLog);
		} else if (actionLog instanceof UserLog) {
			userLogRepository.save((UserLog) actionLog);
		} else if (actionLog instanceof EventLog) {
			eventLogRepository.save((EventLog) actionLog);
		} else if (actionLog instanceof TodoLog) {
			todoLogRepository.save((TodoLog) actionLog);
		} else if (actionLog instanceof AccountLog) {
			accountLogRepository.save((AccountLog) actionLog);
		} else if (actionLog instanceof LiveWireLog) {
			liveWireLogRepository.save((LiveWireLog) actionLog);
		} else {
			throw new UnsupportedOperationException("Unsupported log: " + actionLog);
		}
	}
}
