package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.notification.EventCancelledNotification;
import za.org.grassroot.core.domain.notification.EventChangedNotification;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.notification.EventReminderNotification;
import za.org.grassroot.core.domain.notification.MeetingRsvpTotalsNotification;
import za.org.grassroot.core.domain.notification.MeetingThankYouNotification;
import za.org.grassroot.core.domain.notification.UserLanguageNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingContainer;
import za.org.grassroot.core.domain.task.Meeting_;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.domain.task.VoteContainer;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.exception.TaskNameTooLongException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.task.enums.EventListTimeType;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static za.org.grassroot.core.domain.task.EventReminderType.CUSTOM;
import static za.org.grassroot.core.domain.task.EventReminderType.DISABLED;
import static za.org.grassroot.core.enums.EventLogType.*;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service @Slf4j
public class EventBrokerImpl implements EventBroker {

	@Value("${grassroot.events.limit.enabled:false}")
	private boolean eventMonthlyLimitActive;

	private final UserManagementService userService;
	private final EventLogBroker eventLogBroker;
	private final EventRepository eventRepository;
	private final VoteRepository voteRepository;
	private final MeetingRepository meetingRepository;
	private final UidIdentifiableRepository uidIdentifiableRepository;
	private final GroupRepository groupRepository;
	private final PermissionBroker permissionBroker;
	private final LogsAndNotificationsBroker logsAndNotificationsBroker;
	private final CacheUtilService cacheUtilService;
	private final MessageAssemblingService messageAssemblingService;
	private final AccountFeaturesBroker accountFeaturesBroker;
	private final GeoLocationBroker geoLocationBroker;
	private final TaskImageBroker taskImageBroker;

	private final EntityManager entityManager;

	private PasswordTokenService tokenService;
	private ApplicationEventPublisher applicationEventPublisher;
	private GraphBroker graphBroker;

	@Autowired
	public EventBrokerImpl(MeetingRepository meetingRepository, EventLogBroker eventLogBroker, EventRepository eventRepository, VoteRepository voteRepository, UidIdentifiableRepository uidIdentifiableRepository, UserManagementService userService, AccountFeaturesBroker accountFeaturesBroker, GroupRepository groupRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, CacheUtilService cacheUtilService, MessageAssemblingService messageAssemblingService, GeoLocationBroker geoLocationBroker, TaskImageBroker taskImageBroker, EntityManager entityManager) {
		this.meetingRepository = meetingRepository;
		this.eventLogBroker = eventLogBroker;
		this.eventRepository = eventRepository;
		this.voteRepository = voteRepository;
		this.uidIdentifiableRepository = uidIdentifiableRepository;
		this.userService = userService;
		this.accountFeaturesBroker = accountFeaturesBroker;
		this.groupRepository = groupRepository;
		this.permissionBroker = permissionBroker;
		this.logsAndNotificationsBroker = logsAndNotificationsBroker;
		this.cacheUtilService = cacheUtilService;
		this.messageAssemblingService = messageAssemblingService;
		this.geoLocationBroker = geoLocationBroker;
		this.taskImageBroker = taskImageBroker;
		this.entityManager = entityManager;
	}

	@Autowired
	public void setTokenService(PasswordTokenService tokenService) {
		this.tokenService = tokenService;
	}

	// almost certainly there's a better way to do both of these
	@Autowired(required = false)
	public void setGraphBroker(GraphBroker graphBroker) {
		this.graphBroker = graphBroker;
	}

	@Autowired(required = false)
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public Event load(String eventUid) {
		Objects.requireNonNull(eventUid);
		return eventRepository.findOneByUid(eventUid);
	}

	@Override
	public Meeting loadMeeting(String meetingUid) {
		log.info("looking for meeting with UID: {}", meetingUid);
		return meetingRepository.findOneByUid(meetingUid);
	}

