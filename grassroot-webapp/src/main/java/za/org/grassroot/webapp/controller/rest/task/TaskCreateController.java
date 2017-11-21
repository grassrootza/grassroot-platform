package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.VoteType;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.web.VoteWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

@Slf4j
@RestController @Grassroot2RestController
@Api("/api/task/create")
@RequestMapping(value = "/api/task/create")
public class TaskCreateController {

    private final EventBroker eventBroker;
    private final UserManagementService userService;
    private final GroupBroker groupBroker;
    private final TaskBroker taskBroker;

    @Autowired
    public TaskCreateController(UserManagementService userManagementService, EventBroker eventBroker, TaskImageBroker taskImageBroker,
                                GroupBroker groupBroker,TaskBroker taskBroker) {
        this.userService = userManagementService;
        this.eventBroker = eventBroker;
        this.groupBroker = groupBroker;
        this.taskBroker = taskBroker;
    }

    @RequestMapping(value = "/meeting/{userUid}/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Call a meeting", notes = "Creates a meeting, that starts at the specified date and time, passed in " +
            "as epoch millis. The first six params are necessary, the rest are optional")
    public ResponseEntity<TaskFullDTO> createMeeting(@PathVariable String userUid,
                                                     @PathVariable String parentUid,
                                                     @PathVariable JpaEntityType parentType,
                                                     @RequestParam String subject,
                                                     @RequestParam String location,
                                                     @RequestParam long dateTimeEpochMillis,
                                                     @RequestParam(required = false) boolean publicMeeting,
                                                     @RequestParam(required = false) Long userLat,
                                                     @RequestParam(required = false) Long userLong,
                                                     @RequestParam(required = false)
                                                     @ApiParam(value = "UIDs of assigned members, if left blank all " +
                                                             "members of the parent are assigned") Set<String> assignedMemberUids,
                                                     @RequestParam(required = false)
                                                     @ApiParam(value = "Server UID of an optional image to include " +
                                                             "in the meeting call") String mediaFileUid) {
        log.info("creating a meeting, subject ={}, location = {}", subject, location);

        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(userUid)
                .parentType(parentType)
                .parentUid(parentUid)
                .name(subject)
                .location(location)
                .startDateTimeInstant(Instant.ofEpochMilli(dateTimeEpochMillis));

        if (publicMeeting) {
            helper = helper.isPublic(true);
        }

        if (userLat != null && userLong != null) {
            helper = helper.userLocation(new GeoLocation(userLat, userLong));
        }

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            helper.assignedMemberUids(assignedMemberUids);
        }

        if (!StringUtils.isEmpty(mediaFileUid)) {
            helper.taskImageKey(mediaFileUid);
        }

        try {
            Meeting createdMeeting = eventBroker.createMeeting(helper);
            return ResponseEntity.ok(new TaskFullDTO(createdMeeting, userService.load(userUid),
                    createdMeeting.getCreatedDateTime(), null));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        }
    }

    @RequestMapping(value = "/vote/{userUid}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Call a vote", notes = "Creates a vote, that starts at the specified date and time, passed in " +
            "as epoch millis. The first six params are necessary, the rest are optional")
    public ResponseEntity<TaskFullDTO> createVote(@PathVariable String userUid,
                                                      @PathVariable String parentUid,
                                                      @RequestParam String title,
                                                      @RequestParam(required = false) List<String> voteOptions,
                                                      @RequestParam(required = false) String description,
                                                      @RequestParam String time,
                                                      @RequestParam(required = false)
                                                      @ApiParam(value = "UIDs of assigned members, if left blank all " +
                                                              "members of the parent are assigned") Set<String> assignedMemberUids){

        LocalDateTime eventStartDateTime = LocalDateTime.parse(time.trim(), getPreferredRestFormat());
        try{
            Group group = groupBroker.load(parentUid);
            User user = userService.load(userUid);

            List<String> options = StringArrayUtil.isAllEmptyOrNull(voteOptions) ? null : voteOptions;

            Vote vote = eventBroker.createVote(user.getUid(), parentUid, JpaEntityType.VOTE, title, eventStartDateTime,
                    false, description, assignedMemberUids, voteOptions);

            log.info("Vote Id={},User ID*********** ={},Group Uid={}",vote.getUid(),user.getUid(),group.getUid());
            return ResponseEntity.ok(new TaskFullDTO(vote, userService.load(userUid),
                    vote.getCreatedDateTime(), null));
        }catch (AccessDeniedException e){
            throw new MemberLacksPermissionException(Permission.VOTE_CREATE_PERMISSION);
        }
    }
}
