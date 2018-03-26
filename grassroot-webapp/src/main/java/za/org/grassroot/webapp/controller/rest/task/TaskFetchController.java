package za.org.grassroot.webapp.controller.rest.task;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.exception.FileCreationException;
import za.org.grassroot.webapp.model.rest.ImageRecordDTO;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Api("/api/task/fetch") @Slf4j
@RequestMapping(value = "/api/task/fetch")
public class TaskFetchController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final TaskImageBroker taskImageBroker;
    private final MemberDataExportBroker dataExportBroker;

    @Autowired
    public TaskFetchController(TaskBroker taskBroker, TaskImageBroker taskImageBroker,
                               JwtService jwtService, UserManagementService userManagementService, MemberDataExportBroker dataExportBroker) {
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.taskImageBroker = taskImageBroker;
        this.dataExportBroker = dataExportBroker;
    }

    @Timed
    @RequestMapping(value = "/updated", method = RequestMethod.POST)
    @ApiOperation(value = "All updated tasks", notes = "Fetches all the tasks updated since the timestamps in the map " +
            "(sends TaskMinimalDTO class, which is not appearing)")
    public ResponseEntity<List<TaskMinimalDTO>> fetchUpdatedTasks(@RequestBody Map<String, Long> knownTasks,
                                                                  HttpServletRequest request) {
        return ResponseEntity.ok(taskBroker.findNewlyChangedTasks(getUserIdFromRequest(request), knownTasks));
    }

    @RequestMapping(value = "/updated/group/{userUid}/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Updated tasks for group", notes = "Fetches all the tasks on this group updated since the " +
            "timestampts in the map")
    public ResponseEntity<List<TaskMinimalDTO>> fetchGroupUpdatedTasks(@PathVariable String userUid,
                                                                       @PathVariable String groupUid,
                                                                       @RequestBody Map<String, Long> knownTasks,
                                                                       HttpServletRequest request) {
        String loggedInUserUid = getUserIdFromRequest(request);
        if (userUid.equals(loggedInUserUid))
            return ResponseEntity.ok(taskBroker.fetchNewlyChangedTasksForGroup(userUid, groupUid, knownTasks));
        else
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
    }

    @RequestMapping(value = "/specified", method = RequestMethod.POST)
    @ApiOperation(value = "Full details on specified tasks", notes = "Fetches full details on tasks specified in the " +
            "map of tasks and their type")
    public ResponseEntity<List<TaskFullDTO>> fetchSpecificTasks(@RequestBody Map<String, TaskType> taskUidsAndTypes,
                                                                HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskBroker.fetchSpecifiedTasks(userUid, taskUidsAndTypes, TaskSortType.TIME_CREATED));
    }

    @RequestMapping(value = "/{taskType}/{taskUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Full details on a single, specified task")
    public ResponseEntity<TaskFullDTO> fetchTask(@PathVariable TaskType taskType, @PathVariable String taskUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskBroker.fetchSpecifiedTasks(userUid, Collections.singletonMap(taskUid, taskType),
                null).get(0));
    }

    @RequestMapping(value = "/meeting/rsvps/{taskUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the RSVPs for a meeting")
    public ResponseEntity<Map<String, String>> fetchMeetingRsvps(HttpServletRequest request, @PathVariable String taskUid) {
        return ResponseEntity.ok(taskBroker.loadResponses(getUserIdFromRequest(request), taskUid, TaskType.MEETING));
    }

    @RequestMapping(value = "/todo/responses/{taskUid}", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> fetchTodoResponses(HttpServletRequest request, @PathVariable String taskUid) {
        return ResponseEntity.ok(taskBroker.loadResponses(getUserIdFromRequest(request), taskUid, TaskType.TODO));
    }

    @RequestMapping(value = "/todo/download/{taskUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadTodoResponses(HttpServletRequest request, @PathVariable String taskUid) {
        try {
            Todo todo = taskBroker.loadEntity(getUserIdFromRequest(request), taskUid, TaskType.TODO, Todo.class);
            String fileName = todo.getName().replaceAll(" ", "_").toLowerCase() + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            XSSFWorkbook xls = dataExportBroker.exportTodoData(getUserIdFromRequest(request), taskUid);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xls.write(baos);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Could not generate todo spreadsheet", e);
            throw new FileCreationException();
        }
    }

    @Timed
    @RequestMapping(value = "/all/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All a users' tasks", notes = "Fetches full details on all a users' tasks, with an option parameter" +
            " for sort type (defaults to sorting by last change")
    public ResponseEntity<List<TaskFullDTO>> fetchAllUserTasks(@PathVariable String userUid,
                                                               @RequestParam(required = false) TaskSortType taskSortType,
                                                               HttpServletRequest request) {

        String loggedInUserUid = getUserIdFromRequest(request);
        if (userUid.equals(loggedInUserUid)) {
            List<TaskFullDTO> tasks = taskBroker.fetchAllUserTasksSorted(userUid, taskSortType);
            return ResponseEntity.ok(tasks);
        } else
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
    }

    @Timed
    @RequestMapping(value = "/upcoming/user", method = RequestMethod.GET)
    @ApiOperation(value = "Upcoming user tasks", notes = "Fetches full details on upcoming user's tasks, with an option parameter" +
            " for sort type (defaults to sorting by last change")
    public ResponseEntity<List<TaskFullDTO>> fetchUpcomingUserTasks(@RequestParam(required = false) TaskSortType taskSortType,
                                                                    HttpServletRequest request) {
        List<TaskFullDTO> tasks = taskBroker.fetchUpcomingUserTasksFull(getUserIdFromRequest(request));
        log.info("found {} tasks for user", tasks.size());
        return ResponseEntity.ok(tasks);
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All tasks for a group", notes = "Fetch tasks for a group", response = ChangedSinceData.class)
    public ResponseEntity<ChangedSinceData<TaskDTO>> fetchUserGroupTasks(@PathVariable String userUid,
                                                                         @PathVariable String groupUid,
                                                                         @RequestParam(required = false) Long changedSinceMillis,
                                                                         HttpServletRequest request) {
        String loggedInUserUid = getUserIdFromRequest(request);
        if (userUid.equals(loggedInUserUid)) {
            ChangedSinceData<TaskDTO> tasks = taskBroker.fetchGroupTasks(userUid, groupUid,
                    changedSinceMillis == null || changedSinceMillis == 0 ? null : Instant.ofEpochMilli(changedSinceMillis));
            log.info("returning tasks: {}", tasks);
            return ResponseEntity.ok(tasks);
        } else
            return new ResponseEntity<>((ChangedSinceData<TaskDTO>) null, HttpStatus.FORBIDDEN);
    }

    @RequestMapping(value = "/posts/{taskType}/{taskUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch posts for a task", notes = "Fetch posts against a task, sorted by date created", response = ImageRecordDTO.class)
    public ResponseEntity<List<ImageRecordDTO>> fetchTaskPosts(@PathVariable TaskType taskType,
                                                               @PathVariable String taskUid,
                                                               HttpServletRequest request) {

        String loggedInUserUid = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskImageBroker.fetchTaskPosts(loggedInUserUid, taskUid, taskType).entrySet().stream()
                .map(entry -> new ImageRecordDTO(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ImageRecordDTO::getCreationTime, Comparator.reverseOrder()))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = "/image/{taskType}/{taskUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch primary image for a task")
    public ResponseEntity<String> fetchTaskImage(@PathVariable TaskType taskType, @PathVariable String taskUid,
                                                 HttpServletRequest request) {
        final String imageKey = taskImageBroker.fetchImageKeyForCreationImage(getUserIdFromRequest(request), taskUid, taskType);
        return StringUtils.isEmpty(imageKey) ? ResponseEntity.ok().build() : ResponseEntity.ok(imageKey);
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
