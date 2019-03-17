package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.util.MapUtils;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.task.vote.VoteDetailsResponse;
import za.org.grassroot.webapp.controller.rest.task.vote.VoteUpdateRequest;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

@Slf4j @RestController @Grassroot2RestController
@Api("/v2/api/task/modify") @RequestMapping(value = "/v2/api/task/modify")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class TaskModifyController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final VoteBroker voteBroker;

    @Autowired
    public TaskModifyController(JwtService jwtService, UserManagementService userManagementService, TaskBroker taskBroker, VoteBroker voteBroker) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.voteBroker = voteBroker;
    }

    @RequestMapping(value = "/cancel/{taskType}/{taskUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Cancel a task (with option of sending out notices to users)")
    public ResponseEntity cancelTask(HttpServletRequest request,
                                     @PathVariable TaskType taskType,
                                     @PathVariable String taskUid,
                                     @RequestParam boolean sendNotifications,
                                     @RequestParam(required = false) String reason) {
        log.info("cancelling task, notification params: send : {}, reason: {}", sendNotifications, reason);
        taskBroker.cancelTask(getUserIdFromRequest(request), taskUid, taskType, sendNotifications, reason);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/change/date/{taskType}/{taskUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskFullDTO> changeTaskDateTime(HttpServletRequest request,
                                                          @PathVariable TaskType taskType,
                                                          @PathVariable String taskUid,
                                                          @RequestParam long newTaskTimeMills) {
        Instant newTime = Instant.ofEpochMilli(newTaskTimeMills);
        log.info("changing task date/time, long: {}, date-time: {}", newTaskTimeMills, newTime);
        TaskFullDTO alteredTask = taskBroker.changeTaskDate(getUserIdFromRequest(request), taskUid, taskType, newTime);
        return ResponseEntity.ok(alteredTask);
    }

    @RequestMapping(value = "/vote/details/{voteUid}", method = RequestMethod.GET)
    public ResponseEntity fetchVoteAdditionalDetails(HttpServletRequest request, @PathVariable String voteUid) {
        log.info("Fetching additional vote details for vote with uid: {}", voteUid);
        final Vote vote = taskBroker.loadEntity(getUserIdFromRequest(request), voteUid, TaskType.VOTE, Vote.class);
        return ResponseEntity.ok(new VoteDetailsResponse(vote));
    }

    @RequestMapping(value = "/vote/details/{voteUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskFullDTO> updateVoteAdditionalDetails(HttpServletRequest request, @PathVariable String voteUid,
                                                                   @RequestBody VoteUpdateRequest updateRequest) {
        log.info("Received vote update request: {}", updateRequest);
        final String userUid = getUserIdFromRequest(request);

        if (updateRequest.getVoteClosingDateMillis() != null)
            taskBroker.changeTaskDate(userUid, voteUid, TaskType.VOTE, Instant.ofEpochMilli(updateRequest.getVoteClosingDateMillis()));

        if (!MapUtils.isEmpty(updateRequest.getMultiLanguagePrompts()) || !MapUtils.isEmpty(updateRequest.getPostVotePrompts()))
            voteBroker.updateMassVotePrompts(userUid, voteUid, updateRequest.getMultiLanguagePrompts(), updateRequest.getPostVotePrompts());

        if (!CollectionUtils.isEmpty(updateRequest.getVoteOptions()) || !MapUtils.isEmpty(updateRequest.getMultiLingualOptions()))
            voteBroker.updateVoteOptions(userUid, voteUid, updateRequest.getVoteOptions(), updateRequest.getMultiLingualOptions());

        if (updateRequest.togglesChanged())
            voteBroker.updateMassVoteToggles(userUid, voteUid, updateRequest.getRandomizeOptions(),
                    updateRequest.getPreCloseVote(), updateRequest.getNoChangeVote());

        TaskFullDTO taskFullDTO = taskBroker.fetchTaskOnly(userUid, voteUid, TaskType.VOTE);
        return ResponseEntity.ok(taskFullDTO);
    }

    @RequestMapping(value = "/vote/details/postvote/clear/{voteUid}", method = RequestMethod.POST)
    public ResponseEntity<VoteDetailsResponse> updateVoteClearPostVote(HttpServletRequest request, @PathVariable String voteUid) {
        log.info("Clearing post-vote prompts");
        voteBroker.updateMassVoteClearPostVote(getUserIdFromRequest(request), voteUid);
        return ResponseEntity.ok(new VoteDetailsResponse(voteBroker.load(voteUid)));
    }

}
