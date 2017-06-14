package za.org.grassroot.webapp.controller.rest.android;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.EventWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

/**
 * Created by aakilomar on 10/24/15.
 */
@RestController
@RequestMapping("/api/vote")
public class VoteRestController {

    private static final Logger log = LoggerFactory.getLogger(VoteRestController.class);

    // todo: clean up some of the dependencies in here
    private final UserManagementService userManagementService;
    private final VoteBroker voteBroker;
    private final EventBroker eventBroker;
    private final TaskBroker taskBroker;
    private final PermissionBroker permissionBroker;
    private final EventLogRepository eventLogRepository;

    @Autowired
    public VoteRestController(UserManagementService userManagementService, VoteBroker voteBroker, EventBroker eventBroker, TaskBroker taskBroker, PermissionBroker permissionBroker, EventLogRepository eventLogRepository) {
        this.userManagementService = userManagementService;
        this.voteBroker = voteBroker;
        this.eventBroker = eventBroker;
        this.taskBroker = taskBroker;
        this.permissionBroker = permissionBroker;
        this.eventLogRepository = eventLogRepository;
    }

    @RequestMapping(value = "/create/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createVote(@PathVariable("phoneNumber") String phoneNumber,
                                                      @PathVariable("id") String groupUid,
                                                      @RequestParam("title") String title,
                                                      @RequestParam(value = "closingTime") String time,
                                                      @RequestParam(value = "description", required = false) String description,
                                                      @RequestParam("reminderMins") int reminderMinutes,
                                                      @RequestParam(value = "members", required = false) List<String> members,
                                                      @RequestParam(value = "options", required = false) List<String> options) {

        log.info("ZOG: Creating vote with parameters ... phoneNumber: {}, groupUid: {}, title: {}, closingTime: {}," +
                         "description: {}, ", phoneNumber, groupUid, title, time);

        User user = userManagementService.findByInputNumber(phoneNumber);
        Set<String> membersUid = Sets.newHashSet();
        if (members != null) {
            membersUid.addAll(members);
        }

        try {
            // todo : atomicity in broker calls, also handle bad UIDs
            LocalDateTime eventStartDateTime = LocalDateTime.parse(time.trim(), getPreferredRestFormat());
            List<String> voteOptions = StringArrayUtil.isAllEmptyOrNull(options) ? null : options;
            Vote vote = eventBroker.createVote(user.getUid(), groupUid, JpaEntityType.GROUP, title, eventStartDateTime,
                    false, description, membersUid, voteOptions);
            eventBroker.updateReminderSettings(user.getUid(), vote.getUid(), EventReminderType.CUSTOM,
                    RestUtil.getReminderMinutes(reminderMinutes));
            TaskDTO voteCreated = taskBroker.load(user.getUid(), vote.getUid(), TaskType.VOTE);
            return RestUtil.okayResponseWithData(RestMessage.VOTE_CREATED, Collections.singletonList(voteCreated));
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        } catch (EventStartTimeNotInFutureException e) {
            return RestUtil.errorResponse(RestMessage.TIME_CANNOT_BE_IN_THE_PAST);
        } catch (AccountLimitExceededException e) {
            return RestUtil.errorResponse(RestMessage.EVENT_LIMIT_REACHED);
        }
    }

    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> viewVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                    @PathVariable("id") String voteUid) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Vote vote = voteBroker.load(voteUid);
        Map<String, Long> voteResults = voteBroker.fetchVoteResults(user.getUid(), voteUid);
        EventWrapper eventWrapper = new EventWrapper(vote, user, voteResults, eventLogRepository);
        return RestUtil.okayResponseWithData(RestMessage.VOTE_DETAILS, eventWrapper);
    }

    @RequestMapping(value = "/totals/{phoneNumber}/{code}/{voteUid}", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Long>> fetchVoteTotals(@PathVariable String phoneNumber, @PathVariable String code,
                                                             @PathVariable String voteUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Event event = eventBroker.load(voteUid);

        permissionBroker.validateGroupPermission(user, event.getAncestorGroup(), null);

        Map<String, Long> totals = voteBroker.fetchVoteResults(user.getUid(), voteUid);
        return totals != null ? new ResponseEntity<>(totals, HttpStatus.OK) :
                new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @RequestMapping(value = "/do/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> castVote(@PathVariable("phoneNumber") String phoneNumber,
                                                    @PathVariable("id") String voteUid,
                                                    @RequestParam String response) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Event event = eventBroker.load(voteUid);
        String trimmedResponse = transformResponseIncludingLegacy(response);
        log.info("casting a vote response! : {}", trimmedResponse);

        ResponseEntity<ResponseWrapper> responseWrapper;
        if (event.getEventType().equals(EventType.VOTE) && isOpen(event)) {
            voteBroker.recordUserVote(user.getUid(), voteUid, trimmedResponse);
            TaskDTO updatedTask = taskBroker.load(user.getUid(), voteUid, TaskType.VOTE);
            responseWrapper = RestUtil.okayResponseWithData(RestMessage.VOTE_SENT, Collections.singletonList(updatedTask));
        } else {
            responseWrapper = RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.VOTE_CLOSED);
        }

        return responseWrapper;
    }

    // because some of the old clients may still send "maybe" expecting the old design .. can remove in time
    private String transformResponseIncludingLegacy(String response) {
        final String normResp = response.trim().toLowerCase();
        return "maybe".equals(normResp) ? "ABSTAIN" :
                "yes".equals(normResp) ? "YES" :
                        "no".equals(normResp) ? "NO" :
                                response.trim();
    }

    @RequestMapping(value = "/update/{uid}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                      @PathVariable("uid") String voteUid, @RequestParam("title") String title,
                                                      @RequestParam(value = "closingTime") String time,
                                                      @RequestParam(value = "description", required = false) String description) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        ResponseEntity<ResponseWrapper> responseWrapper;
        try {
            LocalDateTime updatedTime = LocalDateTime.parse(time.trim(), getPreferredRestFormat());
            eventBroker.updateVote(user.getUid(), voteUid, updatedTime, description);
            TaskDTO updatedTask = taskBroker.load(user.getUid(), voteUid, TaskType.VOTE);
            responseWrapper = RestUtil.okayResponseWithData(RestMessage.VOTE_DETAILS_UPDATED, Collections.singletonList(updatedTask));
        } catch (IllegalStateException e) {
            responseWrapper = RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.VOTE_ALREADY_CANCELLED);
        }

        return responseWrapper;
    }

    @RequestMapping(value = "/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelVote(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @RequestParam("uid") String voteUid){
        User user = userManagementService.findByInputNumber(phoneNumber);
        String userUid = user.getUid();
        Event event = eventBroker.load(voteUid);
        ResponseEntity<ResponseWrapper> responseWrapper;
        if (!event.isCanceled()){
            eventBroker.cancel(userUid,voteUid);
            responseWrapper = RestUtil.messageOkayResponse(RestMessage.VOTE_CANCELLED);
        }else{
            responseWrapper = RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.VOTE_ALREADY_CANCELLED);
        }

        return responseWrapper;
    }


    private boolean isOpen(Event event) { return event.getEventStartDateTime().isAfter(Instant.now()); }

}
