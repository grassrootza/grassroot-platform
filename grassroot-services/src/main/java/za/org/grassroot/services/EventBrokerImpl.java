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
import za.org.grassroot.core.domain.notification.EventChangedNotification;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.async.AsyncEventMessageSender;
import za.org.grassroot.services.async.GenericJmsTemplateProducerService;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	private GenericJmsTemplateProducerService jmsTemplateProducerService;
	@Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private AsyncEventMessageSender asyncEventMessageSender;


	@Autowired
	private CacheUtilService cacheUtilService;
	@Autowired
	private NotificationRepository notificationRepository;

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

		// create notifications
		registerCreateEventNotifications(meeting, eventLog);

		return meeting;
	}

	private void registerCreateEventNotifications(Event event, EventLog eventLog) {
		for (User userToNotify : (Set<User>) event.getAllMembers()) {
			cacheUtilService.clearRsvpCacheForUser(userToNotify, event.getEventType());
			notificationRepository.save(new EventNotification(userToNotify, eventLog, event));
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

		registerEventChangedNotifications(meeting, eventLog, startTimeChanged);
	}

	private void registerEventChangedNotifications(Event event, EventLog eventLog, boolean startTimeChanged) {
		for (User userToNotify : (Set<User>) event.getAllMembers()) {
			boolean memberRepliedWithNo = eventLogManagementService.userRsvpNoForEvent(event.getUid(), userToNotify.getUid());
			// todo: is this needed !?
			boolean changeNotificationRegistered = eventLogManagementService.changeNotificationSentToUser(event.getUid(), userToNotify.getUid());
			if (!changeNotificationRegistered && (!memberRepliedWithNo || startTimeChanged)) {
				notificationRepository.save(new EventChangedNotification(userToNotify, eventLog));
			}
		}
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

		// create notifications
		registerCreateEventNotifications(vote, eventLog);

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

		AfterTxCommitTask afterTxCommitTask = () -> asyncEventMessageSender.sendCancelMeetingNotifications(eventUid);
        applicationEventPublisher.publishEvent(afterTxCommitTask);
	}

	@Override
	@Transactional
	public void sendScheduledReminders() {
		Instant now = Instant.now();
		List<Event> events = eventRepository.findEventsForReminders(Instant.now());
		logger.info("Sending scheduled reminders for {} event(s)", events.size());

		for (Event event : events) {
			try {
				if (event.isCanceled()) {
					throw new IllegalStateException("Event is canceled: " + event);
				}
				if (!event.isScheduledReminderActive()) {
					throw new IllegalStateException("Event is not scheduled for reminder: " + event);
				}

				// todo: figure out how to get and handle errors from here (i.e., so don't set reminders false if an error)
                jmsTemplateProducerService.sendWithNoReply("event-reminder", event.getUid());

				// for meeting calls, send out RSVPs to date
				if (event.getEventType().equals(EventType.MEETING) && event.isRsvpRequired()) {
					jmsTemplateProducerService.sendWithNoReply("meeting-responses", event.getUid());
				}

				event.setNoRemindersSent(event.getNoRemindersSent() + 1);
				event.setScheduledReminderActive(false);

			} catch (Exception e) {
				logger.error("Error while sending scheduled reminder of event " + event + ": " + e.getMessage(), e);
			}
		}

		logger.info("Sending scheduled reminders...done");
	}

	@Override
	@Transactional
	public void sendManualReminder(String userUid, String eventUid, String message) {
		Objects.requireNonNull(eventUid);

		Event event = eventRepository.findOneByUid(eventUid);
		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}

		logger.info("Sending manual reminder for event {} with message {}", event, message);

		EventDTO eventDTO = new EventDTO(event);
		eventDTO.setMessage(message);
		jmsTemplateProducerService.sendWithNoReply("manual-reminder", eventDTO);

		event.setNoRemindersSent(event.getNoRemindersSent() + 1);
	}

	@Override
	@Transactional(readOnly = true)
	public void sendMeetingRSVPsToDate() {

        // since the scheduled job runs once an hour, check for meetings created two days ago, in an hour interval
        Instant start = Instant.now().minus(48, ChronoUnit.HOURS);
		Instant end = start.minus(1, ChronoUnit.HOURS);
        List<Meeting> meetings = meetingRepository.meetingsForResponseTotals(Instant.now(), start, end);

		logger.info("Sending out RSVP totals for {} meetings", meetings.size());
        for (Meeting meeting : meetings) {
            if (meeting.isCanceled()) {
				throw new IllegalStateException("Meeting is cancelled: " + meeting);
			}
			if (!meeting.isRsvpRequired()) {
				throw new IllegalStateException("Meeting does not require RSVPs" + meeting);
			}

			jmsTemplateProducerService.sendWithNoReply("meeting-responses", meeting.getUid());
        }
	}

    @Override
    public void sendMeetingAcknowledgements() {

        /*
         First, get the list of meetings that happened yesterday; if across multiple timezones, probably want to change
         this to be, say, T-12 hours to T-36 hours
        */
        LocalDate yesterday = LocalDate.now().minus(1, ChronoUnit.DAYS);
        Instant start = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MIN), getSAST());
        Instant end = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MAX), getSAST());

        List<Meeting> meetings = meetingRepository.findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(start, end);
        logger.info("Sending out meeting thank you for {} meetings", meetings.size());

        for (Meeting meeting : meetings) {
            if (meeting.isCanceled()) {
                throw new IllegalStateException("Meeting is cancelled: " + meeting);
            }
            if (!meeting.isRsvpRequired()) {
                throw new IllegalStateException("Meeting did not require RSVPs" + meeting);
            }

            jmsTemplateProducerService.sendWithNoReply("meeting-thankyou", meeting.getUid());
        }

    }

    @Override
	@Transactional(readOnly = true)
	public void sendVoteResults() {
		List<Vote> votes = voteRepository.findUnsentVoteResults();
		logger.info("Sending vote results for {} votes...", votes.size());
		for (Vote vote : votes) {
			try {
				logger.info("Sending vote results for vote", vote);
				jmsTemplateProducerService.sendWithNoReply("vote-results", vote.getUid());
			} catch (Exception e) {
				logger.error("Error while sending vote results for vote: " + vote);
			}
		}
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
