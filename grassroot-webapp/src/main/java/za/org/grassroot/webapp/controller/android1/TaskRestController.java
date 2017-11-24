package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.MembershipResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/03/17.
 */

@RestController
@RequestMapping(value = "/api/task", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskRestController {

    private static final Logger logger = LoggerFactory.getLogger(TaskRestController.class);

    private final UserManagementService userManagementService;
    private final TaskBroker taskBroker;
	private final EventBroker eventBroker;
	private final TodoBroker todoBroker;
	private final PermissionBroker permissionBroker;

	@Autowired
	public TaskRestController(UserManagementService userManagementService, TaskBroker taskBroker, EventBroker eventBroker, TodoBroker todoBroker, PermissionBroker permissionBroker) {
		this.userManagementService = userManagementService;
		this.taskBroker = taskBroker;
		this.eventBroker = eventBroker;
		this.todoBroker = todoBroker;
		this.permissionBroker = permissionBroker;
	}

	// calling this "for parent" as in future will use it for any entity that can have sub-tasks, but for now just used for groups
	@RequestMapping(value = "/list/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.GET)
    public ResponseEntity<ChangedSinceData<TaskDTO>> getTasksForParent(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
																	   @PathVariable("parentUid") String parentUid,
																	   @RequestParam(name = "changedSince", required = false) Long changedSinceMillis) {

        User user = userManagementService.findByInputNumber(phoneNumber);
		Instant changedSince = changedSinceMillis == null ? null : Instant.ofEpochMilli(changedSinceMillis);
		try {
			ChangedSinceData<TaskDTO> changedSinceData = taskBroker.fetchGroupTasks(user.getUid(), parentUid, changedSince);
			return new ResponseEntity<>(changedSinceData, HttpStatus.OK);
		} catch (AccessDeniedException e) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getAllUpcomingTasksForUser(
			@PathVariable String phoneNumber,
			@PathVariable String code) {
        User user = userManagementService.findByInputNumber(phoneNumber);
		List<TaskDTO> tasks = taskBroker.fetchUpcomingUserTasks(user.getUid());
        tasks.sort(Collections.reverseOrder());
	    logger.info("returning tasks for user : {}", tasks.toString());
        RestMessage message = (tasks.isEmpty()) ? RestMessage.USER_HAS_NO_TASKS : RestMessage.USER_ACTIVITIES;
        return RestUtil.okayResponseWithData(message, tasks);
    }

	@RequestMapping(value = "/list/since/{phoneNumber}/{code}", method = RequestMethod.GET)
	public ResponseEntity<ChangedSinceData<TaskDTO>> getUpcomingTasksAndCancelledForUser(@PathVariable String phoneNumber,
	                                                                                     @PathVariable String code,
	                                                                                     @RequestParam(name = "changedSince", required = false) Long changedSinceMillis) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		Instant changedSince = changedSinceMillis == null ? null : Instant.ofEpochMilli(changedSinceMillis);
		ChangedSinceData<TaskDTO> changedSinceData = taskBroker.fetchUpcomingTasksAndCancelled(user.getUid(), changedSince);
		return new ResponseEntity<>(changedSinceData, HttpStatus.OK);
	}


    @RequestMapping(value = "/fetch/{phoneNumber}/{code}/{taskUid}/{taskType}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchTask(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String taskUid, @PathVariable String taskType) {

        logger.info("fetching task, phoneNumber={}, taskType={}, taskUid={}", phoneNumber, taskType, taskUid);
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
	        TaskType type = TaskType.valueOf(taskType);
	        TaskDTO task = taskBroker.load(user.getUid(), taskUid, type);
	        return RestUtil.okayResponseWithData(RestMessage.TASK_DETAILS, Collections.singletonList(task));
        } catch (AccessDeniedException e) {
	        return RestUtil.accessDeniedResponse();
        } catch (NullPointerException e) {
	        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TASK_NOT_FOUND);
        }
    }

	@SuppressWarnings("unchecked") // the getAssignedMembers has a strange behavior on type check warnings, hence suppressing
    @RequestMapping(value = "/assigned/{phoneNumber}/{code}/{taskUid}/{taskType}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchAssignedMembers(@PathVariable String phoneNumber, @PathVariable String code,
																@PathVariable String taskUid, @PathVariable TaskType taskType) {
	    logger.info("fetching task assigned members, taskType = {}", taskType);
	    User requestingUser = userManagementService.findByInputNumber(phoneNumber);

	    Task task;
	    Set<User> users;

	    switch (taskType) {
		    case MEETING:
		    case VOTE:
			    Event event = eventBroker.load(taskUid);
			    users = event.getAssignedMembers();
			    task = event;
			    break;
		    case TODO:
			    Todo todo = todoBroker.load(taskUid);
			    users = todo.getAssignedMembers();
			    task = todo;
			    break;
		    default:
			    logger.error("Error! Trying to fetch assigned members for unknown task type");
			    return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_ENTITY_TYPE);
	    }

	    List<MembershipResponseWrapper> assignedMembers = new ArrayList<>();

	    if (!users.contains(requestingUser)) {
		    try {
			    permissionBroker.validateGroupPermission(requestingUser, task.getAncestorGroup(), null);
		    } catch (AccessDeniedException e) {
			    return RestUtil.accessDeniedResponse();
		    }
	    } else {
		    // so the calling user is returned at the top of the list and is not duplicated
		    users.remove(requestingUser);
		    assignedMembers.add(new MembershipResponseWrapper(requestingUser));
	    }

	    users.stream()
			    .sorted((u1, u2) -> u1.nameToDisplay().compareTo(u2.nameToDisplay()))
			    .forEach(u -> assignedMembers.add(new MembershipResponseWrapper(u)));

	    return RestUtil.okayResponseWithData(RestMessage.GROUP_MEMBERS, assignedMembers);
    }


}
