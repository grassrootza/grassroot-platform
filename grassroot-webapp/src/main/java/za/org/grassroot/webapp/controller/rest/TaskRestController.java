package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.MembershipResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.core.dto.TaskDTO;

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

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private TaskBroker taskBroker;

	@Autowired
	private EventBroker eventBroker;

	@Autowired
	private TodoBroker todoBroker;

    // calling this "for parent" as in future will use it for any entity that can have sub-tasks, but for now just used for groups
	@RequestMapping(value = "/list/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.GET)
    public ResponseEntity<ChangedSinceData<TaskDTO>> getTasksForParent(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
																	   @PathVariable("parentUid") String parentUid,
																	   @RequestParam(name = "changedSince", required = false) Long changedSinceMillis) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
		Instant changedSince = changedSinceMillis == null ? null : Instant.ofEpochMilli(changedSinceMillis);
		ChangedSinceData<TaskDTO> changedSinceData = taskBroker.fetchGroupTasks(user.getUid(), parentUid, changedSince);
        return new ResponseEntity<>(changedSinceData, HttpStatus.OK);
    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getAllUpcomingTasksForUser(
			@PathVariable String phoneNumber,
			@PathVariable String code) {
        // todo: should really start storing UID on phone and pass that back here
        User user = userManagementService.loadOrSaveUser(phoneNumber);
		List<TaskDTO> tasks = taskBroker.fetchUpcomingUserTasks(user.getUid());
        Collections.sort(tasks, Collections.reverseOrder());
        RestMessage message = (tasks.isEmpty()) ? RestMessage.USER_HAS_NO_TASKS : RestMessage.USER_ACTIVITIES;
        ResponseWrapper wrapper = new GenericResponseWrapper(HttpStatus.OK, message, RestStatus.SUCCESS, tasks);
        return new ResponseEntity<>(wrapper, HttpStatus.OK);
    }

    @RequestMapping(value = "/fetch/{phoneNumber}/{code}/{taskUid}/{taskType}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchTask(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String taskUid, @PathVariable String taskType) {

        logger.info("fetching task, phoneNumber={}, taskType={}, taskUid={}", phoneNumber, taskType, taskUid);
        User user = userManagementService.findByInputNumber(phoneNumber);
        TaskType type = TaskType.valueOf(taskType);
        TaskDTO task = taskBroker.load(user.getUid(), taskUid, type);
        ResponseWrapper wrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.TASK_DETAILS, RestStatus.SUCCESS,
                                                             Collections.singletonList(task));
        return new ResponseEntity<>(wrapper, HttpStatus.OK);
    }

    @RequestMapping(value = "/assigned/{phoneNumber}/{code}/{taskUid}/{taskType}", method = RequestMethod.GET)
    public ResponseEntity<List<MembershipResponseWrapper>> fetchAssignedMembers(@PathVariable String phoneNumber, @PathVariable String code,
                                                           @PathVariable String taskUid, @PathVariable TaskType taskType) {
	    logger.info("fetching task assigned members, taskType = {}", taskType);
	    User user = userManagementService.findByInputNumber(phoneNumber);
	    // todo : think about whether we need permissions here
	    Set<User> users;
	    switch (taskType) {
		    case MEETING:
		    case VOTE:
			    Event event = eventBroker.load(taskUid);
			    users = event.getAssignedMembers();
			    break;
		    case TODO:
			    LogBook logBook = todoBroker.load(taskUid);
			    users = logBook.getAssignedMembers();
			    break;
		    default:
			    throw new UnsupportedOperationException("Error! Trying to fetch assigned members for unknown task type");
	    }

	    List<MembershipResponseWrapper> assignedMembers = new ArrayList<>();
	    for(User u: users){
		    assignedMembers.add(new MembershipResponseWrapper(u));
	    }

	    return new ResponseEntity<>(assignedMembers, HttpStatus.OK);
    }


}
