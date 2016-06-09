
package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.MeetingRsvpsDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private UserManagementService userManagementService;

    @Autowired
    private EventLogManagementService eventLogManagementService;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private EventManagementService eventManagementService; // todo :really need to migrate from this old thing to broker

    @InitBinder
    public void initBinder(ServletRequestDataBinder binder) {
        binder.registerCustomEditor(LocalDateTime.class, new LocalDateTimePropertyEditor(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBadRequest(HttpMessageNotReadableException e) {
        log.warn("Returning HTTP 400", e);
    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createMeeting(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String parentUid, @RequestParam String title,
                                                         @RequestParam String description,
                                                         @RequestParam LocalDateTime eventStartDateTime,
                                                         @RequestParam int reminderMinutes,
                                                         @RequestParam String location,
                                                         @RequestParam(value="members", required = false) Set<String> members) {

        log.info("REST : received meeting create request... with local date time: {}, and members: {}",
                 eventStartDateTime.toString(), members == null ? "null" : members.toString());

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Set<String> assignedMemberUids = (members == null) ? new HashSet<>() : members;
        EventReminderType reminderType = reminderMinutes == -1 ? EventReminderType.GROUP_CONFIGURED : EventReminderType.CUSTOM;

        // todo : decide what to do with event reminder types
        Meeting meeting = eventBroker.createMeeting(user.getUid(), parentUid, JpaEntityType.GROUP, title, eventStartDateTime,
                                                    location, false, true, false, reminderType, reminderMinutes, description,
                                                    assignedMemberUids);
        TaskDTO createdMeeting = taskBroker.load(user.getUid(), meeting.getUid(), TaskType.MEETING);

        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.CREATED, RestMessage.MEETING_CREATED,
                                                                     RestStatus.SUCCESS, Collections.singletonList(createdMeeting));

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
            EventReminderType reminderType = reminderMinutes == -1 ? EventReminderType.GROUP_CONFIGURED : EventReminderType.CUSTOM;
            eventBroker.updateMeeting(user.getUid(), meetingUid, title, dateTime, location, includeSubGroups, rsvp, relayable,
                    reminderType, reminderMinutes, description);
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
            TaskDTO updatedTask = taskBroker.load(user.getUid(), meetingUid, TaskType.MEETING);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.RSVP_SENT,
                                                         RestStatus.SUCCESS, Collections.singletonList(updatedTask));
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

    @RequestMapping(value = "/rsvps/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.GET)
    public ResponseEntity<MeetingRsvpsDTO> listRsvps(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String meetingUid) {
        // todo : some permission checking to make sure user can see details
        User user = userManagementService.findByInputNumber(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        ResponseTotalsDTO totals = eventLogManagementService.getResponseCountForEvent(meeting);

        boolean canViewRsvps = meeting.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);

        MeetingRsvpsDTO rsvpsDTO;
        if (canViewRsvps) {
            rsvpsDTO = new MeetingRsvpsDTO(meetingUid, totals, eventManagementService.getRSVPResponses(meeting));
        } else {
            rsvpsDTO = new MeetingRsvpsDTO(meetingUid, totals);
        }

        return new ResponseEntity<>(rsvpsDTO, HttpStatus.OK);
    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().isAfter(Instant.now());
    }


}
