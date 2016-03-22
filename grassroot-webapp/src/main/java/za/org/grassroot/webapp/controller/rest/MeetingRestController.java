package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.EventDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Created by paballo on 2016/03/21.
 */
@RestController
@RequestMapping("/api/meeting")
public class MeetingRestController {


    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;




    @RequestMapping(value = "/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(eventId));
        String trimmedResponse = response.toLowerCase().trim();
        ResponseWrapper responseWrapper;
        if(event.isCanceled()){
            responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND,RestMessage.MEETING_CANCELLED,RestStatus.FAILURE);
        }else if (event.getEventType().equals(EventType.Meeting) && isOpen(event)) {
            eventLogManagementService.rsvpForEvent(event, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.RSVP_SENT, RestStatus.SUCCESS);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    @RequestMapping(value="/view/{id}/{phoneNumber}/{code}", method =RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @PathVariable("id") String id){
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Event event = eventManagementService.loadEvent(Long.parseLong(id));
        EventLog eventLog = eventLogManagementService.getEventLogOfUser(event, user, EventLogType.EventRSVP);
        boolean hasResponded = eventLogManagementService.userRsvpForEvent(event, user);
        Map<String,Integer> totals = eventManagementService.getMeetingRsvpTotals(event);
        EventDTO eventDTO =new EventDTO(event,eventLog,user,hasResponded,totals);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.OK,RestMessage.MEETING_DETAILS,RestStatus.SUCCESS,
                eventDTO);

        return new ResponseEntity<>(responseWrapper,HttpStatus.valueOf(responseWrapper.getCode()));
    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
    }



}
