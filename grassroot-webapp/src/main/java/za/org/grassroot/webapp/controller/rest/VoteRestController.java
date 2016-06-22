package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

/**
 * Created by aakilomar on 10/24/15.
 */
@RestController
@RequestMapping("/api/vote")
public class VoteRestController {

    private static final Logger log = LoggerFactory.getLogger(VoteRestController.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private EventLogManagementService eventLogManagementService;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private TaskBroker taskBroker;

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

        LocalDateTime eventStartDateTime = LocalDateTime.parse(time.trim(), getPreferredRestFormat());
        Vote vote = eventBroker.createVote(user.getUid(), groupUid, JpaEntityType.GROUP, title, eventStartDateTime,
                                           false, relayable, description, membersUid);
        eventBroker.updateReminderSettings(user.getUid(), vote.getUid(), EventReminderType.CUSTOM,
                                           RestUtil.getReminderMinutes(reminderMinutes));

        TaskDTO voteCreated = taskBroker.load(user.getUid(), vote.getUid(), TaskType.VOTE);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.CREATED, RestMessage.VOTE_CREATED,
                                                                     RestStatus.SUCCESS, Collections.singletonList(voteCreated));

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> viewVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                    @PathVariable("id") String voteUid) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventBroker.load(voteUid);
        EventLog eventLog = eventLogManagementService.getEventLogOfUser(event, user, EventLogType.RSVP);
        boolean hasResponded = eventLogManagementService.userRsvpForEvent(event, user);
        ResponseTotalsDTO totals = eventLogManagementService.getVoteResultsForEvent(event);
        EventWrapper eventWrapper = new EventWrapper(event, eventLog, user, hasResponded, totals);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VOTE_DETAILS, RestStatus.SUCCESS, eventWrapper);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/totals/{phoneNumber}/{code}/{voteUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseTotalsDTO> fetchVoteTotals(@PathVariable String phoneNumber, @PathVariable String code,
                                                             @PathVariable String voteUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Event event = eventBroker.load(voteUid); // todo : permission checking on this user!
        ResponseTotalsDTO totals = eventLogManagementService.getVoteResultsForEvent(event);
        if (totals != null) {
            return new ResponseEntity<>(totals, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
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
            TaskDTO updatedTask = taskBroker.load(user.getUid(), voteUid, TaskType.VOTE);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VOTE_SENT, RestStatus.SUCCESS,
                                                         Collections.singletonList(updatedTask));
        } else if (hasVoted) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_HAS_ALREADY_VOTED, RestStatus.FAILURE);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CLOSED, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/update/{uid}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                      @PathVariable("uid") String voteUid, @RequestParam("title") String title,
                                                      @RequestParam(value = "closingTime") String time,
                                                      @RequestParam(value = "description", required = false) String description) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        ResponseWrapper responseWrapper;
        try {
            LocalDateTime updatedTime = LocalDateTime.parse(time.trim(), getPreferredRestFormat());
            eventBroker.updateVote(user.getUid(), voteUid, updatedTime, description);
            TaskDTO updatedTask = taskBroker.load(user.getUid(), voteUid, TaskType.VOTE);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VOTE_DETAILS_UPDATED, RestStatus.SUCCESS,Collections.singletonList(updatedTask));
        } catch (java.lang.IllegalStateException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CANCELLED, RestStatus.FAILURE);

        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @RequestParam("uid") String voteUid){
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        String userUid = user.getUid();
        Event event = eventBroker.load(voteUid);
        ResponseWrapper responseWrapper;
        if(!event.isCanceled()){
            eventBroker.cancel(userUid,voteUid);
            responseWrapper = new  ResponseWrapperImpl(HttpStatus.OK, RestMessage.VOTE_CANCELLED, RestStatus.SUCCESS);
        }else{
            responseWrapper = new  ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_ALREADY_CANCELLED, RestStatus.FAILURE);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    private boolean isOpen(Event event) { return event.getEventStartDateTime().isAfter(Instant.now()); }

}