	@Override
	@Transactional
	public Meeting createMeeting(MeetingBuilderHelper helper, UserInterfaceType channel) {
		helper.validateMeetingFields();

		User user = userService.load(helper.getUserUid());
		MeetingContainer parent = uidIdentifiableRepository.findOneByUid(MeetingContainer.class,
				helper.getParentType(), helper.getParentUid());

		Event possibleDuplicate = checkForDuplicate(helper.getUserUid(), helper.getParentUid(),
                helper.getName(), helper.getStartInstant(), helper.getAssignedMemberUids());
		if (possibleDuplicate != null) { // todo : hand over to update meeting if anything different in parameters
			log.info("detected a duplicate meeting, subject {}, returning it", helper.getName());
			return (Meeting) possibleDuplicate;
		}

		checkForEventLimit(helper.getParentUid());
		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		Meeting meeting = helper.convertToBuilder(user, parent).createMeeting();
		meeting = setReminder(meeting, helper);

		log.info("Created meeting, with importance={}, reminder type={} and time={}", meeting.getSpecialForm(), meeting.getReminderType(), meeting.getScheduledReminderTime());

		meetingRepository.save(meeting);

		meeting = checkForAndSetImage(meeting, helper);

		LogsAndNotificationsBundle bundle = createMeetingBundle(meeting, user);
		logsAndNotificationsBroker.storeBundle(bundle);

		generateResponseTokens(meeting);

		log.info("called store bundle, exiting create mtg method ... triggering graph if enabled");
		if (graphBroker != null && applicationEventPublisher != null) {
			applicationEventPublisher.publishEvent(addToGraph(meeting));
		}

		log.info("also recording a meeting location, if appropriate options set, etc. Do we have one? : {}", helper.hasPreciseLocation());
		if (helper.hasPreciseLocation() && applicationEventPublisher != null) {
			log.info("We have locations, recording it: {}", helper.getUserLocation());
			applicationEventPublisher.publishEvent(addEventLocation(meeting.getUid(), helper.getUserLocation(), channel));
		}

		return meeting;
	}

	private AfterTxCommitTask addToGraph(Meeting meeting) {
		return () -> {
			List<String> assignedUids = meeting.getMembers().stream().map(User::getUid).collect(Collectors.toList());
			addTaskToGraph(meeting.getUid(), TaskType.MEETING, assignedUids);
		};
	}

	private AfterTxCommitTask addEventLocation(String eventUid, GeoLocation location, UserInterfaceType sourceInterface) {
		return () -> {
			geoLocationBroker.calculateMeetingLocationInstant(eventUid, location, sourceInterface);
		};
	}

	private Meeting setReminder(Meeting meeting, MeetingBuilderHelper helper) {
		// else sometimes reminder setting will be in the past, causing duplication of meetings; defaulting to 1 hours
		if (!helper.getReminderType().equals(DISABLED) &&
				meeting.getScheduledReminderTime() != null &&
				meeting.getScheduledReminderTime().isBefore(Instant.now())) {
			meeting.setCustomReminderMinutes(60);
			meeting.setReminderType(CUSTOM);
			meeting.updateScheduledReminderTime();
		}
		return meeting;
	}

	private Meeting checkForAndSetImage(Meeting meeting, MeetingBuilderHelper helper) {
		if (!StringUtils.isEmpty(helper.getTaskImageKey())) {
			taskImageBroker.recordImageForTask(helper.getUserUid(), meeting.getUid(), TaskType.MEETING,
					Collections.singleton(helper.getTaskImageKey()), EventLogType.IMAGE_AT_CREATION, null);
			meeting.setImageUrl(taskImageBroker.getShortUrl(helper.getTaskImageKey()));
		}
		return meeting;
	}

	private LogsAndNotificationsBundle createMeetingBundle(Meeting meeting, User user) {
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		cacheUtilService.clearRsvpCacheForUser(user.getUid());
		EventLog meetingCreatedLog = new EventLog(user, meeting, CREATED);
		EventLog creatingUserRsvpLog = new EventLog(user, meeting, EventLogType.RSVP, EventRSVPResponse.YES);
		bundle.addLog(meetingCreatedLog);
		bundle.addLog(creatingUserRsvpLog);
		Set<Notification> notifications = constructEventInfoNotifications(meeting, meetingCreatedLog, meeting.getMembers());
		bundle.addNotifications(notifications);
		return bundle;
	}

	@SuppressWarnings("unchecked")
	private void generateResponseTokens(Event event) {
		Set<User> users = (Set<User>) event.getMembers();
		Set<String> emailMemberUids = users.stream().filter(User::areNotificationsByEmail)
				.map(User::getUid).collect(Collectors.toSet());
		if (!emailMemberUids.isEmpty()) {
			tokenService.generateResponseTokens(emailMemberUids, event.getAncestorGroup().getUid(), event.getUid());
		}
	}

	@Override
	public List<Meeting> publicMeetingsUserIsNotPartOf(String term, User user){
		return meetingRepository.publicMeetingsUserIsNotPartOfWithsSearchTerm(term, user);
	}

	private void checkForEventLimit(String parentUid) {
		if (eventMonthlyLimitActive && accountFeaturesBroker.numberEventsLeftForGroup(parentUid) < 1) {
			throw new AccountLimitExceededException();
		}
	}

