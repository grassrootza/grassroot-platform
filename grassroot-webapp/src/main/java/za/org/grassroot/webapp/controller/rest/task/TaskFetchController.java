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
import za.org.grassroot.core.enums.TaskType;

import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

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
    public ResponseEntity<ResponseWrapper> fetchUpdatedTasks(@PathVariable String userUid,
                                                             @RequestBody Map<String, Long> knownTasks) {
        return RestUtil.okayResponseWithData(RestMessage.TASK_FOUND,
                taskBroker.findNewlyChangedTasks(userUid, knownTasks));
    }

    @RequestMapping(value = "/specified/{userUid}")
    public ResponseEntity<ResponseWrapper> fetchSpecificTasks(@PathVariable String userUid,
                                                              @RequestBody Map<String, TaskType> taskUidsAndTypes) {
        return RestUtil.okayResponseWithData(RestMessage.TASK_DETAILS,
                taskBroker.fetchSpecifiedTasks(userUid, taskUidsAndTypes, TaskSortType.TIME_CREATED));
    }

    @Timed
    @RequestMapping(value = "/all/{userUid}")
    public ResponseEntity<ResponseWrapper> fetchAllUserTasks(@PathVariable String userUid,
                                                             @RequestParam TaskSortType taskSortType) {
        return RestUtil.okayResponseWithData(RestMessage.TASK_DETAILS,
                taskBroker.fetchAllUserTasksSorted(userUid, taskSortType));
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}")
    @ApiOperation(value = "All tasks for a group", notes = "Fetch tasks for a group", response = ChangedSinceData.class)
    public ResponseEntity<ResponseWrapper> fetchUserGroupTasks(@PathVariable String userUid,
                                                               @PathVariable String groupUid,
                                                               @RequestParam long changedSinceMillis) {
        return RestUtil.okayResponseWithData(RestMessage.TASK_DETAILS,
                taskBroker.fetchGroupTasks(userUid, groupUid,
                        changedSinceMillis == 0 ? null : Instant.ofEpochMilli(changedSinceMillis)));
    }
}
