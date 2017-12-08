package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TaskLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.repository.*;

import java.time.Instant;
import java.util.HashSet;
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
	private final CampaignLogRepository campaignLogRepository;

	@Autowired
	public LogsAndNotificationsBrokerImpl(NotificationRepository notificationRepository, GroupLogRepository groupLogRepository,
										  UserLogRepository userLogRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, AccountLogRepository accountLogRepository, LiveWireLogRepository liveWireLogRepository, CampaignLogRepository campaignLogRepository) {
		this.notificationRepository = notificationRepository;
		this.groupLogRepository = groupLogRepository;
		this.userLogRepository = userLogRepository;
		this.eventLogRepository = eventLogRepository;
		this.todoLogRepository = todoLogRepository;
		this.accountLogRepository = accountLogRepository;
		this.liveWireLogRepository = liveWireLogRepository;
		this.campaignLogRepository = campaignLogRepository;
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

		Set<Group> groupsToUpdateLogTimestamp = new HashSet<>();
		Set<Group> groupsToUpdateTaskTimestamp = new HashSet<>();
		for (ActionLog log : logs) {
			logger.debug("Saving log {}", log);
			saveLog(log);
			checkForGroupLogUpdate(log, groupsToUpdateLogTimestamp);
			checkForTaskUpdate(log, groupsToUpdateTaskTimestamp);
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			logger.info("Storing {} notifications", notifications.size());
		}

		for (Notification notification : notifications) {
			notificationRepository.save(notification);
		}

		Instant now = Instant.now();
		groupsToUpdateLogTimestamp.forEach(g -> g.setLastGroupChangeTime(now));
		groupsToUpdateTaskTimestamp.forEach(g -> g.setLastTaskCreationTime(now));
	}

    @Override
    public long countNotifications(Specifications<Notification> specifications) {
        return notificationRepository.count(specifications);
    }

    private void saveLog(ActionLog actionLog) {
		if (actionLog instanceof GroupLog) {
			groupLogRepository.save((GroupLog) actionLog);
			((GroupLog) actionLog).getGroup().setLastGroupChangeTime(Instant.now());
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
		}else if (actionLog instanceof CampaignLog) {
			campaignLogRepository.save((CampaignLog) actionLog);
		}  else {
			throw new UnsupportedOperationException("Unsupported log: " + actionLog);
		}
	}

	private void checkForGroupLogUpdate(ActionLog log, Set<Group> groups) {
		if (log instanceof GroupLog) {
			groups.add(((GroupLog) log).getGroup());
		}
	}

	private void checkForTaskUpdate(ActionLog log, Set<Group> groups) {
		if (log instanceof TaskLog && ((TaskLog) log).isCreationLog()) {
			groups.add(((TaskLog) log).getTask().getAncestorGroup());
		}
	}
}
