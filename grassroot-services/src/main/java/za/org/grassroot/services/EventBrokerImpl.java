package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

		if (parentType.equals(JpaEntityType.GROUP)) {
			Meeting possibleDuplicate = checkForDuplicateMeeting(user, (Group) parent, name, eventStartDateTimeInSystem);
			if (possibleDuplicate != null) {
				// todo : hand over to update meeting if anything different in parameters
				logger.info("Detected duplicate meeting creation, returning the already-created one ... ");
				return possibleDuplicate;
			}
		}

		permissionBroker.validateGroupPermission(user, parent.getThisOrAncestorGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

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
	// todo : make generic so that works for any parent container type & for votes too ...
	@Transactional(readOnly = true)
	private Meeting checkForDuplicateMeeting(User user, Group group, String name, Instant eventStartDateTimeSystem) {
		Objects.requireNonNull(user);

		logger.info("Checking for duplicate meeting ...");

		Instant start = eventStartDateTimeSystem.minus(180, ChronoUnit.SECONDS);
		Instant end = eventStartDateTimeSystem.plus(180, ChronoUnit.SECONDS);

		return meetingRepository.findOneByCreatedByUserAndParentGroupAndNameAndEventStartDateTimeBetweenAndCanceledFalse(user, group, name, start, end);
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
				// todo : maybe move this into a single method in the assigned members container
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

		User user = userRepository.findOneByUid(userUid); // todo: permission checking, once worked out proper logic
		Vote vote = voteRepository.findOneByUid(voteUid);

		if (vote.isCanceled()) {
			throw new IllegalStateException("Vote is canceled: " + vote);
		}

		Instant convertedClosingDateTime = convertToSystemTime(eventStartDateTime, getSAST());

		boolean startTimeChanged = !vote.getEventStartDateTime().equals(eventStartDateTime);
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

        // todo: permission checking
        Event event = eventRepository.findOneByUid(eventUid);
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

		event.setNoRemindersSent(event.getNoRemindersSent() + 1);
		event.setScheduledReminderActive(false);

		// todo: figure out how to get and handle errors from here (i.e., so don't set reminders false if an error)
		// we set null here, because initiator is the app itself!

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

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

		ResponseTotalsDTO responseTotalsDTO = eventLogBroker.getVoteResultsForEvent(vote);
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
}
