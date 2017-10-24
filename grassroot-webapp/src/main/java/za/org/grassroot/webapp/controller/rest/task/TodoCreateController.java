package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.services.task.TodoBrokerNew;
import za.org.grassroot.services.task.TodoHelper;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.time.Instant;
import java.util.Set;

@Slf4j
@RestController @Grassroot2RestController
@Api("/api/task/create/todo")
@RequestMapping(value = "/api/task/create/todo")
public class TodoCreateController {

    private final TodoBrokerNew todoBrokerNew;
    private final UserManagementService userManager;

    @Autowired
    public TodoCreateController(TodoBrokerNew todoBrokerNew, UserManagementService userManager) {
        this.todoBrokerNew = todoBrokerNew;
        this.userManager = userManager;
    }

    /*
    Used when the thing 'to-do' is respond with some piece of needed information. Whatever the responses are will
    be tagged with the 'response tag'. Paradigmatic case is to request ID numbers (e.g., in filling out member data).
     */
    @RequestMapping(value = "/information/{userUid}/{parentType}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskFullDTO> createInformationRequestTodo(@PathVariable String userUid,
                                                                    @PathVariable String parentUid,
                                                                    @PathVariable JpaEntityType parentType,
                                                                    @RequestParam String description,
                                                                    @RequestParam String responseTag,
                                                                    @RequestParam long dueDateTime,
                                                                    @RequestParam(required = false) Set<String> assignedUids,
                                                                    @RequestParam(required = false) Set<String> mediaFileUids) {
        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.INFORMATION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .description(description)
                .responseTag(responseTag)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime)).build();

        if (assignedUids != null && !assignedUids.isEmpty()) {
            todoHelper.setAssignedMemberUids(assignedUids);
        }

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            todoHelper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(todoHelper);
    }

    /*
    Used when the thing 'to-do' is for one or more members of the group to perform some action, and for one or more
    other members to confirm they have done it. Paradigmatic case is X will visit school, and Y will say they have done.
    Note 1: allow for case when assigned is empty, which means that someone outside group has promised to the group that
    something will be done, and group will confirm (via confirmed members, or below). todo : consider another enum?
    Note 2: similarly, consider empty set to be 'anyone can confirm'
     */
    @RequestMapping(value = "/confirmation/{userUid}/{parentType}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskFullDTO> createConfirmationRequiredTodo(@PathVariable String userUid,
                                                                      @PathVariable String parentUid,
                                                                      @PathVariable JpaEntityType parentType,
                                                                      @RequestParam String description,
                                                                      @RequestParam long dueDateTime,
                                                                      @RequestParam boolean requireImages,
                                                                      @RequestParam Set<String> assignedMemberUids,
                                                                      @RequestParam Set<String> confirmingMemberUids,
                                                                      @RequestParam(required = false) Set<String> mediaFileUids) {
        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.CONFIRMATION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .description(description)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime))
                .assignedMemberUids(assignedMemberUids)
                .confirmingMemberUids(confirmingMemberUids).build();

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            todoHelper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(todoHelper);
    }

    /*
    Used for the simplest kind of 'to-do', which is just to say 'please do something by this time', with a reminder
    (or two, if paid for). This can be, for example, 'please listen to the radio at this time'. Don't bother with confirmations.
     */
    @RequestMapping(value = "/action/{userUid}/{parentType}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<TaskFullDTO> createActionRequiredTodo(@PathVariable String userUid,
                                                                @PathVariable String parentUid,
                                                                @PathVariable JpaEntityType parentType,
                                                                @RequestParam String description,
                                                                @RequestParam long dueDateTime,
                                                                @RequestParam boolean numberReminders,
                                                                @RequestParam(required = false) Set<String> assignedMemberUids,
                                                                @RequestParam(required = false) Set<String> mediaFileUids) {
        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.ACTION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .description(description)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime))
                .build();

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            todoHelper.setAssignedMemberUids(assignedMemberUids);
        }

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            todoHelper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(todoHelper);
    }

    private ResponseEntity<TaskFullDTO> handleCreationAndReturn(TodoHelper todoHelper) {
        final String createdTodoUid = todoBrokerNew.create(todoHelper);
        final Todo createdTodo = todoBrokerNew.load(createdTodoUid);

        // common handler will respond if throws no permission exception
        return ResponseEntity.ok(new TaskFullDTO(
                createdTodo,
                userManager.load(todoHelper.getUserUid()),
                createdTodo.getCreatedDateTime(),
                false));
    }

}
