package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class EventBrokerImpl implements EventBroker {
	private final Logger logger = LoggerFactory.getLogger(EventBrokerImpl.class);

	@Autowired
	private EventRepository eventRepository;
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

		return meeting;
	}

	@Override
	public Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime, boolean includeSubGroups,
						   boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		Vote vote = new Vote(name, eventStartDateTime, user, group, includeSubGroups, rsvpRequired, relayable, reminderType,
				customReminderMinutes);

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

		event.setCanceled(true);
	}

	@Override
	@Transactional
	public void updateName(String userUid, String eventUid, String name) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);
		Objects.requireNonNull(name);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		event.setName(name);
	}

	@Override
	@Transactional
	public void updateStartTimestamp(String userUid, String eventUid, Timestamp eventStartDateTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventUid);

		validateEventStartTime(eventStartDateTime);

		User user = userRepository.findOneByUid(userUid);
		Event event = eventRepository.findOneByUid(eventUid);

		event.setEventStartDateTime(eventStartDateTime);
		event.updateScheduledReminderTime();
	}

	@Override
	@Transactional
	public void updateMeetingLocation(String userUid, String meetingUid, String eventLocation) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingUid);

		User user = userRepository.findOneByUid(userUid);
		Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);

		meeting.setEventLocation(eventLocation);
	}

	@Override
	@Transactional
	public void sendScheduledReminders() {
		Instant now = Instant.now();
		List<Event> events = eventRepository.findEventsForReminders(now);
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

		logger.info("Sending manual reminder for event {} with message {}", event, message);

		EventDTO eventDTO = new EventDTO(event);
		eventDTO.setMessage(message);
		jmsTemplateProducerService.sendWithNoReply("manual-reminder", eventDTO);

		event.setNoRemindersSent(event.getNoRemindersSent() + 1);
	}
}
