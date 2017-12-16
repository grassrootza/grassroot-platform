package za.org.grassroot.webapp.controller.rest.task;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.model.rest.ImageRecordDTO;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Api("/api/task/fetch")
@RequestMapping(value = "/api/task/fetch")
public class TaskFetchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(TaskFetchController.class);

    private final TaskBroker taskBroker;
    private final TaskImageBroker taskImageBroker;

    @Autowired
    public TaskFetchController(TaskBroker taskBroker, TaskImageBroker taskImageBroker,
                               JwtService jwtService, UserManagementService userManagementService) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.taskImageBroker = taskImageBroker;
    }

    @Timed
    @RequestMapping(value = "/updated/{userUid}", method = RequestMethod.POST)
    @ApiOperation(value = "All updated tasks", notes = "Fetches all the tasks updated since the timestamps in the map " +
            "(sends TaskMinimalDTO class, which is not appearing)")
    public ResponseEntity<List<TaskMinimalDTO>> fetchUpdatedTasks(@PathVariable String userUid,
                                                                  @RequestBody Map<String, Long> knownTasks) {
        return ResponseEntity.ok(taskBroker.findNewlyChangedTasks(userUid, knownTasks));
    }

    @RequestMapping(value = "/updated/group/{userUid}/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Updated tasks for group", notes = "Fetches all the tasks on this group updated since the " +
            "timestampts in the map")
    public ResponseEntity<List<TaskMinimalDTO>> fetchGroupUpdatedTasks(@PathVariable String userUid,
                                                                       @PathVariable String groupUid,
                                                                       @RequestBody Map<String, Long> knownTasks) {
        return ResponseEntity.ok(taskBroker.fetchNewlyChangedTasksForGroup(userUid, groupUid, knownTasks));
    }

    @Timed
    @RequestMapping(value = "/specified/{userUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Full details on specified task", notes = "Fetches full details on tasks specified in the " +
            "map of tasks and their type")
    public ResponseEntity<List<TaskFullDTO>> fetchSpecificTasks(@PathVariable String userUid,
                                                                @RequestBody Map<String, TaskType> taskUidsAndTypes) {
        return ResponseEntity.ok(taskBroker.fetchSpecifiedTasks(userUid, taskUidsAndTypes, TaskSortType.TIME_CREATED));
    }

    @Timed
    @RequestMapping(value = "/all/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All a users' tasks", notes = "Fetches full details on all a users' tasks, with an option parameter" +
            " for sort type (defaults to sorting by last change")
    public ResponseEntity<List<TaskFullDTO>> fetchAllUserTasks(@PathVariable String userUid,
                                                             @RequestParam(required = false) TaskSortType taskSortType) {
        List<TaskFullDTO> tasks = taskBroker.fetchAllUserTasksSorted(userUid, taskSortType);
        return ResponseEntity.ok(tasks);
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All tasks for a group", notes = "Fetch tasks for a group", response = ChangedSinceData.class)
    public ResponseEntity<ChangedSinceData<TaskDTO>> fetchUserGroupTasks(@PathVariable String userUid,
                                                                         @PathVariable String groupUid,
                                                                         @RequestParam(required = false) long changedSinceMillis) {
        return ResponseEntity.ok(taskBroker.fetchGroupTasks(userUid, groupUid,
                        changedSinceMillis == 0 ? null : Instant.ofEpochMilli(changedSinceMillis)));
    }

    @RequestMapping(value = "/posts/{userUid}/{taskType}/{taskUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch posts for a task", notes = "Fetch posts against a task, sorted by date created", response = ImageRecordDTO.class)
    public ResponseEntity<List<ImageRecordDTO>> fetchTaskPosts(@PathVariable String userUid,
                                                               @PathVariable TaskType taskType,
                                                               @PathVariable String taskUid) {
        return ResponseEntity.ok(taskImageBroker.fetchTaskPosts(userUid, taskUid, taskType).entrySet().stream()
                .map(entry -> new ImageRecordDTO(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ImageRecordDTO::getCreationTime, Comparator.reverseOrder()))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = "/upcoming/group/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All tasks for a group", notes = "Fetch tasks for a group", response = ChangedSinceData.class)
    public ResponseEntity<List<TaskFullDTO>> fetchUserGroupTasks(@PathVariable String groupUid, HttpServletRequest request) {

        String userId = getUserIdFromRequest(request);
        if (userId != null) {
            List<TaskFullDTO> tasks = taskBroker.fetchUpcomingGroupTasks(userId, groupUid);
            return ResponseEntity.ok(tasks);
        } else {
            return new ResponseEntity<>((List<TaskFullDTO>) null, HttpStatus.UNAUTHORIZED);
        }
    }


}
