package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.time.Instant;
import java.util.Set;

@Slf4j
@RestController
@Api("/api/task/create")
@RequestMapping(value = "/api/task/create")
public class TaskCreateController {

    private final EventBroker eventBroker;
    private final UserManagementService userService;

    @Autowired
    public TaskCreateController(UserManagementService userManagementService, EventBroker eventBroker, TaskImageBroker taskImageBroker) {
        this.userService = userManagementService;
        this.eventBroker = eventBroker;
    }

    @RequestMapping(value = "/create/{parentType}/{userUid}/{parentUid}", method = RequestMethod.POST)
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

        Meeting createdMeeting = eventBroker.createMeeting(helper);
        return ResponseEntity.ok(new TaskFullDTO(createdMeeting, userService.load(userUid),
                createdMeeting.getCreatedDateTime(), false));
    }
}
