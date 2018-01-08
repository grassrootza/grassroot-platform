package za.org.grassroot.webapp.controller.rest.task;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoHelper;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;

@Slf4j
@RestController @Grassroot2RestController
@Api("/api/task/create/todo")
@RequestMapping(value = "/api/task/create/todo")
public class TodoCreateController extends BaseRestController {

    private final TodoBroker todoBroker;
    private final UserManagementService userManager;

    @Autowired
    public TodoCreateController(JwtService jwtService, TodoBroker todoBroker, UserManagementService userManager) {
        super(jwtService, userManager);
        this.todoBroker = todoBroker;
        this.userManager = userManager;
    }

    /*
    Used when the thing 'to-do' is respond with some piece of needed information. Whatever the responses are will
    be tagged with the 'response tag'. Paradigmatic case is to request ID numbers (e.g., in filling out member data).
     */
    @RequestMapping(value = "/information/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a todo that requests information from members, by a given date, and which tags their response " +
            "with the response tag. Optional to assign or request only some members, or to add media files")
    public ResponseEntity<TaskFullDTO> createInformationRequestTodo(HttpServletRequest request,
                                                                    @PathVariable String parentUid,
                                                                    @PathVariable JpaEntityType parentType,
                                                                    @RequestParam String subject,
                                                                    @RequestParam String responseTag,
                                                                    @RequestParam long dueDateTime,
                                                                    @RequestParam(required = false) Set<String> assignedUids,
                                                                    @RequestParam(required = false) Set<String> mediaFileUids) {
        String userUid = getUserIdFromRequest(request);

        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.INFORMATION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .subject(subject)
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
    something will be done, and group will confirm (via confirmed members, or below).
    Note 2: similarly, consider empty set to be 'anyone can confirm'
     */
    @RequestMapping(value = "/confirmation/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a todo that requires a confirmation it's been done, by some set of members, and that " +
            "may or may not recur (at an interval given in milliseconds). Option is to require confirmations to include images")
    public ResponseEntity<TaskFullDTO> createConfirmationRequiredTodo(HttpServletRequest request,
                                                                      @PathVariable String parentUid,
                                                                      @PathVariable JpaEntityType parentType,
                                                                      @RequestParam String subject,
                                                                      @RequestParam long dueDateTime,
                                                                      @RequestParam boolean requireImages,
                                                                      @RequestParam Set<String> assignedMemberUids,
                                                                      @RequestParam Set<String> confirmingMemberUids,
                                                                      @RequestParam boolean recurring,
                                                                      @RequestParam(required = false) Long recurringPeriodMillis,
                                                                      @RequestParam(required = false) Set<String> mediaFileUids) {

        String userUid = getUserIdFromRequest(request);

        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.VALIDATION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .subject(subject)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime))
                .assignedMemberUids(assignedMemberUids)
                .confirmingMemberUids(confirmingMemberUids)
                .recurring(recurring)
                .recurringPeriodMillis(recurringPeriodMillis)
                .requireImagesForConfirm(requireImages)
                .build();

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            todoHelper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(todoHelper);
    }

    /*
    Used for the simplest kind of 'to-do', which is just to say 'please do something by this time', with a reminder
    (or two, if paid for). This can be, for example, 'please listen to the radio at this time'. Don't bother with confirmations.
     */
    @RequestMapping(value = "/action/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a simple to-do that just does that - tells the relevant members that something has to " +
            "be done by a given time, with options for recurrence (recurring period given in milliseconds)")
    public ResponseEntity<TaskFullDTO> createActionRequiredTodo(HttpServletRequest request,
                                                                @PathVariable String parentUid,
                                                                @PathVariable JpaEntityType parentType,
                                                                @RequestParam String subject,
                                                                @RequestParam long dueDateTime,
                                                                @RequestParam boolean recurring,
                                                                @RequestParam(required = false) Long recurringPeriodMillis,
                                                                @RequestParam(required = false) Set<String> assignedMemberUids,
                                                                @RequestParam(required = false) Set<String> mediaFileUids) {
        String userUid = getUserIdFromRequest(request);


        TodoHelper todoHelper = TodoHelper.builder()
                .todoType(TodoType.ACTION_REQUIRED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .subject(subject)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime))
                .recurring(recurring)
                .recurringPeriodMillis(recurringPeriodMillis)
                .build();

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            todoHelper.setAssignedMemberUids(assignedMemberUids);
        }

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            todoHelper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(todoHelper);
    }

    /*
    To-do that needs volunteers ...
     */
    @RequestMapping(value = "/volunteer/{parentType}/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a todo that requests volunteers, and notifies the creator when someone replies yes")
    public ResponseEntity<TaskFullDTO> createActionRequiredTodo(HttpServletRequest request,
                                                                @PathVariable String parentUid,
                                                                @PathVariable JpaEntityType parentType,
                                                                @RequestParam String subject,
                                                                @RequestParam long dueDateTime,
                                                                @RequestParam(required = false) Set<String> assignedMemberUids,
                                                                @RequestParam(required = false) Set<String> mediaFileUids) {

        String userUid = getUserIdFromRequest(request);

        TodoHelper helper = TodoHelper.builder()
                .todoType(TodoType.VOLUNTEERS_NEEDED)
                .parentType(parentType)
                .userUid(userUid)
                .parentUid(parentUid)
                .subject(subject)
                .dueDateTime(Instant.ofEpochMilli(dueDateTime))
                .build();

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            helper.setAssignedMemberUids(assignedMemberUids);
        }

        if (mediaFileUids != null && !mediaFileUids.isEmpty()) {
            helper.setMediaFileUids(mediaFileUids);
        }

        return handleCreationAndReturn(helper);
    }

    private ResponseEntity<TaskFullDTO> handleCreationAndReturn(TodoHelper todoHelper) {
        final String createdTodoUid = todoBroker.create(todoHelper);
        final Todo createdTodo = todoBroker.load(createdTodoUid);

        // common handler will respond if throws no permission exception
        return ResponseEntity.ok(new TaskFullDTO(
                createdTodo,
                userManager.load(todoHelper.getUserUid()),
                createdTodo.getCreatedDateTime(),
                null));
    }

}
