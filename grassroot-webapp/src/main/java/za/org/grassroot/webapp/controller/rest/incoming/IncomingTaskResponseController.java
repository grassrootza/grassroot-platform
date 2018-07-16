package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController @Grassroot2RestController
@RequestMapping("/v2/api/inbound/respond/") @Api("/v2/api/inbound/respond/")
public class IncomingTaskResponseController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final PasswordTokenService tokenService;

    public IncomingTaskResponseController(JwtService jwtService, UserManagementService userManagementService, TaskBroker taskBroker, PasswordTokenService tokenService) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.tokenService = tokenService;
    }

    @RequestMapping(value = "fetch/{taskType}/{taskUid}/{userUid}/{token}", method = RequestMethod.GET)
    public ResponseEntity fetchMinimalTaskDetails(@PathVariable TaskType taskType,
                                                  @PathVariable String taskUid,
                                                  @PathVariable String userUid,
                                                  @PathVariable String token) {
        tokenService.validateEntityResponseCode(userUid, taskUid, token);
        final Task task = taskBroker.loadEntity(userUid, taskUid, taskType, TaskType.toClass(taskType));
        final String userResponse = taskBroker.fetchUserResponse(userUid, task);
        return ResponseEntity.ok(new TaskPublicMinimalDTO(task, userResponse));
    }

    @RequestMapping(value = "submit/{taskType}/{taskUid}/{userUid}/{token}", method = RequestMethod.GET)
    public ResponseEntity respondToTask(@PathVariable TaskType taskType,
                                        @PathVariable String taskUid,
                                        @PathVariable String userUid,
                                        @PathVariable String token,
                                        @RequestParam String response,
                                        HttpServletRequest request) {
        final String requestUserUid = getUserIdFromRequest(request);
        if (!StringUtils.isEmpty(requestUserUid) && !requestUserUid.equals(userUid)) {
            throw new AccessDeniedException("Error! Looks like spoofing attempt");
        }
        tokenService.validateEntityResponseCode(userUid, taskUid, token);
        log.info("submitting task response: type = {}, uid = {}, user = {}", taskType, taskUid, userUid);
        taskBroker.respondToTask(userUid, taskUid, taskType, response);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
