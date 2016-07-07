package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
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

    @Override
    @Transactional
    public EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, String message) {
        Event event = eventRepository.findOneByUid(eventUid);
        log.info("Creating event log, with event={}", event);
        User user = userRepository.findOneByUid(userUid);
        EventLog eventLog = new EventLog(user, event, eventLogType, message);
        return eventLogRepository.save(eventLog);
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
    @Transactional
    public void rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse) {
        log.trace("rsvpForEvent...event..." + event.getId() + "...user..." + user.getPhoneNumber() +
                          "...rsvp..." + rsvpResponse.toString());

        if (!userRsvpForEvent(event,user)) {
            createEventLog(EventLogType.RSVP, event.getUid(), user.getUid(), rsvpResponse.toString());
            // clear rsvp cache for user
            cacheUtilService.clearRsvpCacheForUser(user, event.getEventType());

            // see if everyone voted, if they did expire the vote so that the results are sent out
            // todo: consider adding a "prior closing date time" or some other way to trigger this

            if (event.getEventType() == EventType.VOTE) {
                ResponseTotalsDTO rsvpTotalsDTO = getVoteResultsForEvent(event);
                log.info("rsvpForEvent... response total DTO for vote : " + rsvpTotalsDTO.toString());
                if (rsvpTotalsDTO.getNumberNoRSVP() < 1) {
                    log.info("rsvpForEvent...everyone has voted... sending out results for {}", event.getName());
                    event.setEventStartDateTime(Instant.now());
                }
            }
        } else if (event.getEventStartDateTime().isAfter(Instant.now())) {
            // allow the user to change their rsvp / vote as long as meeting is open
            EventLog eventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
            eventLog.setMessage(rsvpResponse.toString());
            log.info("rsvpForEvent... changing response to {} on eventLog {}", rsvpResponse.toString(), eventLog);
            eventLogRepository.saveAndFlush(eventLog); // todo: shouldn't need this, but it's not persisting (cleaning needed)
        }
    }

    @Override
    public boolean userRsvpForEvent(Event event, User user) {
        EventLog rsvpEventLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
        return rsvpEventLog != null;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseTotalsDTO getResponseCountForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            log.info("Assembling count with eventId: {}, groupId: {}", event.getId(), event.getAncestorGroup().getId());
            List<Object[]> groupTotals = eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), event.getAncestorGroup().getId());
            return new ResponseTotalsDTO(groupTotals);
        }
        ResponseTotalsDTO totals = new ResponseTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAncestorGroup().getId())) {
            List<Object[]> groupTotals = eventLogRepository.rsvpTotalsForEventAndGroup(event.getId(), group.getId());
            totals.add(new ResponseTotalsDTO(groupTotals));
        }
        log.info("getRSVPTotalsForEvent...returning..." + totals.toString());
        return totals;
    }

    @Override
    public ResponseTotalsDTO getVoteResultsForEvent(Event event) {
        if (!event.isIncludeSubGroups()) {
            List<Object[]> groupVotes = eventLogRepository.voteTotalsForEventAndGroup(event.getId(), event.getAncestorGroup().getId());
            return new ResponseTotalsDTO(groupVotes);
        }
        ResponseTotalsDTO totals = new ResponseTotalsDTO();
        for (Group group : groupRepository.findGroupAndSubGroupsById(event.getAncestorGroup().getId())) {
            ResponseTotalsDTO groupVotes = new ResponseTotalsDTO(eventLogRepository.voteTotalsForEventAndGroup(event.getId(), group.getId()));
            totals.add(groupVotes);
        }
        log.info("getVoteResultsForEvent...returning..." + totals.toString());
        return totals;
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
