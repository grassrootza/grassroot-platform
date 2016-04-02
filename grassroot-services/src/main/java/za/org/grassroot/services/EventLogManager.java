package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.RSVPTotalsPerGroupDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
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
    VoteRepository voteRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    CacheUtilService cacheUtilService;


    /* @Override
    public EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message) {
        EventLog eventLog = eventLogRepository.save(new EventLog(user, event, eventLogType, message));
        eventLogRepository.flush();
        return eventLog;
    }*/

    @Override
    @Transactional
    public EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, String message) {
        Event event = eventRepository.findOneByUid(eventUid);
        User user = userRepository.findOneByUid(userUid);
        EventLog eventLog = new EventLog(user, event, eventLogType, message, null);
        return eventLogRepository.save(eventLog);
    }

    @Override
    public boolean notificationSentToUser(Event event, User user) {
        return eventLogRepository.notificationSent(event, user);
    }

    @Override
    public boolean voteResultSentToUser(String voteUid, String userUid) {
        Vote vote = voteRepository.findOneByUid(voteUid);
        User user = userRepository.findOneByUid(userUid);
        return eventLogRepository.voteResultSent(vote, user);
    }

    @Override
    public boolean changeNotificationSentToUser(String eventUid, String userUid, String message) {
        Event event = eventRepository.findOneByUid(eventUid);
        User user = userRepository.findOneByUid(userUid);
        boolean messageSent = eventLogRepository.changeNotificationSent(event, user, message);
        log.info("changeNotificationSentToUser...user..." + user.getPhoneNumber() + "...event..." + event.getId() + "...version..." + event.getVersion() + "...message..." + message + "...returning..." + messageSent);
        return messageSent;
    }

    @Override
    public boolean cancelNotificationSentToUser(String eventUid, String userUid) {
        Event event = eventRepository.findOneByUid(eventUid);
        User user = userRepository.findOneByUid(userUid);
        return eventLogRepository.cancelNotificationSent(event, user);
    }

    @Override
    public boolean reminderSentToUser(Event event, User user) {
        return eventLogRepository.reminderSent(event, user);
    }

    @Override
    public void rsvpForEvent(Long eventId, Long userId, String strRsvpResponse) {
        rsvpForEvent(eventId, userId, EventRSVPResponse.fromString(strRsvpResponse));
    }

    @Override
    public void rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse) {
        rsvpForEvent(eventRepository.findOne(eventId), userRepository.findOne(userId), rsvpResponse);
    }

    @Override
    public void rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse) {
        rsvpForEvent(eventRepository.findOne(eventId), userRepository.findByPhoneNumber(phoneNumber), rsvpResponse);
    }

    @Override
    public void rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse) {
        log.trace("rsvpForEvent...event..." + event.getId() + "...user..." + user.getPhoneNumber() + "...rsvp..." + rsvpResponse.toString());
        // dont allow the user to rsvp/vote twice
        if (!userRsvpForEvent(event,user)) {
            createEventLog(EventLogType.EventRSVP, event.getUid(), user.getUid(), rsvpResponse.toString());
            // clear rsvp cache for user
            cacheUtilService.clearRsvpCacheForUser(user, event.getEventType());

            if (event.getEventType() == EventType.VOTE) {
                // see if everyone voted, if they did expire the vote so that the results are sent out
                ResponseTotalsDTO rsvpTotalsDTO = getVoteResultsForEvent(event);
                log.trace("rsvpForEvent...after..." + rsvpTotalsDTO.toString());
                if (rsvpTotalsDTO.getNumberNoRSVP() < 1) {
                    Date now = new Date();
                    event.setEventStartDateTime(new Timestamp(now.getTime()));
                    eventRepository.save(event);
                }
            }
//        } else {
//            EventLog eventLog = new EventLog();
//             put values in the fields so that rest method does not NPE
//            eventLog.setId(0L);
//            eventLog.setEvent(new Event());
//            eventLog.setUser(User.makeEmpty());
        }
    }

    @Override
    public EventLog getEventLogOfUser(Event event, User user, EventLogType eventLogType) {
        return eventLogRepository.findByEventAndUserAndEventLogType(event, user,eventLogType);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userRsvpNoForEvent(String eventUid, String userUid) {
        Event event = eventRepository.findOneByUid(eventUid);
        User user = userRepository.findOneByUid(userUid);
        boolean rsvpNoForEvent = eventLogRepository.rsvpNoForEvent(event, user);
        log.info("userRsvpNoForEvent...returning..." + rsvpNoForEvent + " for event..." + event.getId() + "...user..." + user.getPhoneNumber());
        return rsvpNoForEvent;
    }

    @Override
    public boolean userRsvpForEvent(Event event, User user) {
        return eventLogRepository.userRsvpForEvent(event, user);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseTotalsDTO getResponseCountForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            log.info("Assembling count with eventId: {}, groupId: {}", event.getId(), event.resolveGroup().getId());
            return new ResponseTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), event.resolveGroup().getId()));
        }
        ResponseTotalsDTO totals = new ResponseTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.resolveGroup().getId())) {
            totals.add(new ResponseTotalsDTO(eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), group.getId())));
        }
        log.info("getRSVPTotalsForEvent...returning..." + totals.toString());
        return totals;
    }

    @Override
    public ResponseTotalsDTO getVoteResultsForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            final ResponseTotalsDTO rsvpTotalsDTO = new ResponseTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), event.resolveGroup().getId()));
            return rsvpTotalsDTO;
        }
        ResponseTotalsDTO totals = new ResponseTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.resolveGroup().getId())) {
            totals.add(new ResponseTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), group.getId())));
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
