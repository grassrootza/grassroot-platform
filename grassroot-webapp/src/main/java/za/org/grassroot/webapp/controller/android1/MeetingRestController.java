package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.*;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.MeetingRsvpsDTO;
import za.org.grassroot.webapp.model.rest.wrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
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

    // todo: consolidate in this list
    private final UserManagementService userManagementService;
    private final EventLogBroker eventLogBroker;
    private final EventBroker eventBroker;
    private final TaskBroker taskBroker;
    private final PermissionBroker permissionBroker;
    private final EventLogRepository eventLogRepository;
    private final TaskImageBroker taskImageBroker;

    @Autowired
    public MeetingRestController(UserManagementService userManagementService, EventLogBroker eventLogBroker, EventBroker eventBroker, TaskBroker taskBroker, PermissionBroker permissionBroker, EventLogRepository eventLogRepository, TaskImageBroker taskImageBroker) {
        this.userManagementService = userManagementService;
        this.eventLogBroker = eventLogBroker;
        this.eventBroker = eventBroker;
        this.taskBroker = taskBroker;
        this.permissionBroker = permissionBroker;
        this.eventLogRepository = eventLogRepository;
        this.taskImageBroker = taskImageBroker;
    }

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
                                                         @RequestParam(value="members", required = false) Set<String> members,
                                                         @RequestParam(required = false) MultipartFile image) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Set<String> assignedMemberUids = (members == null) ? new HashSet<>() : members;
        EventReminderType reminderType = reminderMinutes == -1 ? EventReminderType.GROUP_CONFIGURED : EventReminderType.CUSTOM;

        try {
            MeetingBuilderHelper helper = new MeetingBuilderHelper()
                    .userUid(user.getUid())
                    .parentUid(parentUid)
                    .parentType(JpaEntityType.GROUP)
                    .name(title)
                    .startDateTime(eventStartDateTime)
                    .location(location)
                    .reminderType(reminderType)
                    .customReminderMinutes(reminderMinutes)
                    .description(description)
                    .assignedMemberUids(assignedMemberUids);

            if (image != null) {
                helper.taskImageKey(taskImageBroker.storeImagePreTask(TaskType.MEETING, image));
            }

            log.debug("meetingHelper: {}", helper);
            Meeting meeting = eventBroker.createMeeting(helper);
            TaskDTO createdMeeting = taskBroker.load(user.getUid(), meeting.getUid(), TaskType.MEETING);
            return RestUtil.okayResponseWithData(RestMessage.MEETING_CREATED, Collections.singletonList(createdMeeting));
        } catch (EventStartTimeNotInFutureException e) {
	        return RestUtil.errorResponse(RestMessage.TIME_CANNOT_BE_IN_THE_PAST);
        } catch (AccountLimitExceededException e) {
            return RestUtil.errorResponse(RestMessage.EVENT_LIMIT_REACHED);
        }
    }

    @RequestMapping(value = "/public/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateMeetingPublic(@PathVariable String phoneNumber, @PathVariable String meetingUid,
                                                               @RequestParam boolean setPublic,
                                                               @RequestParam(required = false) Double latitude,
                                                               @RequestParam(required = false) Double longitude) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            GeoLocation location = longitude != null && latitude != null ? new GeoLocation(latitude, longitude) : null;
            eventBroker.updateMeetingPublicStatus(user.getUid(), meetingUid, setPublic, location, UserInterfaceType.WEB);
            return RestUtil.okayResponseWithData(RestMessage.MEETING_PUBLIC_UPDATED,
                    taskBroker.load(user.getUid(), meetingUid, TaskType.MEETING));
        } catch (AccessDeniedException e) {
            return RestUtil.errorResponse(RestMessage.PERMISSION_DENIED);
        }
    }

    @RequestMapping(value = "/update/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateMeeting(@PathVariable("phoneNumber")String phoneNumber, @PathVariable("code") String code,
                                                         @PathVariable("meetingUid") String meetingUid,
                                                         @RequestParam("title") String title,
                                                         @RequestParam(value = "description", required =false ) String description,
                                                         @RequestParam("startTime") LocalDateTime time,
                                                         @RequestParam("location") String location,
                                                         @RequestParam(value="members", required=false) Set<String> members) {

        log.info("inside update meeting ... received, date time: {}, members: {}", time.format(DateTimeFormatter.ISO_DATE_TIME), members != null ? members : "null");
	    User user = userManagementService.findByInputNumber(phoneNumber);

        try {
	        eventBroker.updateMeeting(user.getUid(), meetingUid, title, description, time, location, null, -1, members);
            TaskDTO updatedTask =  taskBroker.load(user.getUid(), meetingUid, TaskType.MEETING);
            return RestUtil.okayResponseWithData(RestMessage.MEETING_DETAILS_UPDATED, Collections.singletonList(updatedTask));
        } catch (IllegalStateException e) {
		    return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.MEETING_UPDATE_ERROR);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                @PathVariable("code") String code, @PathVariable("id") String meetingUid,
                                                @RequestParam(value = "response", required = true) String response) {

        User user = userManagementService.findByInputNumber(phoneNumber);
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
        User user = userManagementService.findByInputNumber(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(id);
        ResponseTotalsDTO totals = eventLogBroker.getResponseCountForEvent(meeting);
        EventWrapper eventWrapper = new EventWrapper(meeting, user, totals, eventLogRepository);
	    return RestUtil.okayResponseWithData(RestMessage.MEETING_DETAILS, eventWrapper);
    }

    @RequestMapping(value = "/rsvps/{phoneNumber}/{code}/{meetingUid}", method = RequestMethod.GET)
    public ResponseEntity<MeetingRsvpsDTO> listRsvps(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String meetingUid) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);

        ResponseTotalsDTO totals = eventLogBroker.getResponseCountForEvent(meeting);

        log.info("here are the rsvp totals: {}", totals);

        boolean canViewRsvps = meeting.getCreatedByUser().equals(user) ||
                permissionBroker.isGroupPermissionAvailable(user, meeting.getAncestorGroup(), Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);

        MeetingRsvpsDTO rsvpsDTO;
        if (canViewRsvps) {
            rsvpsDTO = new MeetingRsvpsDTO(meetingUid, totals, eventBroker.getRSVPResponses(meeting));
        } else {
            rsvpsDTO = new MeetingRsvpsDTO(meetingUid, totals);
        }

        return new ResponseEntity<>(rsvpsDTO, HttpStatus.OK);
    }

    @RequestMapping(value = "/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelMeeting(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @RequestParam("uid") String meetingUid){
        User user = userManagementService.findByInputNumber(phoneNumber);
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
