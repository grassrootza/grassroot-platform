package za.org.grassroot.services.util;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.TodoLogSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {

    private static final int MAX_PUBLIC_LOGS = 100;

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
                                          UserLogRepository userLogRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, AccountLogRepository accountLogRepository, LiveWireLogRepository liveWireLogRepository, CampaignLogRepository campaignLogRepository, CacheManager cacheManager) {
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
    public List<PublicActivityLog> fetchMostRecentPublicLogs(Integer numberLogs) {
        // todo : definitely need to add caching, pseudonyms
        List<PublicActivityLog> logs = new ArrayList<>();
        int largestPage = numberLogs == null ? MAX_PUBLIC_LOGS : Math.min(numberLogs, MAX_PUBLIC_LOGS);
        Pageable pageable = new PageRequest(0, largestPage);
        // check, in order: campaign logs, group logs, event logs, account logs (for broadcasts), livewire logs
        Map<GroupLogType, PublicActivityType> types = new HashMap<>();
        types.put(GroupLogType.GROUP_ADDED, PublicActivityType.CREATED_GROUP);
        types.put(GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE, PublicActivityType.JOINED_GROUP);
        types.put(GroupLogType.GROUP_MEMBER_ADDED_VIA_CAMPAIGN, PublicActivityType.JOINED_GROUP);
        List<PublicActivityLog> groupLogs = groupLogRepository.findByGroupLogTypeIn(types.keySet(), pageable)
                .stream().map(gl -> new PublicActivityLog(types.get(gl.getGroupLogType()),
                        gl.getUserNameSafe(),
                        gl.getCreatedDateTime().toEpochMilli())).collect(Collectors.toList());
        logs.addAll(groupLogs);

        List<PublicActivityLog> campaignLogs = campaignLogRepository.findByCampaignLogType(CampaignLogType.CAMPAIGN_PETITION_SIGNED, pageable)
                .stream().map(cl -> new PublicActivityLog(PublicActivityType.SIGNED_PETITION, cl.getUser().getName(), cl.getCreationTime().toEpochMilli()))
                .collect(Collectors.toList());
        logs.addAll(campaignLogs);

        List<PublicActivityLog> broadcastLogs = accountLogRepository.findByAccountLogType(AccountLogType.BROADCAST_MESSAGE_SENT, pageable)
                .stream().map(al -> new PublicActivityLog(PublicActivityType.SENT_BROADCAST, al.getUser().getName(), al.getCreationTime().toEpochMilli()))
                .collect(Collectors.toList());
        logs.addAll(broadcastLogs);

        // todo : add in the event logs

        logs.sort(Comparator.comparing(PublicActivityLog::getActionTimeMillis).reversed());
        return logs;
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
			log.info("Storing {} logs", logs.size());
		}

		Set<Group> groupsToUpdateLogTimestamp = new HashSet<>();
		Set<Group> groupsToUpdateTaskTimestamp = new HashSet<>();
		for (ActionLog log : logs) {
			this.log.debug("Saving log {}", log);
			saveLog(log);
			checkForGroupLogUpdate(log, groupsToUpdateLogTimestamp);
			checkForTaskUpdate(log, groupsToUpdateTaskTimestamp);
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			log.info("Storing {} notifications", notifications.size());
		}

		notificationRepository.save(notifications);

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

		log.info("log sweep done, {} event logs, {} todo logs, {} live wire logs, {} campaign logs",
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
