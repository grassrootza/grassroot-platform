package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.services.task.VoteHelper;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController @Grassroot2RestController
@Api("/v2/api/task/create/meeting")
@RequestMapping(value = "/v2/api/task/create/")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class EventCreateController extends BaseRestController{

    private final EventBroker eventBroker;
    private final UserManagementService userService;

    @Autowired
    public EventCreateController(JwtService jwtService, UserManagementService userManagementService, EventBroker eventBroker) {
        super(jwtService, userManagementService);
        this.userService = userManagementService;
        this.eventBroker = eventBroker;
    }

    @RequestMapping(value = "/meeting/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Call a meeting", notes = "Creates a meeting, that starts at the specified date and time, passed in " +
            "as epoch millis. The first five params are necessary, the rest are optional")
    public ResponseEntity<TaskFullDTO> createMeeting(HttpServletRequest request,
                                                     @PathVariable String parentUid,
                                                     @PathVariable JpaEntityType parentType,
                                                     @RequestParam String subject,
                                                     @RequestParam String location,
                                                     @RequestParam long dateTimeEpochMillis,
                                                     @RequestParam(required = false) String description,
                                                     @RequestParam(required = false) boolean publicMeeting,
                                                     @RequestParam(required = false) Double userLat,
                                                     @RequestParam(required = false) Double userLong,
                                                     @RequestParam(required = false)
                                                     @ApiParam(value = "UIDs of assigned members, if left blank all " +
                                                             "members of the parent are assigned") Set<String> assignedMemberUids,
                                                     @RequestParam(required = false)
                                                     @ApiParam(value = "Server UID of an optional image to include " +
                                                             "in the meeting call") String mediaFileUid,
                                                     @RequestParam(required = false) EventSpecialForm meetingImportance) {
        String userUid = getUserIdFromRequest(request);
        log.info("creating a meeting, subject ={}, location = {}, parentUid = {}", subject, location,parentUid);

        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(userUid)
                .parentType(parentType)
                .parentUid(parentUid)
                .name(subject)
                .location(location)
                .description(description)
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

        if(meetingImportance != null){
            helper = helper.importance(meetingImportance);
        }

        log.info("Helper={}",helper);

        try {
            Meeting createdMeeting = eventBroker.createMeeting(helper, UserInterfaceType.REST_GENERIC);
            return ResponseEntity.ok(new TaskFullDTO(createdMeeting, userService.load(userUid),
                    createdMeeting.getCreatedDateTime(), null));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        }
    }

    @RequestMapping(value = "/vote/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Call a vote", notes = "Creates a vote, that starts at the specified date and time, passed in " +
            "as epoch millis. The first five params are necessary, the rest are optional")
    public ResponseEntity<TaskFullDTO> createVote(HttpServletRequest request,
                                                  @PathVariable String parentUid,
                                                  @PathVariable JpaEntityType parentType,
                                                  @RequestParam String title,
                                                  @RequestParam(required = false) List<String> voteOptions,
                                                  @RequestParam(required = false) String description,
                                                  @RequestParam long time,
                                                  @RequestParam(required = false) EventSpecialForm specialForm,
                                                  @RequestParam(required = false) Boolean randomizeOptions,
                                                  @RequestParam(required = false) String mediaFileUid,
                                                  @RequestParam(required = false) @ApiParam(value = "UIDs of assigned members, if left blank all " +
                                                              "members of the parent are assigned") Set<String> assignedMemberUids){

        String userUid = getUserIdFromRequest(request);

        LocalDateTime eventStartDateTime = Instant.ofEpochMilli(time).atZone(DateTimeUtil.getSAST()).toLocalDateTime();

        try {
            User user = userService.load(userUid);

            assignedMemberUids = assignedMemberUids == null ? Collections.emptySet() : assignedMemberUids;
            log.info("title={}, description={}, time={}, members={}, options={}, special form={}, randomize={}", title, description, eventStartDateTime, assignedMemberUids, voteOptions, specialForm, randomizeOptions);

            VoteHelper helper = VoteHelper.builder()
                    .userUid(user.getUid())
                    .parentUid(parentUid)
                    .parentType(parentType)
                    .name(title)
                    .eventStartDateTime(eventStartDateTime)
                    .description(description)
                    .options(voteOptions)
                    .taskImageKey(mediaFileUid)
                    .assignMemberUids(assignedMemberUids)
                    .specialForm(specialForm)
                    .randomizeOptions(randomizeOptions != null && randomizeOptions)
                    .build();

            log.info("corresponding helper: {}", helper);

            Vote vote = eventBroker.createVote(helper);

            log.debug("Vote={},User={}",vote,user);
            return ResponseEntity.ok(new TaskFullDTO(vote, user,vote.getCreatedDateTime(), null));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        }
    }

}
