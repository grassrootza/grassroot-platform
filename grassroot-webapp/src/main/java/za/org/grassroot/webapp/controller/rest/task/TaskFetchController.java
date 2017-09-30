package za.org.grassroot.webapp.controller.rest.task;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

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
                                                                  @RequestBody
                                                                  @ApiParam(value = "Map of tasks the client knows about, with " +
                                                                          "task UID as key and epochMilli of last known change (on server) as value")
                                                                  Map<String, Long> knownTasks) {
        return ResponseEntity.ok(taskBroker.findNewlyChangedTasks(userUid, knownTasks));
    }

    @RequestMapping(value = "/specified/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the complete details of a list of tasks")
    public ResponseEntity<List<TaskDTO>> fetchSpecificTasks(@PathVariable String userUid,
                                                              @RequestBody Map<String, TaskType> taskUidsAndTypes) {
        return ResponseEntity.ok(taskBroker.fetchSpecifiedTasks(userUid, taskUidsAndTypes, TaskSortType.TIME_CREATED));
    }

    @Timed
    @RequestMapping(value = "/all/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Full task list for user", notes = "Fetches every task for a user, throughout their history",
            response = TaskDTO[].class)
    public ResponseEntity<ResponseWrapper> fetchAllUserTasks(@PathVariable String userUid,
                                                             @RequestParam TaskSortType taskSortType) {
        return RestUtil.okayResponseWithData(RestMessage.TASK_DETAILS,
                taskBroker.fetchAllUserTasksSorted(userUid, taskSortType));
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All tasks for a group", notes = "Fetch tasks for a group", response = ChangedSinceData.class)
    public ResponseEntity<ChangedSinceData<TaskDTO>> fetchUserGroupTasks(@PathVariable String userUid,
                                                                         @PathVariable String groupUid) {
        return ResponseEntity.ok(taskBroker.fetchGroupTasks(userUid, groupUid, null));
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "All updated tasks for a group", notes = "Fetches all tasks for the group since the last time " +
            "there was a change in any of them", response = TaskMinimalDTO.class)
    public ResponseEntity<List<TaskMinimalDTO>> fetchUpdatedGroupTasks(@PathVariable String userUid,
                                                                            @PathVariable String groupUid,
                                                                            @RequestBody
                                                                            @ApiParam(value = "Map of tasks for this group that the client knows about, with " +
                                                                                        "taskUID as key and epochMilli of last known change as value")
                                                                            Map<String, Long> knownTasks) {
        return ResponseEntity.ok(taskBroker.fetchNewlyChangedTasksForGroup(userUid, groupUid, knownTasks));
    }
}