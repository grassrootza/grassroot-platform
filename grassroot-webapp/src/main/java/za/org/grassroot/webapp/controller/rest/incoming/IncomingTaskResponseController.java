package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;

import javax.servlet.http.HttpServletRequest;

@Api("/api/inbound/respond/")
@RestController @Slf4j
@RequestMapping("/api/inbound/respond/")
public class IncomingTaskResponseController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final PasswordTokenService tokenService;

    public IncomingTaskResponseController(JwtService jwtService, UserManagementService userManagementService, TaskBroker taskBroker, PasswordTokenService tokenService) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.tokenService = tokenService;
    }

    @RequestMapping(value = "{taskType}/{taskUid}/{userUid}/{token}", method = RequestMethod.GET)
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
        taskBroker.respondToTask(userUid, taskUid, taskType, response);
        return ResponseEntity.ok().build();
    }
}
