package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.RestUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Created by aakilomar on 10/24/15.
 */
@RestController
@RequestMapping("/api/vote")
public class VoteRestController {

    private static final Logger log = LoggerFactory.getLogger(VoteRestController.class);

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    EventBroker eventBroker;


    @RequestMapping(value = "/create/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createVote(@PathVariable("phoneNumber") String phoneNumber,
                                                      @PathVariable("code") String code,
                                                      @PathVariable("id") String groupUid,
                                                      @RequestParam("title") String title,
                                                      @RequestParam(value = "closingTime") String time,
                                                      @RequestParam(value = "description", required = false) String description,
                                                      @RequestParam("reminderMins") int reminderMinutes,
                                                      @RequestParam(value = "notifyGroup", required = false) boolean relayable,
                                                      @RequestParam(value = "members", required = false) List<String> members) {

        log.info("ZOG: Creating vote with parameters ... phoneNumber: {}, groupUid: {}, title: {}, closingTime: {}," +
                         "description: {}, ",
                 phoneNumber, groupUid, title, time);

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Set<String> membersUid = Sets.newHashSet();
        if (members != null) {
            membersUid.addAll(members);
        }

        Instant eventStartDateTime = Instant.parse(time);
        Vote vote = eventBroker.createVote(user.getUid(), groupUid, JpaEntityType.GROUP, title, Timestamp.from(eventStartDateTime),
                                           false, relayable, description, membersUid);
        eventBroker.updateReminderSettings(user.getUid(), vote.getUid(), EventReminderType.CUSTOM,
                                           RestUtil.getReminderMinutes(reminderMinutes));

        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.VOTE_CREATED, RestStatus.SUCCESS);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> viewVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                    @PathVariable("id") String voteUid) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventBroker.load(voteUid);
        EventLog eventLog = eventLogManagementService.getEventLogOfUser(event, user, EventLogType.EventRSVP);
        boolean hasResponded = eventLogManagementService.userRsvpForEvent(event, user);
        RSVPTotalsDTO totals = eventLogManagementService.getVoteResultsForEvent(event);
        EventWrapper eventWrapper = new EventWrapper(event, eventLog, user, hasResponded, totals);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VOTE_DETAILS, RestStatus.SUCCESS, eventWrapper);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));


    }

    @RequestMapping(value = "/do/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> castVote(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("code") String code, @PathVariable("id") String voteUid,
                                                    @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventBroker.load(voteUid);
        String trimmedResponse = response.toLowerCase().trim();
        boolean hasVoted = eventLogManagementService.userRsvpForEvent(event, user);
        ResponseWrapper responseWrapper;
        if (event.getEventType().equals(EventType.VOTE) && (!hasVoted && isOpen(event))) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VOTE_SENT, RestStatus.SUCCESS);
        } else if (hasVoted) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_HAS_ALREADY_VOTED, RestStatus.FAILURE);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CLOSED, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/update/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                      @PathVariable("id") String voteUid, @RequestParam("title") String title,
                                                      @RequestParam(value = "closingTime") String time,
                                                      @RequestParam(value = "description", required = false) String description) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        ResponseWrapper responseWrapper;
        try {
            eventBroker.updateVote(user.getUid(), voteUid, Timestamp.from(Instant.parse(time)), description);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VOTE_DETAILS_UPDATED, RestStatus.SUCCESS);
        } catch (java.lang.IllegalStateException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CANCELLED, RestStatus.FAILURE);

        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));


    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
    }


}
