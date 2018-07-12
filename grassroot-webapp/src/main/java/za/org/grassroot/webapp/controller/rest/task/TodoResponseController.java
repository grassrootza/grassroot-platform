package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/task/respond/todo") @Api("/v2/api/task/respond/todo")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class TodoResponseController extends BaseRestController {

    private final TodoBroker todoBroker;

    @Autowired
    public TodoResponseController(JwtService jwtService, UserManagementService userManagementService, TodoBroker todoBroker) {
        super(jwtService, userManagementService);
        this.todoBroker = todoBroker;
    }

    /*
    User is responding to a request for information to-do
    todo: validation
     */
    @RequestMapping(value = "information/{todoUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Record a user responding to a todo that requests information")
    public ResponseEntity recordUserInformationResponse(HttpServletRequest request,
                                                        @PathVariable String todoUid,
                                                        @RequestParam String response) {
        todoBroker.recordResponse(getUserIdFromRequest(request), todoUid, response, false);
        return ResponseEntity.ok(RestMessage.TODO_RESPONSE_RECORDED);
    }

    @ExceptionHandler(TodoTypeMismatchException.class)
    public ResponseEntity<ResponseWrapper> todoMismatchError() {
        return RestUtil.errorResponse(RestMessage.TODO_TYPE_MISMATCH);
    }
}
