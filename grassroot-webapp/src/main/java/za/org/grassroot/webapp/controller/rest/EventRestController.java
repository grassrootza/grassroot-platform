package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.EventDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by F5203783 on 2015/09/03.
 */
@RestController
@RequestMapping("/api/event")
public class EventRestController {

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;


    @RequestMapping(value = "/vote/do/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> castVote(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                    @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(eventId));
        String trimmedResponse = response.toLowerCase().trim();
        boolean hasVoted = eventLogManagementService.userRsvpForEvent(event, user);
        ResponseWrapper responseWrapper;
        if (event.getEventType().equals(EventType.Vote) && (!hasVoted && isOpen(event))) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VOTE_SENT, RestStatus.SUCCESS);
        } else if (hasVoted) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_HAS_ALREADY_VOTED, RestStatus.FAILURE);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CLOSED, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/meeting/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                    @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(eventId));
        String trimmedResponse = response.toLowerCase().trim();
        ResponseWrapper responseWrapper;
        if (event.getEventType().equals(EventType.Meeting) && isOpen(event)) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.RSVP_SENT, RestStatus.SUCCESS);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE, RestStatus.FAILURE);
         }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
    }


}
