package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping(value = { "/api/todo", "/api/logbook" })
public class TodoRestController {

    private static final Logger log = LoggerFactory.getLogger(TodoRestController.class);

    private final UserManagementService userManagementService;

    private final TodoBroker todoBroker;

    private final TaskBroker taskBroker;

    @Autowired
    public TodoRestController(UserManagementService userManagementService, TodoBroker todoBroker, TaskBroker taskBroker) {
        this.userManagementService = userManagementService;
        this.todoBroker = todoBroker;
        this.taskBroker = taskBroker;
    }

    @InitBinder
    public void initBinder(ServletRequestDataBinder binder) {
        binder.registerCustomEditor(LocalDateTime.class, new LocalDateTimePropertyEditor(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @RequestMapping(value ="/complete/{phoneNumber}/{code}/{id}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber,
                                                       @PathVariable("code") String code, @PathVariable("id") String id) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Todo todo = todoBroker.load(id);

        if (!todo.isCompletionConfirmedByMember(user)) {
            todoBroker.confirmCompletion(user.getUid(), id, TodoCompletionConfirmType.COMPLETED, LocalDateTime.now());
            TaskDTO updatedTask = taskBroker.load(user.getUid(), id, TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_SET_COMPLETED, Collections.singletonList(updatedTask));
        } else {
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.TODO_ALREADY_COMPLETED);
        }
    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createTodo(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String parentUid,
                                                         @RequestParam String title,
                                                         @RequestParam String description,
                                                         @RequestParam LocalDateTime dueDate,
                                                         @RequestParam int reminderMinutes,
                                                         @RequestParam(value="members", required = false) Set<String> members) {

        log.info("REST : received logbook create request... with local date time: {}, and members: {}",
                dueDate.toString(), members == null ? "null" : members.toString());

        User user = userManagementService.findByInputNumber(phoneNumber);
        Set<String> assignedMemberUids = (members == null) ? new HashSet<>() : members;

        try {
            Todo todo = todoBroker.create(user.getUid(), JpaEntityType.GROUP, parentUid, title, dueDate, reminderMinutes,
                    false, assignedMemberUids);
            TaskDTO createdTask = taskBroker.load(user.getUid(), todo.getUid(), TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_CREATED, Collections.singletonList(createdTask));
        } catch(AccountLimitExceededException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TODO_LIMIT_REACHED);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/update/{phoneNumber}/{code}/{taskUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateTodo(@PathVariable String phoneNumber, @PathVariable String code,
                                                          @PathVariable String taskUid,
                                                          @RequestParam String title,
                                                          @RequestParam String description,
                                                          @RequestParam LocalDateTime dueDate,
                                                          @RequestParam(value="reminderMinutes", required = false) Integer reminderMinutes,
                                                          @RequestParam(value="members", required = false) Set<String> members) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            todoBroker.update(user.getUid(), taskUid, title, description, dueDate, reminderMinutes, members);
            TaskDTO taskDTO = taskBroker.load(user.getUid(), taskUid, TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_UPDATED, Collections.singletonList(taskDTO));
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelTodo(@PathVariable String phoneNumber, @PathVariable String code,
                                                      @RequestParam String todoUid) {

        try {
            User user = userManagementService.findByInputNumber(phoneNumber);
            todoBroker.cancel(user.getUid(), todoUid);
            return RestUtil.messageOkayResponse(RestMessage.TODO_CANCELLED);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

}