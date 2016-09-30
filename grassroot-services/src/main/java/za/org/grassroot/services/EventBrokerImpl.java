package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.enums.EventListTimeType;
import za.org.grassroot.services.exception.TaskNameTooLongException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static za.org.grassroot.core.domain.EventReminderType.CUSTOM;
import static za.org.grassroot.core.domain.EventReminderType.DISABLED;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class EventBrokerImpl implements EventBroker {
	private final Logger logger = LoggerFactory.getLogger(EventBrokerImpl.class);

	@Autowired
	private EventLogBroker eventLogBroker;
	@Autowired
	private EventRepository eventRepository;
	@Autowired
	private VoteRepository voteRepository;
	@Autowired
	private MeetingRepository meetingRepository;
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private PermissionBroker permissionBroker;
	@Autowired
	private LogsAndNotificationsBroker logsAndNotificationsBroker;
	@Autowired
	private CacheUtilService cacheUtilService;
	@Autowired
	private MessageAssemblingService messageAssemblingService;

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
    public Meeting createMeeting(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime, String eventLocation,
								 boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
								 int customReminderMinutes, String description, Set<String> assignMemberUids, MeetingImportance importance) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(parentUid);
        Objects.requireNonNull(parentType);
        Objects.requireNonNull(assignMemberUids);

		Instant eventStartDateTimeInSystem = convertToSystemTime(eventStartDateTime, getSAST());
		validateEventStartTime(eventStartDateTimeInSystem);

		User user = userRepository.findOneByUid(userUid);
		MeetingContainer parent = uidIdentifiableRepository.findOneByUid(MeetingContainer.class, parentType, parentUid);

		// todo : implement for generic parent types, once have migrated to jpa specifications
		if (parentType.equals(JpaEntityType.GROUP)) {
			Event possibleDuplicate = checkForDuplicate(userUid, parentUid, name, eventStartDateTimeInSystem);
			if (possibleDuplicate != null && possibleDuplicate.getEventType().equals(EventType.MEETING)) {
				// todo : hand over to update meeting if anything different in parameters
				logger.info("Detected duplicate meeting creation, returning the already-created one ... ");
				return (Meeting) possibleDuplicate;
			}
		}

		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		if (name.length() > 40) {
			throw new TaskNameTooLongException();
		}

		Meeting meeting = new Meeting(name, eventStartDateTimeInSystem, user, parent, eventLocation,
                                      includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);

		if (!assignMemberUids.isEmpty()) {
			assignMemberUids.add(userUid); // enforces creating user part of meeting, if partial selection
		}

		meeting.assignMembers(assignMemberUids);

		// else sometimes reminder setting will be in the past, causing duplication of meetings; defaulting to 1 hours
		if (!reminderType.equals(DISABLED) && meeting.getScheduledReminderTime().isBefore(Instant.now())) {
			meeting.setCustomReminderMinutes(60);
			meeting.setReminderType(CUSTOM);
			meeting.updateScheduledReminderTime();
		}

		logger.info("Created meeting, with reminder type={} and time={}", meeting.getReminderType(),
					meeting.getScheduledReminderTime().toString());

		meetingRepository.save(meeting);

		eventLogBroker.rsvpForEvent(meeting.getUid(), meeting.getCreatedByUser().getUid(), EventRSVPResponse.YES);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, meeting, EventLogType.CREATED);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventInfoNotifications(meeting, eventLog);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		return meeting;
	}

	// introducing so that we can check catch & handle duplicate requests (e.g., from malfunctions on Android client offline->queue->sync function)
	@Transactional(readOnly = true)
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

	private Set<Notification> constructEventInfoNotifications(Event event, EventLog eventLog) {
		Set<Notification> notifications = new HashSet<>();
		for (User member : getAllEventMembers(event)) {
			cacheUtilService.clearRsvpCacheForUser(member, event.getEventType());
			String message = messageAssemblingService.createEventInfoMessage(member, event);
			Notification notification = new EventInfoNotification(member, message, eventLog);
			notifications.add(notification);
		}
		return notifications;
	}

	@SuppressWarnings("unchecked")
	private Set<User> getAllEventMembers(Event event) {
		return (Set<User>) event.getAllMembers();
	}

	@Override
    @Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation) {
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

		// note : for the moment, only the user who called the meeting can change it ... may alter in future, depends on user feedback
		if (!meeting.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only meeting caller can change meeting");
		}

		if (name.length() > 40) {
			throw new TaskNameTooLongException();
		}

        Instant convertedStartDateTime = convertToSystemTime(eventStartDateTime, getSAST());
        boolean startTimeChanged = !convertedStartDateTime.equals(meeting.getEventStartDateTime());
        if (startTimeChanged) {
            validateEventStartTime(convertedStartDateTime);
            meeting.setEventStartDateTime(convertedStartDateTime);
			meeting.updateScheduledReminderTime();
        }

        meeting.setName(name);
        meeting.setEventLocation(eventLocation);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, meeting, EventLogType.CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventChangedNotifications(meeting, eventLog, startTimeChanged);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);
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

		if (!meeting.getCreatedByUser().equals(user) ||
				permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
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

		EventLog eventLog = new EventLog(user, meeting, EventLogType.CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		// todo : handle member addition or removal differently (use a flag / enum to record meeting change type?)
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
						   boolean includeSubGroups, boolean relayable, String description, Set<String> assignMemberUids) {
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
		}

		if (name.length() > 40) {
			throw new TaskNameTooLongException();
		}

		Vote vote = new Vote(name, convertedClosingDateTime, user, parent, includeSubGroups, relayable, description);
		if (assignMemberUids != null && !assignMemberUids.isEmpty()) {
			assignMemberUids.add(userUid); // enforce creating user part of vote
		}
		vote.assignMembers(assignMemberUids);

		voteRepository.save(vote);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(user, vote, EventLogType.CREATED);
		bundle.addLog(eventLog);

		Set<Notification> notifications = constructEventInfoNotifications(vote, eventLog);
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

		EventLog eventLog = new EventLog(user, vote, EventLogType.CHANGE, null, startTimeChanged);
		bundle.addLog(eventLog);

		logsAndNotificationsBroker.storeBundle(bundle);

		return savedVote;
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

		EventLog eventLog = new EventLog(user, event, EventLogType.CANCELLED);
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
		EventLog eventLog = new EventLog(null, event, EventLogType.REMINDER);

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

		EventLog eventLog = new EventLog(null, meeting, EventLogType.RSVP_TOTAL_MESSAGE);
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

		EventLog eventLog = new EventLog(user, event, EventLogType.MANUAL_REMINDER);
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

		EventLog eventLog = new EventLog(null, meeting, EventLogType.THANK_YOU_MESSAGE);

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
	public void sendVoteResults(String voteUid) {
		Objects.requireNonNull(voteUid);
		Vote vote = voteRepository.findOneByUid(voteUid);

		logger.info("Sending vote results for vote", vote);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

		EventLog eventLog = new EventLog(null, vote, EventLogType.RESULT);

		ResponseTotalsDTO responseTotalsDTO = eventLogBroker.getResponseCountForEvent(vote);
		Set<User> voteResultsNotificationSentMembers = new HashSet<>(userRepository.findNotificationTargetsForEvent(
				vote, VoteResultsNotification.class));
		for (User member : getAllEventMembers(vote)) {
			if (!voteResultsNotificationSentMembers.contains(member)) {
				String message = messageAssemblingService.createVoteResultsMessage(member, vote,
						responseTotalsDTO.getYes(),
						responseTotalsDTO.getNo(),
						responseTotalsDTO.getMaybe(),
						responseTotalsDTO.getNumberNoRSVP());
				Notification notification = new VoteResultsNotification(member, message, eventLog);
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
	public void assignMembers(String userUid, String eventUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		// consider allowing organizers to also add/remove assignment
		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created meeting can add members");
		}

		event.assignMembers(assignMemberUids);
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String eventUid, Set<String> memberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created meeting can remove members");
		}

		event.removeAssignedMembers(memberUids);
	}

	/*
	SECTION starts here for reading & retrieving user events
	major todo : switch this to using a JPA specification model & quick findOne / count call, as in newly designed todo broker
	 */

	@Override
	@Transactional(readOnly = true)
	public List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
		logger.debug("getOutstandingResponseForUser..." + user.getPhoneNumber() + "...type..." + eventType.toString());
		List<Event> outstandingRSVPs = cacheUtilService.getOutstandingResponseForUser(user, eventType);

		if (outstandingRSVPs == null) {
			Map<Long, Long> eventMap = new HashMap<>();
			outstandingRSVPs = new ArrayList<>();
			List<Group> groups = groupRepository.findByMembershipsUserAndActiveTrue(user);

			if (groups != null) {
				logger.debug("getOutstandingResponseForUser...number of groups..." + groups.size());
				for (Group group : groups) {
					Predicate<Event> filter = event -> event.isRsvpRequired() && event.getEventType() == eventType
							&& ((eventType == EventType.MEETING && event.getCreatedByUser().getId() != user.getId()) || eventType != EventType.MEETING);
					Set<Event> upcomingEvents = group.getUpcomingEventsIncludingParents(filter);

					for (Event event : upcomingEvents) {
						if (eventType == EventType.VOTE && !checkUserJoinedBeforeVote(user, event, group)) {
							logger.info(String.format("Excluding vote %s for %s as the user joined group %s after the vote was called", event.getName(), user.getPhoneNumber(), group.getId()));
							continue;
						}

						if (!eventLogBroker.hasUserRespondedToEvent(event, user) && eventMap.get(event.getId()) == null) {
							outstandingRSVPs.add(event);
							eventMap.put(event.getId(), event.getId());
						}
					}

				}
				cacheUtilService.putOutstandingResponseForUser(user, eventType, outstandingRSVPs);
			}
		}

		return outstandingRSVPs;
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
		users.stream().forEach(u -> rsvpResponses.put(u, usersAnsweredYes.contains(u) ? EventRSVPResponse.YES :
						usersAnsweredNo.contains(u) ? EventRSVPResponse.NO : EventRSVPResponse.NO_RESPONSE));

		logger.info("worked through stream of {} users, got back a map of size {}", users.size(), rsvpResponses.size());

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
	@SuppressWarnings("unchecked")
	public List<Event> retrieveGroupEvents(Group group, EventType eventType, Instant periodStart, Instant periodEnd) {
		List<Event> events;
		Sort sort = new Sort(Sort.Direction.DESC, "eventStartDateTime");
		Instant beginning = (periodStart == null) ? group.getCreatedDateTime() : periodStart;
		Instant end = (periodEnd == null) ? DateTimeUtil.getVeryLongAwayInstant() : periodEnd;

		if (eventType == null) {
			events = eventRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end, sort);
		} else {
			events = eventType.equals(EventType.MEETING) ?
					(List) meetingRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end) :
					(List) voteRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end);
		}

		return events;
	}



}