	// introducing so that we can check catch & handle duplicate requests (e.g., from malfunctions on Android client offline->queue->sync function)
    @SuppressWarnings("unchecked") // the getAssignedMembers has a strange behavior on type check warnings, hence suppressing
    private Event checkForDuplicate(String userUid, String parentGroupUid, String name, Instant startDateTime, Set<String> assignedMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentGroupUid);
		Objects.requireNonNull(name);
		Objects.requireNonNull(startDateTime);

		Instant intervalStart = startDateTime.minus(180, ChronoUnit.SECONDS);
		Instant intervalEnd = startDateTime.plus(180, ChronoUnit.SECONDS);

		User user = userService.load(userUid);
		Group parentGroup = groupRepository.findOneByUid(parentGroupUid);

		Event possibleEvent = eventRepository.findOneByCreatedByUserAndParentGroupAndNameAndEventStartDateTimeBetweenAndCanceledFalse(user, parentGroup, name, intervalStart, intervalEnd);
		if (possibleEvent == null)
		    return null;

		boolean priorHasAssigned = !possibleEvent.isAllGroupMembersAssigned();
		boolean newHasAssigned = assignedMemberUids != null && !assignedMemberUids.isEmpty();

		if (!priorHasAssigned && !newHasAssigned) {
		    return possibleEvent;
        } else if (priorHasAssigned && newHasAssigned) {
		    Set<User> users = possibleEvent.getAssignedMembers();
		    Set<String> priorUids = users.stream().map(User::getUid).collect(Collectors.toSet());
		    return priorUids.equals(assignedMemberUids) ? possibleEvent : null;
        } else {
		    return null; // because they must be different, by definition (one is full assigned, other is not)
        }
	}

	private Set<Notification> constructEventInfoNotifications(Event event, EventLog eventLog, Set<User> usersToNotify) {
		Set<Notification> notifications = new HashSet<>();
		log.debug("constructing event notifications ... has image URL? : {}", event.getImageUrl());
		for (User member : usersToNotify) {
			cacheUtilService.clearRsvpCacheForUser(member.getUid());
			String message = messageAssemblingService.createEventInfoMessage(member, event);
			Notification notification = new EventInfoNotification(member, message, eventLog);
			notifications.add(notification);
		}
		// check if creating user is on Android, in which case, add an explicit SMS
		if (event.getCreatedByUser().getMessagingPreference().equals(DeliveryRoute.ANDROID_APP)) {
			log.debug("event creator on Android, sending an SMS so they know format");
			User creator = event.getCreatedByUser();
			String creatorMessage = messageAssemblingService.createEventInfoMessage(creator, event);
			Notification smsNotification = new EventInfoNotification(creator, creatorMessage, eventLog);
			smsNotification.setDeliveryChannel(DeliveryRoute.SMS);
			notifications.add(smsNotification);
		}
		return notifications;
	}

	@SuppressWarnings("unchecked")
	private Set<User> getAllEventMembers(Event event) {
		return (Set<User>) event.getAllMembers();
	}

	@Override
    @Transactional
	public boolean updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation) {
		Objects.requireNonNull(userUid);
        Objects.requireNonNull(meetingUid);

		User user = userService.load(userUid);
        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

        boolean meetingChanged = false;

        if (meeting.isCanceled()) {
            throw new IllegalStateException("Meeting is canceled: " + meeting);
        }

		// note : for the moment, only the user who called the meeting can change it ... may alter in future, depends on user feedback
		if (!meeting.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only meeting caller can change meeting");
		}

		if (!StringUtils.isEmpty(name) && name.length() > Event.MAX_NAME_LENGTH) {
			throw new TaskNameTooLongException();
		}

		boolean startTimeChanged;
        if (eventStartDateTime != null) {
			Instant convertedStartDateTime = convertToSystemTime(eventStartDateTime, getSAST());
			Duration timeBetween = Duration.between(convertedStartDateTime, meeting.getEventStartDateTime());
			log.info("duration between old and new dates and times: {} seconds", timeBetween.getSeconds());
			startTimeChanged = Math.abs(timeBetween.getSeconds()) > 90; // since otherwise pick up spurious non-equals because of small instant changes
			if (startTimeChanged) {
				log.info("start time changed! to : " + convertedStartDateTime);
				validateEventStartTime(convertedStartDateTime);
				meeting.setEventStartDateTime(convertedStartDateTime);
				meeting.updateScheduledReminderTime();
				meetingChanged = true;
			}
		} else {
        	startTimeChanged = false;
		}

        if (!StringUtils.isEmpty(name) && !meeting.getName().equals(name)) {
        	log.info("Meeting title changed!");
        	meeting.setName(name);
        	meetingChanged = true;
		}

        if (!StringUtils.isEmpty(eventLocation) && !meeting.getEventLocation().equals(eventLocation)) {
			log.info("Meeting location changed!");
        	meeting.setEventLocation(eventLocation);
			meetingChanged = true;
		}

		if (meetingChanged) {
			constructChangedMtgNotifications(user, meeting, startTimeChanged);
		}

		return meetingChanged;
	}

	@Override
	@Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, String description, LocalDateTime eventStartDateTime,
	                          String eventLocation, EventReminderType reminderType, int customReminderMinutes, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingUid);
		Objects.requireNonNull(name);
		Objects.requireNonNull(eventStartDateTime);
		Objects.requireNonNull(eventLocation);

		User user = userService.load(userUid);
		Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is canceled: " + meeting);
		}

		if (!meeting.getCreatedByUser().equals(user) &&
				!permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
			throw new AccessDeniedException("Error! Only meeting caller or group organizer can change meeting");
		}

		if (name.length() > Event.MAX_NAME_LENGTH) {
			throw new TaskNameTooLongException();
		}

		Instant convertedStartDateTime = convertToSystemTime(eventStartDateTime, getSAST());
		validateEventStartTime(convertedStartDateTime);
		boolean startTimeChanged = !convertedStartDateTime.equals(meeting.getEventStartDateTime());

		meeting.setName(name);
		meeting.setEventStartDateTime(convertedStartDateTime);
		meeting.setEventLocation(eventLocation);

		if (reminderType != null) {
			meeting.setReminderType(reminderType);
		}

		if (customReminderMinutes != -1 && !meeting.getReminderType().equals(CUSTOM)) {
			meeting.setCustomReminderMinutes(customReminderMinutes);
		}

		meeting.updateScheduledReminderTime();

		if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
			assignedMemberUids.add(userUid); // as above, enforce creating user part of meeting
			if (meeting.isAllGroupMembersAssigned()) {
				meeting.assignMembers(assignedMemberUids);
			} else {
				Set<String> existingMemberUids = meeting.getAssignedMembers()
						.stream()
						.map(User::getUid)
						.collect(Collectors.toSet());
				existingMemberUids.removeAll(assignedMemberUids); // so have just members not in new set
				meeting.removeAssignedMembers(existingMemberUids); // remove those
				meeting.assignMembers(assignedMemberUids); // reset to current
			}
		}

		constructChangedMtgNotifications(user, meeting, startTimeChanged);
	}

	private void constructChangedMtgNotifications(User user, Meeting meeting, boolean startTimeChanged) {
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, meeting, CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventChangedNotifications(meeting, eventLog, startTimeChanged);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	private Set<Notification> constructEventChangedNotifications(Event event, EventLog eventLog, boolean startTimeChanged) {
		Set<User> rsvpWithNoMembers = new HashSet<>(userService.findUsersThatRsvpForEvent(event, EventRSVPResponse.NO));
		return getAllEventMembers(event).stream()
				.filter(member -> startTimeChanged || !rsvpWithNoMembers.contains(member))
				.map(member -> {
					String message = messageAssemblingService.createEventChangedMessage(member, event);
					return new EventChangedNotification(member, message, eventLog);
				})
				.collect(Collectors.toSet());
	}

	@Override
	@Transactional
	public Vote createVote(VoteHelper helper) {
		Objects.requireNonNull(helper.getUserUid());
		Objects.requireNonNull(helper.getParentUid());
		Objects.requireNonNull(helper.getParentType());
		Objects.requireNonNull(helper.getName());

		log.info("Creating vote from helper: {}", helper);

		Instant convertedClosingDateTime = convertToSystemTime(helper.getEventStartDateTime(), getSAST());
        validateEventStartTime(convertedClosingDateTime);

		User user = userService.load(helper.getUserUid());
		VoteContainer parent = uidIdentifiableRepository.findOneByUid(VoteContainer.class, helper.getParentType(), helper.getParentUid());

		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		if (JpaEntityType.GROUP.equals(helper.getParentType())) {
			Event possibleDuplicate = checkForDuplicate(helper.getUserUid(), helper.getParentUid(),
					helper.getName(), convertedClosingDateTime, helper.getAssignMemberUids());
			if (possibleDuplicate != null && possibleDuplicate.getEventType().equals(EventType.VOTE)) {
				log.info("Detected duplicate vote creation, returning the already-created one ... ");
				return (Vote) possibleDuplicate;
			}

			if (eventMonthlyLimitActive && accountFeaturesBroker.numberEventsLeftForGroup(helper.getParentUid()) < 1) {
				throw new AccountLimitExceededException();
			}
		}

		if (helper.getName().length() > Event.MAX_NAME_LENGTH) {
			throw new TaskNameTooLongException();
		}

		Vote vote = new Vote(helper.getName(), convertedClosingDateTime, user, parent, helper.isIncludeSubGroups(), helper.getDescription());
		Set<String> assignMemberUids = helper.getAssignMemberUids();
		if (assignMemberUids != null && !assignMemberUids.isEmpty()) {
			assignMemberUids.add(helper.getUserUid()); // enforce creating user part of vote
		}
		vote.assignMembers(assignMemberUids);

		List<String> options = helper.getOptions();
		if (options != null && !options.isEmpty()) {
			log.info("Seeting vote options: {}", helper.getOptions());
			vote.setVoteOptions(options);
		}

		vote.setSpecialForm(helper.getSpecialForm());
		vote.setRandomize(helper.isRandomizeOptions());
		vote.setExcludeAbstention(helper.isExcludeAbstain());

		if (helper.getMultiLanguagePrompts() != null && !helper.getMultiLanguagePrompts().isEmpty()) {
			vote.setLangaugePrompts(helper.getMultiLanguagePrompts());
		}

		if (helper.getPostVotePrompts() != null && !helper.getMultiLanguagePrompts().isEmpty()) {
			vote.setPostVotePrompts(helper.getPostVotePrompts());
		}

		voteRepository.save(vote);

		if (!StringUtils.isEmpty(helper.getTaskImageKey())) {
			taskImageBroker.recordImageForTask(helper.getUserUid(), vote.getUid(), TaskType.VOTE,
					Collections.singleton(helper.getTaskImageKey()), EventLogType.IMAGE_AT_CREATION, null);
			vote.setImageUrl(taskImageBroker.getShortUrl(helper.getTaskImageKey()));
		}

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, vote, CREATED);
		bundle.addLog(eventLog);


		if (helper.isSendNotifications()) {
			Set<Notification> notifications = constructEventInfoNotifications(vote, eventLog, vote.getMembers());
			bundle.addNotifications(notifications);
		} else {
			log.info("Excluding notifications for vote, flag: {}", helper.isSendNotifications());
			vote.setStopNotifications(true);
			vote.setScheduledReminderActive(false);
		}

		logsAndNotificationsBroker.storeBundle(bundle);

		generateResponseTokens(vote);

		if (graphBroker != null) {
			final List<String> assignedUids = vote.getMembers().stream().map(User::getUid).collect(Collectors.toList());
			addTaskToGraph(vote.getUid(), TaskType.VOTE, assignedUids);
		}

		log.info("Completed creating vote: {}", vote);

		return vote;
	}

	private void addTaskToGraph(String taskUid, TaskType taskType, List<String> assignedUids) {
		graphBroker.addTaskToGraph(taskUid, taskType, assignedUids);
		graphBroker.annotateTask(taskUid, taskType, null, null, true);
	}

	@Override
    @Transactional
    public void updateReminderSettings(String userUid, String eventUid, EventReminderType reminderType, int customReminderMinutes) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(eventUid);
        Objects.requireNonNull(reminderType);

        Event event = eventRepository.findOneByUid(eventUid);
		User user = userService.load(userUid);

		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only the event creator can adjust settings");
		}

		event.setReminderType(reminderType);
        event.setCustomReminderMinutes(customReminderMinutes);
        event.updateScheduledReminderTime();

		// as above, check if this puts reminder time in past, and, if so, default it to one hour (on assumption meeting is urgent
		if (!reminderType.equals(DISABLED) && event.getScheduledReminderTime().isBefore(Instant.now())) {
            event.setReminderType(CUSTOM);
            event.setCustomReminderMinutes(60);
            event.updateScheduledReminderTime();
        }
    }

	private void validateEventStartTime(Instant eventStartDateTimeInstant) {
		Instant now = Instant.now();
		if (!eventStartDateTimeInstant.isAfter(now)) {
			throw new EventStartTimeNotInFutureException("Event start time " + eventStartDateTimeInstant.toString() +
					" is not in the future");
		}
	}

	@Override
	@Transactional
	public void cancel(String userUid, String eventUid, boolean notifyMembers) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userService.load(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		if (event.isCanceled()) {
			throw new IllegalStateException("Event is already canceled: " + event);
		}

		// cancelling has higher permission threshold than changing...only person who called event can do it
		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only event caller can cancel event");
		}

		event.setCanceled(true);
		event.setScheduledReminderActive(false);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, event, CANCELLED);
		bundle.addLog(eventLog);

		if (notifyMembers) {
			List<User> rsvpWithNoMembers = userService.findUsersThatRsvpForEvent(event, EventRSVPResponse.NO);
			getAllEventMembers(event).stream().filter(member -> !rsvpWithNoMembers.contains(member)).forEach(member -> {
				String message = messageAssemblingService.createEventCancelledMessage(member, event);
				Notification notification = new EventCancelledNotification(member, message, eventLog);
				bundle.addNotification(notification);
			});
		}

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional
	public void sendScheduledReminder(String eventUid) {
		Event event = eventRepository.findOneByUid(Objects.requireNonNull(eventUid));

		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}

		if (!event.isScheduledReminderActive()) {
			throw new IllegalStateException("Event is not scheduled for reminder: " + event);
		}

		// the notification picker & handler will handle error failures & resending (its proper concern)
		event.setNoRemindersSent(event.getNoRemindersSent() + 1);
		event.setScheduledReminderActive(false);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		// we set null for user here, because initiator is the app itself!
		EventLog eventLog = new EventLog(null, event, REMINDER);

		Set<User> excludedMembers = findEventReminderExcludedMembers(event);
		List<User> eventReminderNotificationSentMembers = userService.findUsersNotifiedAboutEvent(event, EventReminderNotification.class);
		excludedMembers.addAll(eventReminderNotificationSentMembers);

		final Set<User> members = getAllEventMembers(event);
		for (User member : members) {
			if (!excludedMembers.contains(member)) {
				String notificationMessage = messageAssemblingService.createScheduledEventReminderMessage(member, event);
				Notification notification = new EventReminderNotification(member, notificationMessage, eventLog);
				bundle.addNotification(notification);
			}
		}

		// we only want to include event log if there are some notifications
		if (!bundle.getNotifications().isEmpty()) {
			bundle.addLog(eventLog);
		}

		// for meeting calls, send out RSVPs to date to meeting's creator
		if (event.getEventType().equals(EventType.MEETING) && event.isRsvpRequired()) {
			Meeting meeting = (Meeting) event;
			LogsAndNotificationsBundle meetingRsvpTotalsBundle = constructMeetingRsvpTotalsBundle(meeting);
			bundle.addBundle(meetingRsvpTotalsBundle);
		}

		addLanguageNotifications(members, bundle);

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	private void addLanguageNotifications(Collection<User> users, LogsAndNotificationsBundle bundle) {
		final String languageMessage = messageAssemblingService.createMultiLanguageMessage();
		users.stream().filter(userService::shouldSendLanguageText)
				.forEach(user -> {
					log.info("Notifying a user about multiple languages ...");
					UserLog userLog = new UserLog(user.getUid(), UserLogType.NOTIFIED_LANGUAGES, null, UserInterfaceType.SYSTEM);
					bundle.addLog(userLog);
					bundle.addNotification(new UserLanguageNotification(user, languageMessage, userLog));
				});
	}

	private Set<User> findEventReminderExcludedMembers(Event event) {
		Set<User> excludedMembers = new HashSet<>();
		if (event.getEventType().equals(EventType.VOTE)) {
			List<User> votedMembers = userService.findUsersThatRsvpForEvent(event, null);
			excludedMembers.addAll(votedMembers);
		}
		if (event.getEventType().equals(EventType.MEETING)) {
			List<User> meetingDeclinedMembers = userService.findUsersThatRsvpForEvent(event, EventRSVPResponse.NO);
			excludedMembers.addAll(meetingDeclinedMembers);
		}
		return excludedMembers;
	}

	private LogsAndNotificationsBundle constructMeetingRsvpTotalsBundle(Meeting meeting) {
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(null, meeting, RSVP_TOTAL_MESSAGE);
		bundle.addLog(eventLog);

		ResponseTotalsDTO responseTotalsDTO = eventLogBroker.getResponseCountForEvent(meeting);

		User destination = meeting.getCreatedByUser();
		String message = messageAssemblingService.createMeetingRsvpTotalMessage(destination, meeting, responseTotalsDTO);
		Notification notification = new MeetingRsvpTotalsNotification(destination, message, eventLog);
		bundle.addNotification(notification);

		return bundle;
	}

	@Override
	@Transactional
	public void sendMeetingRSVPsToDate(String meetingUid) {
		Objects.requireNonNull(meetingUid);

		Meeting meeting = meetingRepository.findOneByUid(meetingUid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is cancelled: " + meeting);
		}
		if (!meeting.isRsvpRequired()) {
			throw new IllegalStateException("Meeting does not require RSVPs" + meeting);
		}

		LogsAndNotificationsBundle bundle = constructMeetingRsvpTotalsBundle(meeting);
		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional
	public void sendMeetingAcknowledgements(String meetingUid) {
		Objects.requireNonNull(meetingUid);

		Meeting meeting = meetingRepository.findOneByUid(meetingUid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is cancelled: " + meeting);
		}
		if (!meeting.isRsvpRequired()) {
			throw new IllegalStateException("Meeting did not require RSVPs" + meeting);
		}

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(null, meeting, THANK_YOU_MESSAGE);

		Set<User> tankYouNotificationSentMembers = new HashSet<>(
				userService.findUsersNotifiedAboutEvent(meeting, MeetingThankYouNotification.class));

		List<User> rsvpWithYesMembers = userService.findUsersThatRsvpForEvent(meeting, EventRSVPResponse.YES);
		for (User members : rsvpWithYesMembers) {
			if (!tankYouNotificationSentMembers.contains(members)) {
				String message = messageAssemblingService.createMeetingThankYourMessage(members, meeting);
				Notification notification = new MeetingThankYouNotification(members, message, eventLog);
				bundle.addNotification(notification);
			}
		}

		// we only want to include log if there are some notifications
		if (!bundle.getNotifications().isEmpty()) {
			bundle.addLog(eventLog);
		}

		addLanguageNotifications(rsvpWithYesMembers, bundle);
		;
		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional
    public void updateMeetingPublicStatus(String userUid, String meetingUid, boolean isPublic, GeoLocation location, UserInterfaceType interfaceType) {
        Objects.requireNonNull(meetingUid);

        User user = userService.load(userUid);
        Meeting meeting = meetingRepository.findOneByUid(meetingUid);

        if (!meeting.getCreatedByUser().equals(user)) {
        	throw new AccessDeniedException("Only the user that called a meeting can change whether it's public");
		}

		meeting.setPublic(isPublic);

        EventLog newLog = new EventLog(user, meeting, isPublic ? MADE_PUBLIC : MADE_PRIVATE);

        if (location != null) {
			newLog.setLocationWithSource(location, LocationSource.convertFromInterface(interfaceType));
			geoLocationBroker.logUserLocation(userUid, location.getLatitude(), location.getLongitude(), Instant.now(), UserInterfaceType.ANDROID);
		}

		if (isPublic) {
			geoLocationBroker.calculateMeetingLocationInstant(meetingUid, location, UserInterfaceType.WEB);
		}

		logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), Collections.emptySet()));
    }

	/*
	SECTION starts here for reading & retrieving user events
	 */
	@Override
    @Transactional(readOnly = true)
	public List<Event> getEventsNeedingResponseFromUser(User user) {
	    long startTime = System.currentTimeMillis();
		List<Event> cachedRsvps = cacheUtilService.getOutstandingResponseForUser(user.getUid());
		if (cachedRsvps != null) {
			return cachedRsvps;
		}

		Specification<Event> specs = EventSpecifications.upcomingEventsForUser(user);
		Specification<Event> stripMeetingCreated = (root, query, cb) -> cb.or(cb.notEqual(root.get("type"), EventType.MEETING),
				cb.notEqual(root.get("createdByUser"), user));
		List<Event> events = eventRepository
                .findAll(specs.and(stripMeetingCreated), new Sort(Sort.Direction.ASC, "createdDateTime")).stream()
				.filter(e -> !eventLogBroker.hasUserRespondedToEvent(e, user))
				.distinct().collect(Collectors.toList());
		cacheUtilService.putOutstandingResponseForUser(user.getUid(), events);
        log.info("time to check for responses: {} msecs", System.currentTimeMillis() - startTime);
		return events;
	}


	@Override
	@Transactional(readOnly = true)
	public boolean userHasEventsToView(User user, EventType type, EventListTimeType timeType) {
		Instant startTime = timeType.equals(EventListTimeType.FUTURE) ? Instant.now() : DateTimeUtil.getEarliestInstant();
		Instant endTime = timeType.equals(EventListTimeType.PAST) ? Instant.now() : DateTimeUtil.getVeryLongAwayInstant();
		return type.equals(EventType.MEETING) ?
				meetingRepository.countByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(user, startTime, endTime) > 0 :
				voteRepository.countByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(user, startTime, endTime) > 0;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<User, EventRSVPResponse> getRSVPResponses(Event event) {
		Map<User, EventRSVPResponse> rsvpResponses = new LinkedHashMap<>();

		List<User> usersAnsweredYes = userService.findUsersThatRsvpForEvent(event, EventRSVPResponse.YES);
		List<User> usersAnsweredNo = userService.findUsersThatRsvpForEvent(event, EventRSVPResponse.NO);

		@SuppressWarnings("unchecked") // someting strange with getAllMembers and <user> creates an unchecked warning here (hence suppressing)
				Set<User> users = new HashSet<>(event.getAllMembers());
		users.forEach(u -> rsvpResponses.put(u, usersAnsweredYes.contains(u) ? EventRSVPResponse.YES :
						usersAnsweredNo.contains(u) ? EventRSVPResponse.NO : EventRSVPResponse.NO_RESPONSE));

		return rsvpResponses;
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked") // given the conversion to page, this is otherwise spurious
	public Page<Event> getEventsUserCanView(User user, EventType eventType, EventListTimeType timeType, int pageNumber, int pageSize) {
		Instant startTime = !timeType.equals(EventListTimeType.FUTURE) ? DateTimeUtil.getEarliestInstant() : Instant.now();
		Instant endTime = !timeType.equals(EventListTimeType.PAST) ? DateTimeUtil.getVeryLongAwayInstant() : Instant.now();
		return (eventType.equals(EventType.MEETING)) ?
				(Page) meetingRepository.findByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(user, startTime, endTime,
						PageRequest.of(pageNumber, pageSize)) :
				(Page) voteRepository.findByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(user, startTime, endTime,
						PageRequest.of(pageNumber, pageSize));
	}

	@Override
	@Transactional(readOnly = true)
	public Event getMostRecentEvent(String groupUid) {
		Group group = groupRepository.findOneByUid(groupUid);
		return eventRepository.findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
	}

	@Override
	@Transactional(readOnly = true)
	public String getMostFrequentLocation(String groupUid) {
		Group group = groupRepository.findOneByUid(groupUid);
		CriteriaBuilder cb  = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Meeting> root  = query.from(Meeting.class);
		query.multiselect(root.get(Meeting_.eventLocation), cb.count(root.get(Meeting_.eventLocation)));
		query.where(cb.equal(root.get(Meeting_.ancestorGroup), group));
		query.groupBy(root.get(Meeting_.eventLocation));
		query.orderBy(cb.desc(cb.count(root.get(Meeting_.eventLocation))));
		List<Tuple> results = entityManager.createQuery(query).getResultList();
		log.info("results of query: {}", results);
		return results != null && !results.isEmpty() ? (String) results.get(0).get(0) : "";
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<Event> retrieveGroupEvents(Group group, User user, Instant periodStart, Instant periodEnd) {
		Sort sort = new Sort(Sort.Direction.DESC, "eventStartDateTime");
		Instant beginning = (periodStart == null) ? group.getCreatedDateTime() : periodStart;
		Instant end = (periodEnd == null) ? DateTimeUtil.getVeryLongAwayInstant() : periodEnd;

		Specification<Event> specifications = Specification.where(EventSpecifications.notCancelled())
				.and(EventSpecifications.hasGroupAsParent(group))
				.and(EventSpecifications.startDateTimeBetween(beginning, end))
				.and(EventSpecifications.hasAllUsersAssignedOrIsAssigned(user));

		return eventRepository.findAll(specifications, sort);
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public LocalTime getMostFrequentEventTime(String groupUid){
		Group group = groupRepository.findOneByUid(groupUid);

		String qry = "SELECT to_char(e.eventStartDateTime,'hh24:mi') " +
				"FROM Meeting e " +
				"WHERE e.ancestorGroup= :group ";

		TypedQuery<String> eventTypedQuery = entityManager.createQuery(qry,String.class)
						.setParameter("group",group);

		List<String> times = eventTypedQuery.getResultList();
		LocalTime localTime = null;
		if(times != null && !times.isEmpty()){
			Map<String, Long> result = times.stream()
					.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

			Map.Entry<String,Long> entry = result.entrySet().iterator().next();
			localTime = LocalTime.parse(entry.getKey());
		}

		return localTime;
	}

}
