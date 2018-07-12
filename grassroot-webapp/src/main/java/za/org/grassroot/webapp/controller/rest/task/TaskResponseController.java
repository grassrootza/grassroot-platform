package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.exception.TaskFinishedException;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping(value = "/v2/api/task/respond") @Api("/v2/api/task/respond")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class TaskResponseController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final EventLogBroker eventLogBroker;
    private final VoteBroker voteBroker;
    private final TaskImageBroker taskImageBroker;

    @Autowired
    public TaskResponseController(JwtService jwtService, UserManagementService userManager, TaskBroker taskBroker, EventLogBroker eventLogBroker, VoteBroker voteBroker, TaskImageBroker taskImageBroker) {
        super(jwtService, userManager);
        this.taskBroker = taskBroker;
        this.eventLogBroker = eventLogBroker;
        this.voteBroker = voteBroker;
        this.taskImageBroker = taskImageBroker;
    }

    @RequestMapping(value = "/meeting/{taskUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Respond to a meeting invite, with RSVP options YES, NO, MAYBE (upper case)")
    public ResponseEntity respondToMeeting(HttpServletRequest request, @PathVariable String taskUid,
                                           @RequestParam EventRSVPResponse response) {
        eventLogBroker.rsvpForEvent(taskUid, getUserIdFromRequest(request), response);
        try {
            return ResponseEntity.ok(taskBroker.loadResponses(getUserIdFromRequest(request), taskUid, TaskType.MEETING));
        } catch (TaskFinishedException e) {
            return RestUtil.errorResponse(RestMessage.MEETING_PAST);
        }
    }

    @RequestMapping(value = "/vote/{taskUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Cast a response to a vote, with the user's response (taken from vote entity) as 'vote'")
    public ResponseEntity respondToVote(HttpServletRequest request,
                                        @PathVariable String taskUid,
                                        @RequestParam String vote) {
        try {
            voteBroker.recordUserVote(getUserIdFromRequest(request), taskUid, vote.trim());
            TaskFullDTO taskDto = new TaskFullDTO(voteBroker.load(taskUid), getUserFromRequest(request), Instant.now(), vote);
            taskDto.setVoteResults(voteBroker.fetchVoteResults(getUserIdFromRequest(request), taskUid, true));
            return ResponseEntity.ok(taskDto); // so it goes back with latest results
        } catch (IllegalArgumentException e) {
            return RestUtil.errorResponse(RestMessage.USER_NOT_PART_OF_VOTE);
        } catch (TaskFinishedException e) {
            return RestUtil.errorResponse(RestMessage.VOTE_ALREADY_CLOSED);
        }
    }

    @RequestMapping(value = "/post/{userUid}/{taskType}/{taskUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadTaskImage(@PathVariable String userUid,
                                                           @PathVariable TaskType taskType,
                                                           @PathVariable String taskUid,
                                                           @RequestParam MultipartFile image,
                                                           @RequestParam(required = false) String caption,
                                                           @RequestParam(required = false) Double longitude,
                                                           @RequestParam(required = false) Double latitude) {

        log.info("uploading an image with long={}, lat={}", longitude, latitude);
        try {
            String actionLogUid = taskImageBroker.storeImageForTask(userUid, taskUid, taskType, image, caption,
                    longitude, latitude);
            return !StringUtils.isEmpty(actionLogUid) ?
                    RestUtil.okayResponseWithData(RestMessage.MEETING_IMAGE_ADDED, actionLogUid) :
                    RestUtil.errorResponse(RestMessage.MEETING_IMAGE_ERROR);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

}
