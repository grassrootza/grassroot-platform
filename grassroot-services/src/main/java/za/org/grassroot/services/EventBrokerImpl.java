package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.*;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.EventWithTotals;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static za.org.grassroot.core.domain.EventReminderType.CUSTOM;
import static za.org.grassroot.core.domain.EventReminderType.DISABLED;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class EventBrokerImpl implements EventBroker {
	private final Logger logger = LoggerFactory.getLogger(EventBrokerImpl.class);

	@Autowired
	private EventLogManagementService eventLogManagementService;
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
    private ApplicationEventPublisher applicationEventPublisher;


	@Autowired
	private CacheUtilService cacheUtilService;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private EventLogRepository eventLogRepository;
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
	public List<Event> loadEventsUserCanManage(String userUid, EventType eventType, int pageNumber, int pageSize) {
		User user = userRepository.findOneByUid(userUid);
		Page<Event> pageOfEvents = eventRepository.
			findByCreatedByUserAndEventStartDateTimeGreaterThanAndEventTypeAndCanceledFalse(user, Instant.now(), EventType.MEETING,
																							new PageRequest(pageNumber, pageSize));
		return pageOfEvents.getContent();
	}

	@Override
	@Transactional
	public Meeting createMeeting(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime, String eventLocation,
															  boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
															  int customReminderMinutes, String description, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(parentType);
		Objects.requireNonNull(assignMemberUids);

		Instant eventStartDateTimeInSystem = convertToSystemTime(eventStartDateTime, getSAST());
		validateEventStartTime(eventStartDateTimeInSystem);

		User user = userRepository.findOneByUid(userUid);
		MeetingContainer parent = uidIdentifiableRepository.findOneByUid(MeetingContainer.class, parentType, parentUid);

		permissionBroker.validateGroupPermission(user, parent.resolveGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		Meeting meeting = new Meeting(name, eventStartDateTimeInSystem, user, parent, eventLocation,
                                      includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		meeting.assignMembers(assignMemberUids);

		// else sometimes reminder setting will be in the past, causing duplication of meetings; defaulting to 1 hours
		if (!reminderType.equals(DISABLED) && meeting.getScheduledReminderTime().isBefore(Instant.now())) {
			meeting.setCustomReminderMinutes(60);
			meeting.setReminderType(CUSTOM);
			meeting.updateScheduledReminderTime();
		}

		meetingRepository.save(meeting);

		eventLogManagementService.rsvpForEvent(meeting, meeting.getCreatedByUser(), EventRSVPResponse.YES);

		// create history log
		EventLog eventLog = new EventLog(user, meeting, EventLogType.MeetingCreated, null);
		eventLogRepository.save(eventLog);

		// create notifications
		registerNotificationsForEventMembers(meeting, destination -> {
			cacheUtilService.clearRsvpCacheForUser(destination, meeting.getEventType());
			return new EventNotification(destination, meeting, eventLog);
		});

		return meeting;
	}

	private void registerNotificationsForEventMembers(Event event, Function<User, Notification> notificationConstructor) {
		for (User destination : (Set<User>) event.getAllMembers()) {
			Notification notification = notificationConstructor.apply(destination);
			if (notification != null) {
				notificationRepository.save(notification);
			}
		}
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

		EventLog eventLog = new EventLog(user, meeting, EventLogType.EventChange, null);
		eventLogRepository.save(eventLog);

		registerEventChangedNotifications(meeting, eventLog, startTimeChanged);
    }

	@Override
	@Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime,
							  String eventLocation, boolean includeSubGroups, boolean rsvpRequired,
							  boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingUid);
		Objects.requireNonNull(name);
		Objects.requireNonNull(eventStartDateTime);
		Objects.requireNonNull(eventLocation);
		Objects.requireNonNull(reminderType);

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
		meeting.setIncludeSubGroups(includeSubGroups);
		meeting.setRsvpRequired(rsvpRequired);
		meeting.setRelayable(relayable);
		meeting.setReminderType(reminderType);
		meeting.setCustomReminderMinutes(customReminderMinutes);

		meeting.updateScheduledReminderTime();

		EventLog eventLog = new EventLog(user, meeting, EventLogType.EventChange, null);
		eventLogRepository.save(eventLog);

		registerEventChangedNotifications(meeting, eventLog, startTimeChanged);
	}

	private void registerEventChangedNotifications(Event event, EventLog eventLog, boolean startTimeChanged) {
		List<User> rsvpWithNoMembers = userRepository.findUsersThatRSVPNoForEvent(event);
		registerNotificationsForEventMembers(event, destination -> {
			if (startTimeChanged || !rsvpWithNoMembers.contains(destination)) {
				return new EventChangedNotification(destination, eventLog);
			}
			return null;
		});
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

		permissionBroker.validateGroupPermission(user, parent.resolveGroup(), Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		Vote vote = new Vote(name, convertedClosingDateTime, user, parent, includeSubGroups, relayable, description);
		vote.assignMembers(assignMemberUids);

		voteRepository.save(vote);

		// create history log
		EventLog eventLog = new EventLog(user, vote, EventLogType.VoteCreated, null);
		eventLogRepository.save(eventLog);

		// create notifications
		registerNotificationsForEventMembers(vote, destination -> {
			cacheUtilService.clearRsvpCacheForUser(destination, vote.getEventType());
			return new EventNotification(destination, vote, eventLog);
		});

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

		if (!vote.getEventStartDateTime().equals(eventStartDateTime)) {
			validateEventStartTime(convertedClosingDateTime);
			vote.setEventStartDateTime(convertedClosingDateTime);
			vote.updateScheduledReminderTime();
		}

		vote.setDescription(description);

		// note: as of now, we don't need to send anything, hence just do an explicit call to repo and return the Vote

		Vote savedVote = voteRepository.save(vote);

		EventLog eventLog = new EventLog(user, vote, EventLogType.EventChange, null);
		eventLogRepository.save(eventLog);

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
		// commenting this out just for now, because parser is unreliable & haven't built recovery means so will annoy users
		// come back to this soon though
		/*if (!eventStartDateTime.toInstant().isAfter(now)) {
			throw new EventStartTimeNotInFutureException("Event start time " + eventStartDateTime + " is not in the future");
		}*/
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

		EventLog eventLog = new EventLog(user, event, EventLogType.EventCancelled, null);
		eventLogRepository.save(eventLog);

		List<User> rsvpWithNoMembers = userRepository.findUsersThatRSVPNoForEvent(event);
		registerNotificationsForEventMembers(event, destination -> {
			if (!rsvpWithNoMembers.contains(destination)) {
				return new EventCancelledNotification(destination, eventLog);
			}
			return null;
		});
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
		EventLog eventLog = new EventLog(null, event, EventLogType.EventReminder, null);
		eventLogRepository.save(eventLog);

		Set<User> excludedMembers = findEventReminderExcludedMembers(event);
		List<User> eventReminderNotificationSentMembers = userRepository.findUsersWithNotificationSentForEvent(
				event, EventReminderNotification.class);
		excludedMembers.addAll(eventReminderNotificationSentMembers);

		registerNotificationsForEventMembers(event, destination -> {
			if (!excludedMembers.contains(destination)) {
				return new EventReminderNotification(destination, eventLog);
			}
			return null;
		});

		// for meeting calls, send out RSVPs to date to meeting's creator
		if (event.getEventType().equals(EventType.MEETING) && event.isRsvpRequired()) {
			Meeting meeting = (Meeting) event;

			registerMeetingResponsesLogAndNotification(meeting);
		}
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

	private void registerMeetingResponsesLogAndNotification(Meeting meeting) {
		EventLog eventLog = new EventLog(null, meeting, EventLogType.EventRsvpTotalMessage, null);
		eventLogRepository.save(eventLog);

		ResponseTotalsDTO responseTotalsDTO = eventLogManagementService.getResponseCountForEvent(meeting);

		User destination = meeting.getCreatedByUser();
		String message = messageAssemblingService.createMeetingRsvpTotalMessage(destination, meeting, responseTotalsDTO);
		notificationRepository.save(new MeetingRsvpTotalsNotification(destination, eventLog, message));
	}

	@Override
	@Transactional
	public void sendManualReminder(String userUid, String eventUid, String message) {
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);

		Event event = eventRepository.findOneByUid(eventUid);
		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}

		logger.info("Sending manual reminder for event {} with message {}", event, message);
		event.setNoRemindersSent(event.getNoRemindersSent() + 1);

		EventLog eventLog = new EventLog(user, event, EventLogType.EventManualReminder, message);
		eventLogRepository.save(eventLog);

		Set<User> excludedMembers = findEventReminderExcludedMembers(event);
		registerNotificationsForEventMembers(event, destination -> {
			if (!excludedMembers.contains(destination)) {
				return new EventReminderNotification(destination, eventLog);
			}
			return null;
		});
	}

	@Override
	@Transactional
	public void sendMeetingRSVPsToDate(String uid) {
		Objects.requireNonNull(uid);

		Meeting meeting = meetingRepository.findOneByUid(uid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is cancelled: " + meeting);
		}
		if (!meeting.isRsvpRequired()) {
			throw new IllegalStateException("Meeting does not require RSVPs" + meeting);
		}

		registerMeetingResponsesLogAndNotification(meeting);
	}

	@Override
	@Transactional
	public void sendMeetingAcknowledgements(String uid) {
		Objects.requireNonNull(uid);

		Meeting meeting = meetingRepository.findOneByUid(uid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is cancelled: " + meeting);
		}
		if (!meeting.isRsvpRequired()) {
			throw new IllegalStateException("Meeting did not require RSVPs" + meeting);
		}

		EventLog eventLog = new EventLog(null, meeting, EventLogType.EventThankYouMessage, null);
		eventLogRepository.save(eventLog);

		Set<User> tankYouNotificationSentMembers = new HashSet<>(
				userRepository.findUsersWithNotificationSentForEvent(meeting, MeetingThankYouNotification.class));

		List<User> rsvpWithYesMembers = userRepository.findUsersThatRSVPYesForEvent(meeting);
		for (User destination : rsvpWithYesMembers) {
			if (!tankYouNotificationSentMembers.contains(destination)) {
				Notification notification = new MeetingThankYouNotification(destination, eventLog);
				notificationRepository.save(notification);
			}
		}
	}

	@Override
	@Transactional
	public void sendVoteResults(String uid) {
		Objects.requireNonNull(uid);
		Vote vote = voteRepository.findOneByUid(uid);

		logger.info("Sending vote results for vote", vote);

		ResponseTotalsDTO rsvpTotalsDTO = eventLogManagementService.getVoteResultsForEvent(vote);
		EventWithTotals eventWithTotals = new EventWithTotals(new EventDTO(vote), rsvpTotalsDTO);

		ResponseTotalsDTO totalsDTO = eventWithTotals.getResponseTotalsDTO();
		EventLog eventLog = new EventLog(null, vote, EventLogType.EventResult, null);
		eventLogRepository.save(eventLog);

		Set<User> voteResultsNotificationSentMembers = new HashSet<>(userRepository.findUsersWithNotificationSentForEvent(vote, VoteResultsNotification.class));
		registerNotificationsForEventMembers(vote, destination -> {
			if (!voteResultsNotificationSentMembers.contains(destination)) {
				String message = messageAssemblingService.createVoteResultsMessage(destination, vote,
						totalsDTO.getYes(), totalsDTO.getNo(), totalsDTO.getMaybe(), totalsDTO.getNumberNoRSVP());

				logger.info("sendVoteResultsToUser...send message..." + message + "...to..." + destination.getPhoneNumber());
				return new VoteResultsNotification(destination, eventLog);
			}
			return null;
		});
	}

	@Override
	@Transactional
	public void assignMembers(String userUid, String eventUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		event.assignMembers(assignMemberUids);
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String eventUid, Set<String> memberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		event.removeAssignedMembers(memberUids);
	}
}
