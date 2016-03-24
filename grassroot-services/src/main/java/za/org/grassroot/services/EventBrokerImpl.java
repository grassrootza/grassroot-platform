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
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.EventWithTotals;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	private GroupRepository groupRepository;
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
			findByCreatedByUserAndEventStartDateTimeGreaterThanAndEventTypeAndCanceledFalse(user, Timestamp.valueOf(LocalDateTime.now()), EventType.MEETING,
																							new PageRequest(pageNumber, pageSize));
		return pageOfEvents.getContent();
	}

	@Override
	@Transactional
	public Meeting createMeeting(String userUid, String groupUid, String name, Timestamp eventStartDateTime, String eventLocation,
								 boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
								 int customReminderMinutes, String description, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(assignMemberUids);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		Meeting meeting = new Meeting(name, eventStartDateTime, user, group, eventLocation, includeSubGroups, rsvpRequired,
				relayable, reminderType, customReminderMinutes, description);
		meeting.assignMembers(assignMemberUids);

		meetingRepository.save(meeting);

		eventLogManagementService.rsvpForEvent(meeting, meeting.getCreatedByUser(), EventRSVPResponse.YES);

        AfterTxCommitTask afterTxCommitTask = () -> asyncEventMessageSender.sendNewMeetingNotifications(meeting.getUid());
        applicationEventPublisher.publishEvent(afterTxCommitTask);

		return meeting;
	}

	@Override
    @Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime, String eventLocation) {
		Objects.requireNonNull(userUid);
        Objects.requireNonNull(meetingUid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(eventStartDateTime);
        Objects.requireNonNull(eventLocation);

        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

        if (meeting.isCanceled()) {
            throw new IllegalStateException("Meeting is canceled: " + meeting);
        }

        boolean startTimeChanged = !eventStartDateTime.equals(meeting.getEventStartDateTime());
        if (startTimeChanged) {
            validateEventStartTime(eventStartDateTime);
            meeting.setEventStartDateTime(eventStartDateTime);
        }

        meeting.setName(name);
        meeting.setEventLocation(eventLocation);

        sendChangeNotifications(meeting.getUid(), EventType.MEETING, startTimeChanged);
    }

	@Override
	@Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime,
							  String eventLocation, boolean includeSubGroups, boolean rsvpRequired,
							  boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingUid);
		Objects.requireNonNull(name);
		Objects.requireNonNull(eventStartDateTime);
		Objects.requireNonNull(eventLocation);
		Objects.requireNonNull(reminderType);

		Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is canceled: " + meeting);
		}
		validateEventStartTime(eventStartDateTime);
		boolean startTimeChanged = !eventStartDateTime.equals(meeting.getEventStartDateTime());

		meeting.setName(name);
		meeting.setEventStartDateTime(eventStartDateTime);
		meeting.setEventLocation(eventLocation);
		meeting.setIncludeSubGroups(includeSubGroups);
		meeting.setRsvpRequired(rsvpRequired);
		meeting.setRelayable(relayable);
		meeting.setReminderType(reminderType);
		meeting.setCustomReminderMinutes(customReminderMinutes);

		meeting.updateScheduledReminderTime();

		sendChangeNotifications(meeting.getUid(), EventType.MEETING, startTimeChanged);
	}

	@Override
	@Transactional
	public Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime,
						   boolean includeSubGroups, boolean relayable, String description, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		Vote vote = new Vote(name, eventStartDateTime, user, group, includeSubGroups, relayable, description);
		vote.assignMembers(assignMemberUids);

		voteRepository.save(vote);

		jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(vote));
		logger.info("Queued to event-added..." + vote.getId() + "...version..." + vote.getVersion());

		return vote;
	}

	@Override
	public Vote updateVote(String userUid, String voteUid, Timestamp eventStartDateTime, String description) {
		return null;
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
    }

    private void validateEventStartTime(Timestamp eventStartDateTime) {
		Instant now = Instant.now();
		if (!eventStartDateTime.toInstant().isAfter(now)) {
			throw new EventStartTimeNotInFutureException("Event start time " + eventStartDateTime + " is not in the future");
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

		AfterTxCommitTask afterTxCommitTask = () -> asyncEventMessageSender.sendCancelMeetingNotifications(eventUid);
        applicationEventPublisher.publishEvent(afterTxCommitTask);
	}

	private void sendChangeNotifications(String eventUid, EventType eventType, boolean startTimeChanged) {
		// todo: replace with just passing the UID around
        AfterTxCommitTask afterTxCommitTask = () -> asyncEventMessageSender.sendChangedEventNotification(eventUid, eventType, startTimeChanged);
        applicationEventPublisher.publishEvent(afterTxCommitTask);
	}

	@Override
	@Transactional
	public void sendScheduledReminders() {
		Instant now = Instant.now();
		List<Event> events = eventRepository.findEventsForReminders(Date.from(now));
		logger.info("Sending scheduled reminders for {} event(s)", events.size());

		for (Event event : events) {
			try {
				if (event.isCanceled()) {
					throw new IllegalStateException("Event is canceled: " + event);
				}
				if (!event.isScheduledReminderActive()) {
					throw new IllegalStateException("Event is not scheduled for reminder: " + event);
				}

				jmsTemplateProducerService.sendWithNoReply("event-reminder", new EventDTO(event));

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
	@Transactional
	public void sendVoteResults() {
		List<Vote> votes = voteRepository.findUnsentVoteResults();
		logger.info("Sending vote results for {} votes...", votes.size());
		for (Vote vote : votes) {
			try {
				logger.info("Sending vote results for vote", vote);
				// get the totals
				RSVPTotalsDTO rsvpTotalsDTO = eventLogManagementService.getVoteResultsForEvent(vote);

				// queue vote results request
				jmsTemplateProducerService.sendWithNoReply("vote-results", new EventWithTotals(new EventDTO(vote), rsvpTotalsDTO));
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
