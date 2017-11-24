package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@Slf4j
@RestController @Grassroot2RestController
@Api("/api/task/respond/todo")
@RequestMapping("/api/task/respond/todo")
public class TodoResponseController {

    private final TodoBroker todoBroker;

    @Autowired
    public TodoResponseController(TodoBroker todoBroker) {
        this.todoBroker = todoBroker;
    }

    /*
    User is responding to a request for information to-do
    todo: validation
     */
    @RequestMapping(value = "information/{todoUid}/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Record a user responding to a todo that requests information")
    public ResponseEntity recordUserInformationResponse(@PathVariable String todoUid,
                                                        @PathVariable String userUid,
                                                        @RequestParam String response) {
        todoBroker.recordResponse(userUid, todoUid, response, false);
        return ResponseEntity.ok(RestMessage.TODO_RESPONSE_RECORDED);
    }

    @ExceptionHandler(TodoTypeMismatchException.class)
    public ResponseEntity<ResponseWrapper> todoMismatchError() {
        return RestUtil.errorResponse(RestMessage.TODO_TYPE_MISMATCH);
    }
}
