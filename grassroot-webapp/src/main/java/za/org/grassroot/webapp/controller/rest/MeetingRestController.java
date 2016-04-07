
package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.IllegalStateException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

/**
 * Created by paballo on 2016/03/21.
 */

@RestController
@RequestMapping("/api/meeting")
public class MeetingRestController {

    private static final Logger log = LoggerFactory.getLogger(MeetingRestController.class);

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    EventBroker eventBroker;


    @RequestMapping(value = "/create/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createMeeting(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                         @PathVariable("id") String groupUid, @RequestParam("title") String title, @RequestParam("description") String description,
                                                         @RequestParam("startTime") String time, @RequestParam("notifyGroup") boolean relayable, @RequestParam("reminderMins") int reminderMinutes,
                                                         @RequestParam("location") String location, @RequestParam("includeSubGroups") boolean includeSubGroups, @RequestParam("rsvpRequired") boolean rsvp,
                                                         @RequestParam(value="members", required = false) List<String> members) {

        log.info("REST : received meeting create request... with time string: {}", time);

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Set<String> membersUid = Sets.newHashSet();
        if(members != null){
            membersUid.addAll(members);
        }

        LocalDateTime meetingDateTime = LocalDateTime.parse(time, getPreferredRestFormat());
        eventBroker.createMeeting(user.getUid(), groupUid, JpaEntityType.GROUP, title, meetingDateTime, location,
                                  includeSubGroups, rsvp, relayable, EventReminderType.CUSTOM, reminderMinutes, description, membersUid);
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.MEETING_CREATED, RestStatus.SUCCESS);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/update/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateMeeting(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                         @PathVariable("id") String meetingUid, @RequestParam("title") String title, @RequestParam("description") String description,
                                                         @RequestParam("startTime") String time, @RequestParam("notifyGroup") boolean relayable, @RequestParam("reminderMins") int reminderMinutes,
                                                         @RequestParam("location") String location, @RequestParam("includeSubGroups") boolean includeSubGroups, @RequestParam("rsvpRequired") boolean rsvp) {

        log.info("Received update meeting request, with string: " + time);

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        ResponseWrapper responseWrapper;

        try {
            LocalDateTime dateTime = LocalDateTime.parse(time, getPreferredRestFormat());
            eventBroker.updateMeeting(user.getUid(), meetingUid, title, dateTime, location, includeSubGroups, rsvp, relayable,
                    EventReminderType.CUSTOM, reminderMinutes, description);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.MEETING_DETAILS_UPDATED, RestStatus.SUCCESS);
        } catch (IllegalStateException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.MEETING_CANCELLED, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                @PathVariable("code") String code, @PathVariable("id") String meetingUid,
                                                @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        String trimmedResponse = response.toLowerCase().trim();
        ResponseWrapper responseWrapper;
        if (meeting.isCanceled()) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.MEETING_CANCELLED, RestStatus.FAILURE);
        } else if (isOpen(meeting)) {
            eventLogManagementService.rsvpForEvent(meeting, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.RSVP_SENT, RestStatus.SUCCESS);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @PathVariable("id") String id) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(id);
        EventLog eventLog = eventLogManagementService.getEventLogOfUser(meeting, user, EventLogType.EventRSVP);
        boolean hasResponded = eventLogManagementService.userRsvpForEvent(meeting, user);
        ResponseTotalsDTO totals = eventLogManagementService.getResponseCountForEvent(meeting);
        EventWrapper eventWrapper = new EventWrapper(meeting, eventLog, user, hasResponded, totals);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.MEETING_DETAILS, RestStatus.SUCCESS,
                eventWrapper);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().isAfter(Instant.now());
    }


}
