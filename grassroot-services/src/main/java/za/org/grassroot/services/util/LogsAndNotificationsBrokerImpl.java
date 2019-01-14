package za.org.grassroot.services.util;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.domain.notification.NotificationStatus;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TaskLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.NotificationSpecifications;
import za.org.grassroot.core.specifications.TodoLogSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.services.util.PublicActivityType.*;

@Service @Slf4j
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {

    private static final int MAX_PUBLIC_LOGS = 20;

    private static final List<GroupLogType> joinedGroupTypes = Arrays.asList(GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
            GroupLogType.GROUP_MEMBER_ADDED_VIA_CAMPAIGN);

	private final NotificationRepository notificationRepository;
	private final BroadcastNotificationRepository broadcastNotificationRepository;
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
	public LogsAndNotificationsBrokerImpl(NotificationRepository notificationRepository, BroadcastNotificationRepository broadcastNotificationRepository, GroupLogRepository groupLogRepository,
										  UserLogRepository userLogRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, AccountLogRepository accountLogRepository, LiveWireLogRepository liveWireLogRepository, CampaignLogRepository campaignLogRepository, CacheUtilService cacheService, ApplicationEventPublisher applicationEventPublisher) {
		this.notificationRepository = notificationRepository;
		this.broadcastNotificationRepository = broadcastNotificationRepository;
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
			log.debug("Storing {} logs", logs.size());
		}

		Set<Group> groupsToUpdateLogTimestamp = new HashSet<>();
		Set<Group> groupsToUpdateTaskTimestamp = new HashSet<>();
		for (ActionLog actionLog : logs) {
			log.debug("Saving log {}", actionLog);
			saveLog(actionLog);
			checkForGroupLogUpdate(actionLog, groupsToUpdateLogTimestamp);
			checkForTaskUpdate(actionLog, groupsToUpdateTaskTimestamp);
			log.debug("saving log, check thread ...");
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			log.info("Storing {} notifications", notifications.size());
		}

		notificationRepository.saveAll(notifications);

		Instant now = Instant.now();
		groupsToUpdateLogTimestamp.forEach(g -> g.setLastGroupChangeTime(now));
		groupsToUpdateTaskTimestamp.forEach(g -> g.setLastTaskCreationTime(now));

		// getting lots of null pointers in here, method not ready, need to come back to this
		AfterTxCommitTask cacheUpdate = () -> {
			log.debug("this should execute after the method is finished, and TX committed");
			this.updateCache(logs);
		};
		applicationEventPublisher.publishEvent(cacheUpdate);

		log.debug("executing main part of method");
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
            log.debug("returning logs from cache, not hitting DB");
            return activityLogs;
        }
        log.debug("calling DB for activity type: {}", activityType);
        final List<PublicActivityType> oldTypes = Arrays.asList(CALLED_MEETING, CALLED_VOTE, CREATED_GROUP, JOINED_GROUP);
        Pageable pageable = PageRequest.of(0, MAX_PUBLIC_LOGS, Sort.Direction.DESC,
                oldTypes.contains(activityType) ? "createdDateTime" : "creationTime");
        switch (activityType) {
            case SIGNED_PETITION:
                activityLogs = campaignLogRepository
                        .findByCampaignLogType(CampaignLogType.CAMPAIGN_PETITION_SIGNED, pageable)
                        .stream().map(cl -> new PublicActivityLog(activityType, cl.getUser().getName(), cl.getCreationTime().toEpochMilli()))
                        .collect(Collectors.toList());
                break;
            case JOINED_GROUP:
                activityLogs = groupLogRepository.findByGroupLogTypeIn(joinedGroupTypes, pageable)
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
        log.debug("updating logs, should be after TX ...");
        actionLogs.forEach(this::updateCacheSingle);
    }

	@Override
	@Transactional
	@SuppressWarnings("unchecked")
	public void abortNotificationSend(Specification specifications) {
		notificationRepository.findAll((Specification<Notification>) specifications)
					.forEach(n -> n.setStatus(NotificationStatus.ABORTED));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Notification> lastNotificationsSentToUser(User user, Integer numberToRetrieve, Instant sinceTime) {
		Instant sinceSentTime = sinceTime != null ? sinceTime : Instant.now().minus(30, ChronoUnit.DAYS); // using a sensible default
		Specification<Notification> specs = Specification.where(NotificationSpecifications.sentOrBetterSince(sinceSentTime))
				.and(NotificationSpecifications.toUser(user));
		Pageable page = PageRequest.of(0, numberToRetrieve == null ? 1 : numberToRetrieve, Sort.Direction.DESC, "lastStatusChange");
		return notificationRepository.findAll(specs, page);
	}

	@Override
	public void removeCampaignLog(User user, Campaign campaign, CampaignLogType logType) {
		campaignLogRepository.deleteAllByCampaignAndUserAndCampaignLogType(campaign, user, logType);
	}

	private void updateCacheSingle(ActionLog actionLog) {
        PublicActivityType activityType = null;

        log.debug("checking for public action type, log = {}", actionLog);
        switch (actionLog.getActionLogType()) {
            case GROUP_LOG:
                GroupLogType logType = ((GroupLog) actionLog).getGroupLogType();
                activityType = joinedGroupTypes.contains(logType) ? JOINED_GROUP :
                        GroupLogType.GROUP_ADDED.equals(logType) ? CREATED_GROUP : null;
                break;
            case EVENT_LOG:
                EventLog eventLog = ((EventLog) actionLog);
                EventLogType eventLogType = eventLog.getEventLogType();
                activityType = !EventLogType.CREATED.equals(eventLogType) ? null :
                        eventLog.getEvent().getEventType().equals(EventType.MEETING) ? CALLED_MEETING : CALLED_VOTE;
                break;
            case TODO_LOG:
                activityType = TodoLogType.CREATED.equals(((TodoLog) actionLog).getType()) ? CREATED_TODO : null;
                break;
            case LIVEWIRE_LOG:
                activityType = LiveWireLogType.ALERT_COMPLETED.equals(((LiveWireLog) actionLog).getType()) ? CREATED_ALERT : null;
                break;
            case CAMPAIGN_LOG:
                CampaignLogType campaignLogType = ((CampaignLog) actionLog).getCampaignLogType();
                activityType = CampaignLogType.CREATED_IN_DB.equals(campaignLogType) ? CREATED_CAMPAIGN :
                        CampaignLogType.CAMPAIGN_PETITION_SIGNED.equals(campaignLogType) ? SIGNED_PETITION : null;
                break;
            case ACCOUNT_LOG:
                activityType = AccountLogType.BROADCAST_MESSAGE_SENT.equals(((AccountLog) actionLog).getAccountLogType()) ? SENT_BROADCAST : null;
            default:
                break;
        }

        log.debug("okay tested for type, comes out as : {}", activityType);

        if (activityType != null) {
            List<PublicActivityLog> activityLogs = cacheService.getCachedPublicActivity(activityType);
            if (activityLogs == null) {
                activityLogs = new ArrayList<>();
            }

            activityLogs.add(new PublicActivityLog(activityType, actionLog.getUser().getName(), actionLog.getCreationTime().toEpochMilli()));
            log.debug("activity type positive, adding to public cache: {}", activityLogs);
            cacheService.putCachedPublicActivity(activityType, activityLogs);
        }
    }

    @Override
    public long countNotifications(Specification<Notification> specifications) {
        return notificationRepository.count(specifications);
    }

    @Override
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public <T extends Notification> long countNotifications(Specification<T> specs, Class<T> notificationType) {
		if (notificationType.equals(BroadcastNotification.class)) {
			return broadcastNotificationRepository.count((Specification<BroadcastNotification>) specs);
		} else {
			return countNotifications((Specification<Notification>) specs);
		}
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
		Specification<EventLog> eventLogSpecs = Specification.where(EventLogSpecifications.forUser(user))
				.and(EventLogSpecifications.forGroup(group))
				.and(ofActionType);
		List<EventLog> eventLogs = eventLogRepository.findAll(eventLogSpecs);
		actionLogs.addAll(eventLogs);

		Set<TodoLogType> todoActionTypes = ImmutableSet.of(TodoLogType.CREATED, TodoLogType.CHANGED,
				TodoLogType.CANCELLED, TodoLogType.EXTENDED, TodoLogType.IMAGE_RECORDED, TodoLogType.RESPONDED);
		Specification<TodoLog> ofTodoAction = (root, query, cb) -> root.get("type").in(todoActionTypes);
		Specification<TodoLog> todoLogSpecs = Specification.where(TodoLogSpecifications.forUser(user))
				.and(TodoLogSpecifications.forGroup(group))
				.and(ofTodoAction);
		List<TodoLog> todoLogs = todoLogRepository.findAll(todoLogSpecs);
		actionLogs.addAll(todoLogs);

		Specification<LiveWireLog> forGroup = (root, query, cb) -> cb.equal(root.join("alert").get("group"), group);
		Specification<LiveWireLog> forUser = (root, query, cb) -> cb.equal(root.get("userTakingAction"), user);
		Specification<LiveWireLog> createdAlert = (root, query, cb) -> cb.equal(root.get("type"), LiveWireLogType.ALERT_CREATED);
		List<LiveWireLog> liveWireLogs = liveWireLogRepository.findAll(Specification.where(forGroup).and(forUser).and(createdAlert));
		actionLogs.addAll(liveWireLogs);

		Specification<CampaignLog> campaignGroup = (root, query, cb) -> cb.equal(root.join("campaign").get("masterGroup"), group);
		Specification<CampaignLog> forUserCampaign = (root, query, cb) -> cb.equal(root.get("user"), user);
		List<CampaignLog> campaignLogs = campaignLogRepository.findAll(Specification.where(campaignGroup).and(forUserCampaign));
		actionLogs.addAll(campaignLogs);

		log.info("log sweep done, {} event logs, {} todo logs, {} live wire logs, {} campaign logs",
				eventLogs.size(), todoLogs.size(), liveWireLogs.size(), campaignLogs);

		return actionLogs.stream().sorted(Comparator.comparing(ActionLog::getCreationTime, Comparator.reverseOrder()))
				.collect(Collectors.toList());
	}

    @Override
	@SuppressWarnings("unchecked")
    public <T extends ActionLog> long countLogs(Specification<T> specs, ActionLogType logType) {
        switch (logType) {
			case LIVEWIRE_LOG: return liveWireLogRepository.count((Specification<LiveWireLog>) specs);
			case USER_LOG:
			case GROUP_LOG:
			case EVENT_LOG:
			case TODO_LOG:
			case ACCOUNT_LOG:
			case CAMPAIGN_LOG:
			case ADDRESS_LOG:
			case TASK_LOG:
			default:
				// fill in as and when needed
				log.info("Bad invocation of generic log counting method");
				return 0;
		}
    }

    @Override
	@Transactional(readOnly = true)
	public long countCampaignLogs(Specification<CampaignLog> specs) {
		return campaignLogRepository.count(specs);
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
