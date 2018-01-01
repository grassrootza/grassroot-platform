package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.notification.*;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.exception.TaskNameTooLongException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.task.enums.EventListTimeType;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static za.org.grassroot.core.domain.task.EventReminderType.CUSTOM;
import static za.org.grassroot.core.domain.task.EventReminderType.DISABLED;
import static za.org.grassroot.core.enums.EventLogType.*;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class EventBrokerImpl implements EventBroker {

	private final Logger logger = LoggerFactory.getLogger(EventBrokerImpl.class);

	@Value("${grassroot.events.limit.enabled:false}")
	private boolean eventMonthlyLimitActive;

	private final EventLogBroker eventLogBroker;
	private final EventRepository eventRepository;
	private final VoteRepository voteRepository;
	private final MeetingRepository meetingRepository;
	private final UidIdentifiableRepository uidIdentifiableRepository;
	private final UserRepository userRepository;
	private final GroupRepository groupRepository;
	private final PermissionBroker permissionBroker;
	private final LogsAndNotificationsBroker logsAndNotificationsBroker;
	private final CacheUtilService cacheUtilService;
	private final MessageAssemblingService messageAssemblingService;
	private final AccountGroupBroker accountGroupBroker;
	private final GeoLocationBroker geoLocationBroker;
	private final TaskImageBroker taskImageBroker;

	private final EntityManager entityManager;

	@Autowired
	public EventBrokerImpl(MeetingRepository meetingRepository, EventLogBroker eventLogBroker, EventRepository eventRepository, VoteRepository voteRepository, UidIdentifiableRepository uidIdentifiableRepository, UserRepository userRepository, AccountGroupBroker accountGroupBroker, GroupRepository groupRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, CacheUtilService cacheUtilService, MessageAssemblingService messageAssemblingService, MeetingLocationRepository meetingLocationRepository, GeoLocationBroker geoLocationBroker, TaskImageBroker taskImageBroker,EntityManager entityManager) {
		this.meetingRepository = meetingRepository;
		this.eventLogBroker = eventLogBroker;
		this.eventRepository = eventRepository;
		this.voteRepository = voteRepository;
		this.uidIdentifiableRepository = uidIdentifiableRepository;
		this.userRepository = userRepository;
		this.accountGroupBroker = accountGroupBroker;
		this.groupRepository = groupRepository;
		this.permissionBroker = permissionBroker;
		this.logsAndNotificationsBroker = logsAndNotificationsBroker;
		this.cacheUtilService = cacheUtilService;
		this.messageAssemblingService = messageAssemblingService;
		this.geoLocationBroker = geoLocationBroker;
		this.taskImageBroker = taskImageBroker;
		this.entityManager = entityManager;
	}

	@Override
	public Event load(String eventUid) {
		Objects.requireNonNull(eventUid);
		return eventRepository.findOneByUid(eventUid);
	}

	@Override
	public Meeting loadMeeting(String meetingUid) {
		return meetingRepository.findOneByUid(meetingUid);
	}


	@Override
	@Transactional
	public Meeting createMeeting(MeetingBuilderHelper helper) {
		helper.validateMeetingFields();

		User user = userRepository.findOneByUid(helper.getUserUid());
		MeetingContainer parent = uidIdentifiableRepository.findOneByUid(MeetingContainer.class,
				helper.getParentType(), helper.getParentUid());

		Event possibleDuplicate = checkForDuplicate(helper.getUserUid(), helper.getParentUid(), helper.getName(), helper.getStartInstant());
		if (possibleDuplicate != null) { // todo : hand over to update meeting if anything different in parameters
			return (Meeting) possibleDuplicate;
		}

		checkForEventLimit(helper.getParentUid());
		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		Meeting meeting = helper.convertToBuilder(user, parent).createMeeting();
		// else sometimes reminder setting will be in the past, causing duplication of meetings; defaulting to 1 hours
		logger.debug("helper reminder type: {}, meeting scheduled reminder time: {}", helper.getReminderType(), meeting.getScheduledReminderTime());
		if (!helper.getReminderType().equals(DISABLED) && meeting.getScheduledReminderTime().isBefore(Instant.now())) {
			meeting.setCustomReminderMinutes(60);
			meeting.setReminderType(CUSTOM);
			meeting.updateScheduledReminderTime();
		}

		logger.info("Created meeting, with importance={}, reminder type={} and time={}", meeting.getImportance(), meeting.getReminderType(), meeting.getScheduledReminderTime());

		meetingRepository.save(meeting);

		eventLogBroker.rsvpForEvent(meeting.getUid(), meeting.getCreatedByUser().getUid(), EventRSVPResponse.YES);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		EventLog eventLog = new EventLog(user, meeting, CREATED);
		bundle.addLog(eventLog);

		if (!StringUtils.isEmpty(helper.getTaskImageKey())) {
			taskImageBroker.recordImageForTask(helper.getUserUid(), meeting.getUid(), TaskType.MEETING,
					helper.getTaskImageKey(), EventLogType.IMAGE_AT_CREATION);
			meeting.setImageUrl(taskImageBroker.getShortUrl(helper.getTaskImageKey()));
		}

		Set<Notification> notifications = constructEventInfoNotifications(meeting, eventLog, meeting.getMembers());
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		return meeting;
	}

	@Override
	public List<Meeting> publicMeetingsUserIsNotPartOf(String term, User user){
		return meetingRepository.publicMeetingsUserIsNotPartOfWithsSearchTerm(term, user);
	}

	private void checkForEventLimit(String parentUid) {
		if (eventMonthlyLimitActive && accountGroupBroker.numberEventsLeftForGroup(parentUid) < 1) {
			throw new AccountLimitExceededException();
		}
	}

	// introducing so that we can check catch & handle duplicate requests (e.g., from malfunctions on Android client offline->queue->sync function)
	private Event checkForDuplicate(String userUid, String parentGroupUid, String name, Instant startDateTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentGroupUid);
		Objects.requireNonNull(name);
		Objects.requireNonNull(startDateTime);

		Instant intervalStart = startDateTime.minus(180, ChronoUnit.SECONDS);;
		Instant intervalEnd = startDateTime.plus(180, ChronoUnit.SECONDS);

		User user = userRepository.findOneByUid(userUid);
		Group parentGroup = groupRepository.findOneByUid(parentGroupUid);

		return eventRepository.findOneByCreatedByUserAndParentGroupAndNameAndEventStartDateTimeBetweenAndCanceledFalse(user, parentGroup, name, intervalStart, intervalEnd);
	}

	private Set<Notification> constructEventInfoNotifications(Event event, EventLog eventLog, Set<User> usersToNotify) {
		Set<Notification> notifications = new HashSet<>();
		logger.debug("constructing event notifications ... has image URL? : {}", event.getImageUrl());
		for (User member : usersToNotify) {
			cacheUtilService.clearRsvpCacheForUser(member, event.getEventType());
			String message = messageAssemblingService.createEventInfoMessage(member, event);
			Notification notification = new EventInfoNotification(member, message, eventLog);
			notifications.add(notification);
		}
		// check if creating user is on Android, in which case, add an explicit SMS
		if (event.getCreatedByUser().getMessagingPreference().equals(DeliveryRoute.ANDROID_APP)) {
			logger.debug("event creator on Android, sending an SMS so they know format");
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

		User user = userRepository.findOneByUid(userUid);
        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

        boolean meetingChanged = false;

        if (meeting.isCanceled()) {
            throw new IllegalStateException("Meeting is canceled: " + meeting);
        }

		// note : for the moment, only the user who called the meeting can change it ... may alter in future, depends on user feedback
		if (!meeting.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only meeting caller can change meeting");
		}

		if (!StringUtils.isEmpty(name) && name.length() > 40) {
			throw new TaskNameTooLongException();
		}

		boolean startTimeChanged;
        if (eventStartDateTime != null) {
			Instant convertedStartDateTime = convertToSystemTime(eventStartDateTime, getSAST());
			Duration timeBetween = Duration.between(convertedStartDateTime, meeting.getEventStartDateTime());
			logger.info("duration between old and new dates and times: {} seconds", timeBetween.getSeconds());
			startTimeChanged = Math.abs(timeBetween.getSeconds()) > 90; // since otherwise pick up spurious non-equals because of small instant changes
			if (startTimeChanged) {
				logger.info("start time changed! to : " + convertedStartDateTime);
				validateEventStartTime(convertedStartDateTime);
				meeting.setEventStartDateTime(convertedStartDateTime);
				meeting.updateScheduledReminderTime();
				meetingChanged = true;
			}
		} else {
        	startTimeChanged = false;
		}

        if (!StringUtils.isEmpty(name) && !meeting.getName().equals(name)) {
        	logger.info("Meeting title changed!");
        	meeting.setName(name);
        	meetingChanged = true;
		}

        if (!StringUtils.isEmpty(eventLocation) && !meeting.getEventLocation().equals(eventLocation)) {
			logger.info("Meeting location changed!");
        	meeting.setEventLocation(eventLocation);
			meetingChanged = true;
		}

		if (meetingChanged) {
			LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

			EventLog eventLog = new EventLog(user, meeting, CHANGE, null, startTimeChanged);
			bundle.addLog(eventLog);

			Set<Notification> notifications = constructEventChangedNotifications(meeting, eventLog, startTimeChanged);
			bundle.addNotifications(notifications);

			logsAndNotificationsBroker.storeBundle(bundle);
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

		User user = userRepository.findOneByUid(userUid);
		Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is canceled: " + meeting);
		}

		if (!meeting.getCreatedByUser().equals(user) &&
				!permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
			throw new AccessDeniedException("Error! Only meeting caller or group organizer can change meeting");
		}

		if (name.length() > 40) {
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

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, meeting, CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventChangedNotifications(meeting, eventLog, startTimeChanged);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	private Set<Notification> constructEventChangedNotifications(Event event, EventLog eventLog, boolean startTimeChanged) {
		Set<User> rsvpWithNoMembers = new HashSet<>(userRepository.findUsersThatRSVPNoForEvent(event));
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
	public Vote createVote(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime,
                           boolean includeSubGroups, String description, Set<String> assignMemberUids, List<String> options) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(parentType);

		Instant convertedClosingDateTime = convertToSystemTime(eventStartDateTime, getSAST());
        validateEventStartTime(convertedClosingDateTime);

		User user = userRepository.findOneByUid(userUid);
		VoteContainer parent = uidIdentifiableRepository.findOneByUid(VoteContainer.class, parentType, parentUid);

		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		if (parentType.equals(JpaEntityType.GROUP)) {
			Event possibleDuplicate = checkForDuplicate(userUid, parentUid, name, convertedClosingDateTime);
			if (possibleDuplicate != null && possibleDuplicate.getEventType().equals(EventType.VOTE)) {
				logger.info("Detected duplicate vote creation, returning the already-created one ... ");
				return (Vote) possibleDuplicate;
			}

			if (eventMonthlyLimitActive && accountGroupBroker.numberEventsLeftForGroup(parentUid) < 1) {
				throw new AccountLimitExceededException();
			}
		}

		if (name.length() > 40) {
			throw new TaskNameTooLongException();
		}

		Vote vote = new Vote(name, convertedClosingDateTime, user, parent, includeSubGroups, description);
		if (assignMemberUids != null && !assignMemberUids.isEmpty()) {
			assignMemberUids.add(userUid); // enforce creating user part of vote
		}
		vote.assignMembers(assignMemberUids);
		if (options != null && !options.isEmpty()) {
			vote.setVoteOptions(options);
		}

		voteRepository.save(vote);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, vote, CREATED);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventInfoNotifications(vote, eventLog, vote.getMembers());
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		return vote;
	}

	@Override
	@Transactional
	public Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(voteUid);

		User user = userRepository.findOneByUid(userUid);
		Vote vote = voteRepository.findOneByUid(voteUid);

		if (vote.isCanceled()) {
			throw new IllegalStateException("Vote is canceled: " + vote);
		}

		if (!vote.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created vote can update it");
		}

		Instant convertedClosingDateTime = convertToSystemTime(eventStartDateTime, getSAST());

		boolean startTimeChanged = !vote.getEventStartDateTime().equals(convertedClosingDateTime);
		if (startTimeChanged) {
			validateEventStartTime(convertedClosingDateTime);
			vote.setEventStartDateTime(convertedClosingDateTime);
			vote.updateScheduledReminderTime();
		}

		vote.setDescription(description);

		// note: as of now, we don't need to send anything, hence just do an explicit call to repo and return the Vote

		Vote savedVote = voteRepository.save(vote);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, vote, CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		logsAndNotificationsBroker.storeBundle(bundle);

		return savedVote;
	}

	@Override
	@Transactional
	public void updateVoteClosingTime(String userUid, String eventUid, LocalDateTime closingDateTime) {
    	Objects.requireNonNull(userUid);
    	Objects.requireNonNull(eventUid);
    	Objects.requireNonNull(closingDateTime);

    	Event event = eventRepository.findOneByUid(eventUid);
    	User user = userRepository.findOneByUid(userUid);

    	if (!event.getCreatedByUser().equals(user)) {
    		throw new AccessDeniedException("Only vote caller can change closing date time");
		}

		Instant convertedClosing = DateTimeUtil.convertToSystemTime(closingDateTime, DateTimeUtil.getSAST());
    	if (convertedClosing.isBefore(Instant.now())) {
    		throw new EventStartTimeNotInFutureException("Error! Vote changing to past");
		}

		event.setEventStartDateTime(convertedClosing);
    	event.updateScheduledReminderTime();
	}

	@Override
    @Transactional
    public void updateReminderSettings(String userUid, String eventUid, EventReminderType reminderType, int customReminderMinutes) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(eventUid);
        Objects.requireNonNull(reminderType);

        Event event = eventRepository.findOneByUid(eventUid);
		User user = userRepository.findOneByUid(userUid);

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

    @Override
	@Transactional
    public void updateDescription(String userUid, String eventUid, String eventDescription) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(eventUid);

        Event event = eventRepository.findOneByUid(eventUid);
        User user = userRepository.findOneByUid(userUid);

        if (!event.getCreatedByUser().equals(user)) {
        	throw new AccessDeniedException("Error! Only the user who created the meeting can change its description");
		}

		event.setDescription(eventDescription);
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
	public void cancel(String userUid, String eventUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
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

		List<User> rsvpWithNoMembers = userRepository.findUsersThatRSVPNoForEvent(event);
		for (User member : getAllEventMembers(event)) {
			if (!rsvpWithNoMembers.contains(member)) {
				String message = messageAssemblingService.createEventCancelledMessage(member, event);
				Notification notification = new EventCancelledNotification(member, message, eventLog);
				bundle.addNotification(notification);
			}
		}

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional
	public void sendScheduledReminder(String uid) {
		Objects.requireNonNull(uid);

		Event event = eventRepository.findOneByUid(uid);

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
		List<User> eventReminderNotificationSentMembers = userRepository.findNotificationTargetsForEvent(
				event, EventReminderNotification.class);
		excludedMembers.addAll(eventReminderNotificationSentMembers);

		for (User member : getAllEventMembers(event)) {
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

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	private Set<User> findEventReminderExcludedMembers(Event event) {
		Set<User> excludedMembers = new HashSet<>();
		if (event.getEventType().equals(EventType.VOTE)) {
			List<User> votedMembers = userRepository.findUsersThatRSVPForEvent(event);
			excludedMembers.addAll(votedMembers);
		}
		if (event.getEventType().equals(EventType.MEETING)) {
			List<User> meetingDeclinedMembers = userRepository.findUsersThatRSVPNoForEvent(event);
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
	public void sendManualReminder(String userUid, String eventUid) {
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);

		Event event = eventRepository.findOneByUid(eventUid);
		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}

		logger.info("Sending manual reminder for event {} with message {}", event);
		event.setNoRemindersSent(event.getNoRemindersSent() + 1);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, event, MANUAL_REMINDER);
		bundle.addLog(eventLog);

		Set<User> excludedMembers = findEventReminderExcludedMembers(event);
		for (User member : getAllEventMembers(event)) {
			if (!excludedMembers.contains(member)) {
				Notification notification = new EventReminderNotification(member, "manually triggered reminder", eventLog);
				bundle.addNotification(notification);
			}
		}

		logsAndNotificationsBroker.storeBundle(bundle);
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
				userRepository.findNotificationTargetsForEvent(meeting, MeetingThankYouNotification.class));

		List<User> rsvpWithYesMembers = userRepository.findUsersThatRSVPYesForEvent(meeting);
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

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional
	@SuppressWarnings("unchecked") // for weirdness on event.getmembers
	public void assignMembers(String userUid, String eventUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);
		Objects.requireNonNull(assignMemberUids);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		Set<User> priorMembers = (Set<User>) event.getMembers();

		// consider allowing organizers to also add/remove assignment
		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created meeting can add members");
		}

		event.assignMembers(assignMemberUids);

		Set<User> addedMembers = (Set<User>) event.getMembers();
		addedMembers.removeAll(priorMembers);

		if (!addedMembers.isEmpty()) {
			EventLog newLog = new EventLog(user, event, ASSIGNED_ADDED);
			Set<Notification> notifications = constructEventInfoNotifications(event, newLog, addedMembers);
			logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), notifications));
		}
	}

	@Override
	@Transactional
	@SuppressWarnings("unchecked")
	public void removeAssignedMembers(String userUid, String eventUid, Set<String> memberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		Set<User> membersRemoved = (Set<User>) event.getMembers();

		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created meeting can remove members");
		}

		event.removeAssignedMembers(memberUids);

		Set<User> finishedMembers = (Set<User>) event.getMembers();
		membersRemoved.removeAll(finishedMembers);
		if (!membersRemoved.isEmpty()) {
			EventLog newLog = new EventLog(user, event, ASSIGNED_REMOVED);
			logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), Collections.emptySet()));
		}
	}

    @Override
	@Transactional
    public void updateMeetingPublicStatus(String userUid, String meetingUid, boolean isPublic, GeoLocation location, UserInterfaceType interfaceType) {
        Objects.requireNonNull(meetingUid);

        User user = userRepository.findOneByUid(userUid);
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
	major todo : switch this to using a JPA specification model & quick findOne / count call, as in newly designed todo broker
	 */

	@Override
	@Transactional(readOnly = true)
	public List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
		long startTime = System.currentTimeMillis();
	    List<Event> cachedRsvps = cacheUtilService.getOutstandingResponseForUser(user, eventType);
		if (cachedRsvps != null) {
			return cachedRsvps;
		} else {
			Map<Long, Long> eventMap = new HashMap<>();
			final List<Event> outstandingRSVPs = new ArrayList<>();
			Predicate<Event> filter = event ->
					event.isRsvpRequired()
							&& event.getEventType() == eventType
							&& ((eventType == EventType.MEETING && event.getCreatedByUser().getId() != user.getId())
							|| eventType != EventType.MEETING);
			// todo: well, fix this (consolidate into one criteria query)
			groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user)
					.forEach(g -> g.getUpcomingEventsIncludingParents(filter)
							.stream()
							.filter(e -> eventType == EventType.MEETING || checkUserJoinedBeforeVote(user, e, g))
							.filter(e -> !eventLogBroker.hasUserRespondedToEvent(e, user) && eventMap.get(e.getId()) == null)
							.forEach(e -> {
								outstandingRSVPs.add(e);
								eventMap.put(e.getId(), e.getId());
							}));
            cacheUtilService.putOutstandingResponseForUser(user, eventType, outstandingRSVPs);
			logger.info("time to check for responses: {} msecs", System.currentTimeMillis() - startTime);
			return outstandingRSVPs;
		}
	}

	//N.B. remove this if statement if you want to allow votes for people that joined the group late
	private boolean checkUserJoinedBeforeVote(User user, Event event, Group group) {
		Membership membership = group.getMembership(user);
		return membership != null && membership.getJoinTime().isBefore(event.getCreatedDateTime());
	}

	@Override
	public boolean userHasResponsesOutstanding(User user, EventType eventType) {
		return !getOutstandingResponseForUser(user, eventType).isEmpty();
	}


	@Override
	@Transactional(readOnly = true)
	public EventListTimeType userHasEventsToView(User user, EventType type) {
		boolean pastEvents = userHasEventsToView(user, type, EventListTimeType.PAST);
		boolean futureEvents = userHasEventsToView(user, type, EventListTimeType.FUTURE);
		return (pastEvents && futureEvents) ? EventListTimeType.BOTH
				: pastEvents ? EventListTimeType.PAST
				: futureEvents ? EventListTimeType.FUTURE
				: EventListTimeType.NONE;
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

		List<User> usersAnsweredYes = userRepository.findUsersThatRSVPYesForEvent(event);
		List<User> usersAnsweredNo = userRepository.findUsersThatRSVPNoForEvent(event);

		@SuppressWarnings("unchecked") // someting stange with getAllMembers and <user> creates an unchecked warning here (hence suppressing)
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
						new PageRequest(pageNumber, pageSize)) :
				(Page) voteRepository.findByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(user, startTime, endTime,
						new PageRequest(pageNumber, pageSize));
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
		logger.info("results of query: {}", results);
		return results != null && !results.isEmpty() ? (String) results.get(0).get(0) : "";
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<Event> retrieveGroupEvents(Group group, EventType eventType, Instant periodStart, Instant periodEnd) {
		Sort sort = new Sort(Sort.Direction.DESC, "eventStartDateTime");
		Instant beginning = (periodStart == null) ? group.getCreatedDateTime() : periodStart;
		Instant end = (periodEnd == null) ? DateTimeUtil.getVeryLongAwayInstant() : periodEnd;

		Specifications<Event> specifications = Specifications.where(EventSpecifications.notCancelled())
				.and(EventSpecifications.hasGroupAsParent(group))
				.and(EventSpecifications.startDateTimeBetween(beginning, end));

		return (eventType == null) ? eventRepository.findAll(specifications, sort) :
				(List) (eventType.equals(EventType.MEETING) ? meetingRepository.findAll(specifications, sort) :
					voteRepository.findAll(specifications, sort));
	}



}
