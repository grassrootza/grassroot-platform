package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

@Slf4j @RestController @Grassroot2RestController
@Api("/v2/api/task/modify") @RequestMapping(value = "/v2/api/task/modify")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class TaskModifyController extends BaseRestController {

    private final TaskBroker taskBroker;

    public TaskModifyController(JwtService jwtService, UserManagementService userManagementService, TaskBroker taskBroker) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
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

}
