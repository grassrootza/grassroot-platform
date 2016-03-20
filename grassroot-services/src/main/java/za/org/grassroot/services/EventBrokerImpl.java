package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.EventWithTotals;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

	@Override
	@Transactional
	public Meeting createMeeting(String userUid, String groupUid, String name, Timestamp eventStartDateTime, String eventLocation,
								 boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

		Meeting meeting = new Meeting(name, eventStartDateTime, user, group, eventLocation, includeSubGroups, rsvpRequired,
				relayable, reminderType, customReminderMinutes);

		meetingRepository.save(meeting);

		eventLogManagementService.rsvpForEvent(meeting, meeting.getCreatedByUser(), EventRSVPResponse.YES);

		jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(meeting));
		logger.info("Queued to event-added..." + meeting.getId() + "...version..." + meeting.getVersion());

		return meeting;
	}

	@Override
	@Transactional
	public void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime,
							  String eventLocation, boolean includeSubGroups, boolean rsvpRequired,
							  boolean relayable, EventReminderType reminderType, int customReminderMinutes) {

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

		notifyEventChange(meeting, startTimeChanged);
	}

	@Override
	@Transactional
	public Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime,
						   boolean includeSubGroups, boolean relayable) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		Vote vote = new Vote(name, eventStartDateTime, user, group, includeSubGroups, relayable);
		voteRepository.save(vote);

		jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(vote));
		logger.info("Queued to event-added..." + vote.getId() + "...version..." + vote.getVersion());

		return vote;
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

		jmsTemplateProducerService.sendWithNoReply("event-cancelled", new EventDTO(event));
		logger.info("queued to event-cancelled");
	}

	@Override
	@Transactional
	public void updateName(String userUid, String eventUid, String name) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);
		Objects.requireNonNull(name);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);
		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}

		event.setName(name);
		notifyEventChange(event, false);
	}

	@Override
	@Transactional
	public void updateStartTimestamp(String userUid, String eventUid, Timestamp eventStartDateTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);
		if (event.isCanceled()) {
			throw new IllegalStateException("Event is canceled: " + event);
		}
		event.setEventStartDateTime(eventStartDateTime);
		event.updateScheduledReminderTime();

		notifyEventChange(event, true);
	}

	private void notifyEventChange(Event event, boolean startTimeChanged) {
		jmsTemplateProducerService.sendWithNoReply("event-changed", new EventChanged(new EventDTO(event), startTimeChanged));
		logger.info("Queued to event-changed event..." + event.getId() + "...version..." + event.getVersion());
	}

	@Override
	@Transactional
	public void updateMeetingLocation(String userUid, String meetingUid, String eventLocation) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingUid);

		User user = userRepository.findOneByUid(userUid);
		Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);
		if (meeting.isCanceled()) {
			throw new IllegalStateException("Meeting is canceled: " + meeting);
		}

		meeting.setEventLocation(eventLocation);
		notifyEventChange(meeting, false);
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
}
