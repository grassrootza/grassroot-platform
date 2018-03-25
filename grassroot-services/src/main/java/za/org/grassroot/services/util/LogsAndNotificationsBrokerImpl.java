package za.org.grassroot.services.util;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.services.util.PublicActivityType.*;

@Service @Slf4j
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {

    private static final int MAX_PUBLIC_LOGS = 20;

	private final NotificationRepository notificationRepository;
	private final GroupLogRepository groupLogRepository;
	private final UserLogRepository userLogRepository;
	private final EventLogRepository eventLogRepository;
	private final TodoLogRepository todoLogRepository;
	private final AccountLogRepository accountLogRepository;
	private final LiveWireLogRepository liveWireLogRepository;
	private final CampaignLogRepository campaignLogRepository;
	private final CacheUtilService cacheService;

	private final ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	public LogsAndNotificationsBrokerImpl(NotificationRepository notificationRepository, GroupLogRepository groupLogRepository,
										  UserLogRepository userLogRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, AccountLogRepository accountLogRepository, LiveWireLogRepository liveWireLogRepository, CampaignLogRepository campaignLogRepository, CacheManager cacheManager, CacheUtilService cacheService, ApplicationEventPublisher applicationEventPublisher) {
		this.notificationRepository = notificationRepository;
		this.groupLogRepository = groupLogRepository;
		this.userLogRepository = userLogRepository;
		this.eventLogRepository = eventLogRepository;
		this.todoLogRepository = todoLogRepository;
		this.accountLogRepository = accountLogRepository;
		this.liveWireLogRepository = liveWireLogRepository;
		this.campaignLogRepository = campaignLogRepository;
		this.cacheService = cacheService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

    @Override
    public List<PublicActivityLog> fetchMostRecentPublicLogs(Integer numberLogs) {
        int largestPage = numberLogs == null ? MAX_PUBLIC_LOGS : Math.min(numberLogs, MAX_PUBLIC_LOGS);
        // check, in order: campaign logs, group logs, event logs, account logs (for broadcasts), livewire logs

		return Arrays.stream(PublicActivityType.values())
				.map(this::activityLogList).flatMap(List::stream)
				.sorted(Comparator.comparing(PublicActivityLog::getActionTimeMillis).reversed())
				.limit(largestPage)
				.collect(Collectors.toList());
    }

    public List<PublicActivityLog> activityLogList(PublicActivityType activityType) {
		List<PublicActivityLog> activityLogs = cacheService.getCachedPublicActivity(activityType);
		if (activityLogs != null) {
			log.info("returning logs from cache, not hitting DB");
			return activityLogs;
		}
		log.info("calling DB for activity type: {}", activityType);
		final List<PublicActivityType> oldTypes = Arrays.asList(CALLED_MEETING, CALLED_VOTE, CREATED_GROUP, JOINED_GROUP);
		Pageable pageable = new PageRequest(0, MAX_PUBLIC_LOGS, Sort.Direction.DESC,
				oldTypes.contains(activityType) ? "createdDateTime" : "creationTime");
		switch (activityType) {
			case SIGNED_PETITION:
				activityLogs = campaignLogRepository
						.findByCampaignLogType(CampaignLogType.CAMPAIGN_PETITION_SIGNED, pageable)
						.stream().map(cl -> new PublicActivityLog(activityType, cl.getUser().getName(), cl.getCreationTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case JOINED_GROUP:
				List<GroupLogType> types = Arrays.asList(GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
						GroupLogType.GROUP_MEMBER_ADDED_VIA_CAMPAIGN);
				activityLogs = groupLogRepository.findByGroupLogTypeIn(types, pageable)
						.stream().map(gl -> new PublicActivityLog(activityType, gl.getUserNameSafe(), gl.getCreatedDateTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CALLED_MEETING:
				activityLogs = eventLogRepository.findByEventLogTypeAndEventType(EventLogType.CREATED, EventType.MEETING, pageable)
						.stream().map(el -> new PublicActivityLog(activityType, el.getUser().getName(), el.getCreatedDateTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CALLED_VOTE:
				activityLogs = eventLogRepository.findByEventLogTypeAndEventType(EventLogType.CREATED, EventType.VOTE, pageable)
						.stream().map(el -> new PublicActivityLog(activityType, el.getUser().getName(), el.getCreatedDateTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CREATED_TODO:
				activityLogs = todoLogRepository.findByType(TodoLogType.CREATED, pageable)
						.stream().map(tl -> new PublicActivityLog(activityType, tl.getUser().getName(), tl.getCreationTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CREATED_GROUP:
				activityLogs = groupLogRepository.findByGroupLogTypeIn(Collections.singleton(GroupLogType.GROUP_ADDED), pageable)
						.stream().map(gl -> new PublicActivityLog(activityType, gl.getUserNameSafe(), gl.getCreatedDateTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CREATED_ALERT:
				activityLogs = liveWireLogRepository.findByType(LiveWireLogType.ALERT_COMPLETED, pageable)
						.stream().map(lwl -> new PublicActivityLog(activityType, lwl.getUser().getName(), lwl.getCreationTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case SENT_BROADCAST:
				activityLogs = accountLogRepository.findByAccountLogType(AccountLogType.BROADCAST_MESSAGE_SENT, pageable)
						.stream().map(al -> new PublicActivityLog(activityType, al.getUser().getName(), al.getCreationTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			case CREATED_CAMPAIGN:
				activityLogs = campaignLogRepository.findByCampaignLogType(CampaignLogType.CREATED_IN_DB, pageable)
						.stream().map(cl -> new PublicActivityLog(activityType, cl.getUser().getName(), cl.getCreationTime().toEpochMilli()))
						.collect(Collectors.toList());
				break;
			default:
				activityLogs = new ArrayList<>();
		}
		cacheService.putCachedPublicActivity(activityType, activityLogs);
		return activityLogs;
	}

	@Async
	@Override
	public void updateCache(Collection<ActionLog> actionLogs) {
		log.info("updating logs, should be after TX, but is not ... need to debug");
		actionLogs.forEach(this::updateCacheSingle);
	}

	private void updateCacheSingle(ActionLog actionLog) {
		PublicActivityType activityType = null;
		if(actionLog instanceof CampaignLog){
			CampaignLogType campaignLogType = ((CampaignLog) actionLog).getCampaignLogType();
			switch (campaignLogType) {
				case CREATED_IN_DB: activityType = CREATED_CAMPAIGN; break;
				case CAMPAIGN_PETITION_SIGNED: activityType = SIGNED_PETITION;
			}
		} else if (actionLog instanceof GroupLog) {
			GroupLogType groupLogType = ((GroupLog) actionLog).getGroupLogType();
			switch (groupLogType) {
				case GROUP_MEMBER_ADDED_VIA_JOIN_CODE:
				case GROUP_MEMBER_ADDED_VIA_CAMPAIGN: activityType = JOINED_GROUP; break;
				case GROUP_ADDED: activityType = CREATED_GROUP; break;
			}
		} else if(actionLog instanceof EventLog) {
			EventLog eventLog = ((EventLog) actionLog);
			EventLogType eventLogType = eventLog.getEventLogType();
			if(eventLogType.equals(EventLogType.CREATED)) {
				if(eventLog.getEvent().getType().equals(EventType.MEETING))
					activityType = PublicActivityType.CALLED_MEETING;
				else if(eventLog.getEvent().getType().equals(EventType.VOTE))
					activityType = PublicActivityType.CALLED_VOTE;
			}
		} else if(actionLog instanceof TodoLog) {
			TodoLogType todoLogType = ((TodoLog) actionLog).getType();
			if(todoLogType.equals(TodoLogType.CREATED)) {
				activityType = CREATED_TODO;
			}
		} else if(actionLog instanceof LiveWireLog) {
			LiveWireLogType liveWireLogType = ((LiveWireLog) actionLog).getType();
			if(liveWireLogType.equals(LiveWireLogType.ALERT_COMPLETED)) {
				activityType = CREATED_ALERT;
			}
		} else if(actionLog instanceof AccountLog) {
			AccountLogType accountLogType = ((AccountLog) actionLog).getAccountLogType();
			if(accountLogType.equals(AccountLogType.BROADCAST_MESSAGE_SENT)) {
				activityType = SENT_BROADCAST;
			}
		}
		List<PublicActivityLog> activityLogs = cacheService.getCachedPublicActivity(activityType);
		if (activityLogs == null) {
			activityLogs = new ArrayList<>();
		}

		if (activityType != null) {
			activityLogs.add(new PublicActivityLog(activityType, actionLog.getUser().getName(), actionLog.getCreationTime().toEpochMilli()));
			cacheService.putCachedPublicActivity(activityType, activityLogs);
		}
	}


	@Async
	@Override
	@Transactional
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
		for (ActionLog actionLog : logs) {
			log.debug("Saving log {}", actionLog);
			saveLog(actionLog);
			checkForGroupLogUpdate(actionLog, groupsToUpdateLogTimestamp);
			checkForTaskUpdate(actionLog, groupsToUpdateTaskTimestamp);
			log.info("saving log, check thread ...");
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			log.info("Storing {} notifications", notifications.size());
		}

		notificationRepository.save(notifications);

		Instant now = Instant.now();
		groupsToUpdateLogTimestamp.forEach(g -> g.setLastGroupChangeTime(now));
		groupsToUpdateTaskTimestamp.forEach(g -> g.setLastTaskCreationTime(now));

		// getting lots of null pointers in here, method not ready, need to come back to this
//		AfterTxCommitTask cacheUpdate = () -> this.updateCache(logs);
//		applicationEventPublisher.publishEvent(cacheUpdate);
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
