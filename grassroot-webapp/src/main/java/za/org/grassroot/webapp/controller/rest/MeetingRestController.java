package za.org.grassroot.webapp.controller.rest;

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
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.services.*;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.MeetingRsvpsDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    private EventLogBroker eventLogBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private EventManagementService eventManagementService; // todo :really need to migrate from this old thing to broker

    @Autowired
    private EventLogRepository eventLogRepository;

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
        try {
            Meeting meeting = eventBroker.createMeeting(user.getUid(), parentUid, JpaEntityType.GROUP, title, eventStartDateTime,
                    location, false, true, false, reminderType, reminderMinutes, description,
                    assignedMemberUids, MeetingImportance.ORDINARY);
            TaskDTO createdMeeting = taskBroker.load(user.getUid(), meeting.getUid(), TaskType.MEETING);
            return RestUtil.okayResponseWithData(RestMessage.MEETING_CREATED, Collections.singletonList(createdMeeting));
        } catch (EventStartTimeNotInFutureException e) {
	        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TIME_CANNOT_BE_IN_THE_PAST);
        }
    }

    @RequestMapping(value = "/update/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskDTO> updateMeeting(@PathVariable("phoneNumber")String phoneNumber, @PathVariable("code") String code,
                                                         @PathVariable("meetingUid") String meetingUid,
                                                         @RequestParam("title") String title,
                                                         @RequestParam(value = "description", required =false ) String description,
                                                         @RequestParam("startTime") LocalDateTime time,
                                                         @RequestParam("location") String location,
                                                         @RequestParam(value="members", required=false) Set<String> members) {

        log.info("inside update meeting ... received, date time: {}, members: {}", time.format(DateTimeFormatter.ISO_DATE_TIME), members != null ? members : "null");
	    User user = userManagementService.findByInputNumber(phoneNumber);
	    ResponseEntity<TaskDTO> responseEntity;

	    try {
	        eventBroker.updateMeeting(user.getUid(), meetingUid, title, description, time, location, null, -1, members);
            TaskDTO updatedTask =  taskBroker.load(user.getUid(), meetingUid, TaskType.MEETING);
            responseEntity = new ResponseEntity<>(updatedTask, HttpStatus.OK);
        } catch (IllegalStateException e) {
		    // todo : need this to be more intelligent
            responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

	    log.info("returning response entity: {}, headers: {}, body: {}", responseEntity.getStatusCode(), responseEntity.getHeaders(), responseEntity.getBody());
	    return responseEntity;
    }

    @RequestMapping(value = "/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                @PathVariable("code") String code, @PathVariable("id") String meetingUid,
                                                @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        String trimmedResponse = response.toLowerCase().trim();
        ResponseEntity<ResponseWrapper> responseWrapper;
        if (meeting.isCanceled()) {
	        responseWrapper = RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.MEETING_ALREADY_CANCELLED);
        } else if (isOpen(meeting)) {
            eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.fromString(trimmedResponse));
            TaskDTO updatedTask = taskBroker.load(user.getUid(), meetingUid, TaskType.MEETING);
            responseWrapper = RestUtil.okayResponseWithData(RestMessage.RSVP_SENT, Collections.singletonList(updatedTask));
        } else {
            responseWrapper = RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE);
        }
        return responseWrapper;
    }

    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> view(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @PathVariable("id") String id) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(id);
        ResponseTotalsDTO totals = eventLogBroker.getResponseCountForEvent(meeting);
        EventWrapper eventWrapper = new EventWrapper(meeting, user, totals, eventLogRepository);
	    return RestUtil.okayResponseWithData(RestMessage.MEETING_DETAILS, eventWrapper);
    }

    @RequestMapping(value = "/rsvps/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.GET)
    public ResponseEntity<MeetingRsvpsDTO> listRsvps(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String meetingUid) {
        // todo : some permission checking to make sure user can see details
        User user = userManagementService.findByInputNumber(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        ResponseTotalsDTO totals = eventLogBroker.getResponseCountForEvent(meeting);

        log.info("here are the rsvp totals: {}", totals);

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

    @RequestMapping(value = "/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelMeeting(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @RequestParam("uid") String meetingUid){
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        String userUid = user.getUid();
        Event event = eventBroker.load(meetingUid);
        ResponseEntity<ResponseWrapper> responseWrapper;
        if(!event.isCanceled()){
            eventBroker.cancel(userUid,meetingUid);
            responseWrapper = RestUtil.messageOkayResponse(RestMessage.MEETING_CANCELLED);
        }else{
            responseWrapper = RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.MEETING_ALREADY_CANCELLED);
        }
        return responseWrapper;
    }

    private boolean isOpen(Event event) {
	    return event.getEventStartDateTime().isAfter(Instant.now());
    }

}
