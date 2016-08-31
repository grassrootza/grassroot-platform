package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.EventResponseNotification;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
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
    private GroupRepository groupRepository;

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

        log.trace("rsvpForEvent...event..." + event.getId() + "...user..." + user.getPhoneNumber() +
                "...rsvp..." + rsvpResponse.toString());

        if (!hasUserRespondedToEvent(event, user)) {
            EventLog eventLog = createEventLog(EventLogType.RSVP, event.getUid(), user.getUid(), rsvpResponse);
            // clear rsvp cache for user
            cacheUtilService.clearRsvpCacheForUser(user, event.getEventType());

            // see if everyone voted, if they did expire the vote so that the results are sent out

            if (event.getEventType().equals(EventType.VOTE)) {
                ResponseTotalsDTO rsvpTotalsDTO = getVoteResultsForEvent(event);
                log.info("rsvpForEvent... response total DTO for vote : " + rsvpTotalsDTO.toString());
                if (rsvpTotalsDTO.getNumberNoRSVP() < 1) {
                    log.info("rsvpForEvent...everyone has voted... sending out results for {}", event.getName());
                    event.setEventStartDateTime(Instant.now());
                }
            } else {
                if (!user.equals(event.getCreatedByUser())) {
                    generateEventResponseMessage(event, eventLog, rsvpResponse);
                }
            }
        } else if (event.getEventStartDateTime().isAfter(Instant.now())) {
            // allow the user to change their rsvp / vote as long as meeting is open
            EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
            eventLog.setResponse(rsvpResponse);
            log.info("rsvpForEvent... changing response to {} on eventLog {}", rsvpResponse.toString(), eventLog);
            eventLogRepository.saveAndFlush(eventLog); // todo: shouldn't need this, but it's not persisting (cleaning needed)
            if (!user.equals(event.getCreatedByUser())) {
                generateEventResponseMessage(event, eventLog, rsvpResponse);
            }
        }

    }

    @Override
    public boolean hasUserRespondedToEvent(Event event, User user) {
        EventLog rsvpEventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
        return rsvpEventLog != null;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseTotalsDTO getResponseCountForEvent(Event event) {
        ResponseTotalsDTO totals;
        if (!event.isIncludeSubGroups()) {
            List<Object[]> groupTotals = eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), event.getAncestorGroup().getId());
            totals = new ResponseTotalsDTO(groupTotals, event.getAllMembers().size());
        } else {
            totals = new ResponseTotalsDTO();
            for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAncestorGroup().getId())) {
                List<Object[]> groupTotals = eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), group.getId());
                totals.add(new ResponseTotalsDTO(groupTotals, group.getMemberships().size()));
            }
        }
        log.info("getRSVPTotalsForEvent...returning..." + totals.toString());
        return totals;
    }

    @Override
    public ResponseTotalsDTO getVoteResultsForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            List<Object[]> groupVotes = eventLogRepository.voteTotalsForEventAndGroup(event.getId(), event.getAncestorGroup().getId());
            return new ResponseTotalsDTO(groupVotes, event.getAllMembers().size());
        }
        ResponseTotalsDTO totals = new ResponseTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAncestorGroup().getId())) {
            ResponseTotalsDTO groupVotes = new ResponseTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), group.getId()),
                    event.getAllMembers().size());
            totals.add(groupVotes);
        }
        log.info("getVoteResultsForEvent...returning..." + totals.toString());
        return totals;
    }

    private void generateEventResponseMessage(Event event, EventLog eventLog, EventRSVPResponse rsvpResponse) {
        if (event.getCreatedByUser().getMessagingPreference().equals(UserMessagingPreference.ANDROID_APP)) {
            String message = messageAssemblingService.createEventResponseMessage(event.getCreatedByUser(), event, rsvpResponse);
            Notification notification = new EventResponseNotification(event.getCreatedByUser(), message, eventLog);
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addNotification(notification);
            logsAndNotificationsBroker.storeBundle(bundle);
        }
    }

//    private void recursiveTotalsAdd(Event event, Group parentGroup, ResponseTotalsDTO rsvpTotalsDTO ) {
//
//        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
//            recursiveTotalsAdd(event, childGroup, rsvpTotalsDTO);
//        }
//
//        // add all the totals at this level
//        rsvpTotalsDTO.add(new ResponseTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), parentGroup.getId(),event.getCreatedByUser().getId())));
//
//    }


//    private void recursiveVotesAdd(Event event, Group parentGroup, ResponseTotalsDTO rsvpTotalsDTO ) {
//
//        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
//            recursiveVotesAdd(event, childGroup, rsvpTotalsDTO);
//        }
//
//        // add all the totals at this level
//        rsvpTotalsDTO.add(new ResponseTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), parentGroup.getId())));
//
//    }
}
