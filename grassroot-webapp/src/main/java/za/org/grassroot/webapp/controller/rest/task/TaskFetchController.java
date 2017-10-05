package za.org.grassroot.webapp.controller.rest.task;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.enums.TaskSortType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Api("/api/task/fetch")
@RequestMapping(value = "/api/task/fetch")
public class TaskFetchController {

    private static final Logger logger = LoggerFactory.getLogger(TaskFetchController.class);

    private final TaskBroker taskBroker;

    @Autowired
    public TaskFetchController(TaskBroker taskBroker) {
        this.taskBroker = taskBroker;
    }

    @Timed
    @RequestMapping(value = "/updated/{userUid}", method = RequestMethod.POST)
    @ApiOperation(value = "All updated tasks", notes = "Fetches all the tasks updated since the timestamps in the map " +
            "(sends TaskMinimalDTO class, which is not appearing)")
    public ResponseEntity<List<TaskMinimalDTO>> fetchUpdatedTasks(@PathVariable String userUid,
                                                                  @RequestBody Map<String, Long> knownTasks) {
        return ResponseEntity.ok(taskBroker.findNewlyChangedTasks(userUid, knownTasks));
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
}
