package za.org.grassroot.services.util;

import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
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
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.LiveWireLogType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.TodoLogSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
	@Transactional(readOnly = true)
    public List<ActionLog> fetchMembershipLogs(Membership membership) {
		Group group = membership.getGroup();
		User user = membership.getUser();

		List<GroupLog> groupLogs = groupLogRepository.findAll(GroupLogSpecifications
				.memberChangeRecords(group, DateTimeUtil.getEarliestInstant())
				.and(GroupLogSpecifications.containingUser(user)));

		Set<ActionLog> actionLogs = new HashSet<>(groupLogs);

		Set<EventLogType> actionTypes = ImmutableSet.of(EventLogType.RSVP, EventLogType.VOTE_OPTION_RESPONSE,
				EventLogType.IMAGE_RECORDED, EventLogType.IMAGE_REMOVED,
				EventLogType.CANCELLED, EventLogType.CHANGE, EventLogType.CREATED,
				EventLogType.MADE_PRIVATE, EventLogType.MADE_PUBLIC);

		Specification<EventLog> ofActionType = (root, query, cb) -> root.get("eventLogType").in(actionTypes);
		Specifications<EventLog> eventLogSpecs = Specifications.where(EventLogSpecifications.forUser(user))
				.and(EventLogSpecifications.forGroup(group))
				.and(ofActionType);
		List<EventLog> eventLogs = eventLogRepository.findAll(eventLogSpecs);
		actionLogs.addAll(eventLogs);

		Set<TodoLogType> todoActionTypes = ImmutableSet.of(TodoLogType.CREATED, TodoLogType.CHANGED,
				TodoLogType.CANCELLED, TodoLogType.EXTENDED, TodoLogType.IMAGE_RECORDED, TodoLogType.RESPONDED);
		Specification<TodoLog> ofTodoAction = (root, query, cb) -> root.get("type").in(todoActionTypes);
		Specifications<TodoLog> todoLogSpecs = Specifications.where(TodoLogSpecifications.forUser(user))
				.and(TodoLogSpecifications.forGroup(group))
				.and(ofTodoAction);
		List<TodoLog> todoLogs = todoLogRepository.findAll(todoLogSpecs);
		actionLogs.addAll(todoLogs);

		Specification<LiveWireLog> forGroup = (root, query, cb) -> cb.equal(root.join("alert").get("group"), group);
		Specification<LiveWireLog> forUser = (root, query, cb) -> cb.equal(root.get("userTakingAction"), user);
		Specification<LiveWireLog> createdAlert = (root, query, cb) -> cb.equal(root.get("type"), LiveWireLogType.ALERT_CREATED);
		List<LiveWireLog> liveWireLogs = liveWireLogRepository.findAll(Specifications.where(forGroup).and(forUser).and(createdAlert));
		actionLogs.addAll(liveWireLogs);

		Specification<CampaignLog> campaignGroup = (root, query, cb) -> cb.equal(root.join("campaign").get("masterGroup"), group);
		Specification<CampaignLog> forUserCampaign = (root, query, cb) -> cb.equal(root.get("user"), user);
		List<CampaignLog> campaignLogs = campaignLogRepository.findAll(Specifications.where(campaignGroup).and(forUserCampaign));
		actionLogs.addAll(campaignLogs);

		logger.info("log sweep done, {} event logs, {} todo logs, {} live wire logs, {} campaign logs",
				eventLogs.size(), todoLogs.size(), liveWireLogs.size(), campaignLogs);

		return actionLogs.stream().sorted(Comparator.comparing(ActionLog::getCreationTime, Comparator.reverseOrder()))
				.collect(Collectors.toList());
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
