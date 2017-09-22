package za.org.grassroot.webapp.controller.rest.task;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Map;

@RestController
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
}
