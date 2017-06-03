package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.notification.EventResponseNotification;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class EventLogBrokerImpl implements EventLogBroker {

    private Logger log = LoggerFactory.getLogger(EventLogBrokerImpl.class);

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Override
    @Transactional
    public EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, EventRSVPResponse message) {
        Event event = eventRepository.findOneByUid(eventUid);
        log.info("Creating event log, with event={}", event);
        User user = userRepository.findOneByUid(userUid);
        EventLog eventLog = new EventLog(user, event, eventLogType, message);
        return eventLogRepository.save(eventLog);
    }

    @Override
    @Transactional
    public void rsvpForEvent(String eventUid, String userUid, EventRSVPResponse rsvpResponse) {

        Objects.requireNonNull(eventUid);
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(rsvpResponse);

        User user = userRepository.findOneByUid(userUid);
        Event event = eventRepository.findOneByUid(eventUid);

        log.trace("rsvpForEvent...event..." + event.getId() + "...user..." + user.getPhoneNumber() + "...rsvp..." + rsvpResponse.toString());

        if (!hasUserRespondedToEvent(event, user)) {
            EventLog eventLog = createEventLog(EventLogType.RSVP, event.getUid(), user.getUid(), rsvpResponse);
            // clear rsvp cache for user
            cacheUtilService.clearRsvpCacheForUser(user, event.getEventType());

            // see if everyone voted, if they did expire the vote so that the results are sent out
            if (event.getEventType().equals(EventType.VOTE)) {
                checkIfAllUsersVoted(event);
            } else if (event.getEventType().equals(EventType.MEETING) && !user.equals(event.getCreatedByUser())) {
                generateMeetingResponseMessage(event, eventLog, rsvpResponse);
            }
        } else if (event.getEventStartDateTime().isAfter(Instant.now())) {
            // allow the user to change their rsvp / vote as long as meeting is open
            EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
            eventLog.setResponse(rsvpResponse);
            log.info("rsvpForEvent... changing response to {} on eventLog {}", rsvpResponse.toString(), eventLog);
            if (event.getEventType().equals(EventType.MEETING) && !user.equals(event.getCreatedByUser())) {
                generateMeetingResponseMessage(event, eventLog, rsvpResponse);
            }
        }
    }

    private void checkIfAllUsersVoted(Event event) {
        ResponseTotalsDTO rsvpTotalsDTO = getResponseCountForEvent(event);
        log.info("rsvpForEvent... response total DTO for vote : " + rsvpTotalsDTO.toString());
        if (rsvpTotalsDTO.getNumberNoRSVP() < 1) {
            log.info("rsvpForEvent...everyone has voted... sending out results for {}", event.getName());
            event.setEventStartDateTime(Instant.now());
        }
    }

    @Override
    public boolean hasUserRespondedToEvent(Event event, User user) {
        log.info("Checking is user has responded to: {}", event.getName());
        long count = eventLogRepository.count(Specifications
                .where(EventLogSpecifications.forEvent(event))
                .and(EventLogSpecifications.forUser(user))
                .and(EventLogSpecifications.isResponseToAnEvent()));
        log.info("Count of responses: {}", count);
        return count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseTotalsDTO getResponseCountForEvent(Event event) {
        List<EventLog> responseEventLogs = eventLogRepository.findByEventAndEventLogType(event, EventLogType.RSVP);
        return new ResponseTotalsDTO(responseEventLogs, event);
    }

    private void generateMeetingResponseMessage(Event event, EventLog eventLog, EventRSVPResponse rsvpResponse) {
        if (event.getCreatedByUser().getMessagingPreference().equals(UserMessagingPreference.ANDROID_APP)) {
            String message = messageAssemblingService.createEventResponseMessage(event.getCreatedByUser(), event, rsvpResponse);
            Notification notification = new EventResponseNotification(event.getCreatedByUser(), message, eventLog);
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addNotification(notification);
            logsAndNotificationsBroker.storeBundle(bundle);
        }
    }

}
