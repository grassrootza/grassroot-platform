package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.dto.RSVPTotalsPerGroupDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.util.CacheUtilService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by aakilomar on 8/26/15.
 */
@Component
public class EventLogManager implements EventLogManagementService {

    private Logger log = LoggerFactory.getLogger(EventLogManager.class);

    @Autowired
    EventLogRepository eventLogRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    CacheUtilService cacheUtilService;


    @Override
    public EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message) {
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, eventLogType, message));
        eventLogRepository.flush();
        return eventLog;
    }

    @Override
    public boolean notificationSentToUser(Event event, User user) {
        return eventLogRepository.notificationSent(event, user);
    }

    @Override
    public boolean voteResultSentToUser(Event event, User user) {
        return eventLogRepository.voteResultSent(event, user);
    }

    @Override
    public boolean changeNotificationSentToUser(Event event, User user, String message) {
        boolean messageSent = eventLogRepository.changeNotificationSent(event, user, message);
        log.info("changeNotificationSentToUser...user..." + user.getPhoneNumber() + "...event..." + event.getId() + "...version..." + event.getVersion() + "...message..." + message + "...returning..." + messageSent);
        return messageSent;
    }

    @Override
    public boolean cancelNotificationSentToUser(Event event, User user) {
        return eventLogRepository.cancelNotificationSent(event, user);
    }

    @Override
    public boolean reminderSentToUser(Event event, User user) {
        return eventLogRepository.reminderSent(event, user);
    }

    @Override
    public List<EventLog> getMinutesForEvent(Event event) {
        return eventLogRepository.findByEventLogTypeAndEventOrderByIdAsc(EventLogType.EventMinutes, event);
    }

    @Override
    public EventLog rsvpForEvent(Long eventId, Long userId, String strRsvpResponse) {
        return rsvpForEvent(eventId, userId, EventRSVPResponse.fromString(strRsvpResponse));
    }

    @Override
    public EventLog rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse) {
        return rsvpForEvent(eventRepository.findOne(eventId), userRepository.findOne(userId), rsvpResponse);
    }

    @Override
    public EventLog rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse) {
        return rsvpForEvent(eventRepository.findOne(eventId), userRepository.findByPhoneNumber(phoneNumber), rsvpResponse);
    }

    @Override
    public EventLog rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse) {
        log.trace("rsvpForEvent...event..." + event.getId() + "...user..." + user.getPhoneNumber() + "...rsvp..." + rsvpResponse.toString());
        EventLog eventLog = new EventLog();
        // dont allow the user to rsvp/vote twice

        if (!userRsvpForEvent(event,user)) {
            eventLog = createEventLog(EventLogType.EventRSVP, event, user, rsvpResponse.toString());
            // clear rsvp cache for user
            cacheUtilService.clearRsvpCacheForUser(user, event.getEventType());

            if (event.getEventType() == EventType.Vote) {
                // see if everyone voted, if they did expire the vote so that the results are sent out
                RSVPTotalsDTO rsvpTotalsDTO = getVoteResultsForEvent(event);
                log.trace("rsvpForEvent...after..." + rsvpTotalsDTO.toString());
                if (rsvpTotalsDTO.getNumberNoRSVP() < 1) {
                    Date now = new Date();
                    event.setEventStartDateTime(new Timestamp(now.getTime()));
                    eventRepository.save(event);
                }
            }
        } else {
            // put values in the fields so that rest method does not NPE
            eventLog.setId(0L);
            eventLog.setEvent(new Event());
            eventLog.setUser(User.makeEmpty());

        }

        return eventLog;
    }

    @Override
    public EventLog getEventLogOfUser(Event event, User user, EventLogType eventLogType) {
        return eventLogRepository.findByEventAndUserAndEventLogType(event, user,eventLogType);
    }

    @Override
    public boolean userRsvpNoForEvent(Event event, User user) {
        boolean rsvpNoForEvent = eventLogRepository.rsvpNoForEvent(event, user);
        log.info("userRsvpNoForEvent...returning..." + rsvpNoForEvent + " for event..." + event.getId() + "...user..." + user.getPhoneNumber());
        return rsvpNoForEvent;
    }

    @Override
    public boolean userRsvpForEvent(Event event, User user) {
        return eventLogRepository.userRsvpForEvent(event, user);
    }

    @Override
    public RSVPTotalsDTO getRSVPTotalsForEvent(Long eventId) {
        return getRSVPTotalsForEvent(eventRepository.findOne(eventId));
    }

    @Override
    public RSVPTotalsDTO getRSVPTotalsForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            return new RSVPTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), event.getAppliesToGroup().getId(), event.getCreatedByUser().getId()));
        }
        RSVPTotalsDTO totals = new RSVPTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAppliesToGroup().getId())) {
            totals.add(new RSVPTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), event.getAppliesToGroup().getId(), event.getCreatedByUser().getId())));

        }
        log.info("getRSVPTotalsForEvent...returning..." + totals.toString());
        return totals;
    }

    @Override
    public RSVPTotalsDTO getVoteResultsForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            return new RSVPTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), event.getAppliesToGroup().getId()));
        }
        RSVPTotalsDTO totals = new RSVPTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAppliesToGroup().getId())) {
            totals.add(new RSVPTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), group.getId())));
        }
        log.info("getVoteResultsForEvent...returning..." + totals.toString());
        return totals;
    }

    @Override
    public List<RSVPTotalsPerGroupDTO> getVoteTotalsPerGroup(Long startingGroup, Long event) {
        List<RSVPTotalsPerGroupDTO> list = new ArrayList<>();
        for (Object[] objArray : eventLogRepository.voteTotalsPerGroupAndSubGroup(startingGroup,event)) {
            list.add(new RSVPTotalsPerGroupDTO(objArray));
        }

        return list;
    }

    @Override
    public List<EventLog> getNonRSVPEventLogsForEvent(Event event) {
        return eventLogRepository.findByEventAndEventLogTypeNot(event, EventLogType.EventRSVP);
    }

    @Override
    public int countNonRSVPEventLogsForEvent(Event event) {
        // todo: might want to make this a count query
        return getNonRSVPEventLogsForEvent(event).size();
    }

//    private void recursiveTotalsAdd(Event event, Group parentGroup, RSVPTotalsDTO rsvpTotalsDTO ) {
//
//        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
//            recursiveTotalsAdd(event, childGroup, rsvpTotalsDTO);
//        }
//
//        // add all the totals at this level
//        rsvpTotalsDTO.add(new RSVPTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), parentGroup.getId(),event.getCreatedByUser().getId())));
//
//    }


//    private void recursiveVotesAdd(Event event, Group parentGroup, RSVPTotalsDTO rsvpTotalsDTO ) {
//
//        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
//            recursiveVotesAdd(event, childGroup, rsvpTotalsDTO);
//        }
//
//        // add all the totals at this level
//        rsvpTotalsDTO.add(new RSVPTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), parentGroup.getId())));
//
//    }
}
