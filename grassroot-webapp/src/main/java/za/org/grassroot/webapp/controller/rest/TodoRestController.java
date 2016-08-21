package za.org.grassroot.webapp.controller.rest;

import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.services.TodoBroker;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.MembershipResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class TodoRestController {

    private static final Logger log = LoggerFactory.getLogger(TodoRestController.class);

    // todo : move a bunch of this to a superclass
    @InitBinder
    public void initBinder(ServletRequestDataBinder binder) {
        binder.registerCustomEditor(LocalDateTime.class, new LocalDateTimePropertyEditor(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @RequestMapping(value ="/complete/{phoneNumber}/{code}/{id}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber,
                                                       @PathVariable("code") String code, @PathVariable("id") String id) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Todo todo = todoBroker.load(id);

        ResponseWrapper responseWrapper;
        // todo : rather check for whether user has set this completed
        if (!todo.isCompleted()){
            todoBroker.confirmCompletion(user.getUid(), id, LocalDateTime.now()); // todo: watch timezones on this
            TaskDTO updatedTask = taskBroker.load(user.getUid(), id, TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_SET_COMPLETED, Collections.singletonList(updatedTask));
        } else {
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.TODO_ALREADY_COMPLETED);
        }
    }

    @RequestMapping(value = "/assigned/{phoneNumber}/{code}/{uid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getAssignedMemberList(String phoneNumber, String code,@PathVariable("uid") String uid){

        User callingUser = userManagementService.findByInputNumber(phoneNumber);
        Todo todo = todoBroker.load(uid);

        if (!todo.getAssignedMembers().contains(callingUser)) {
            try {
                permissionBroker.validateGroupPermission(callingUser, todo.getAncestorGroup(), null); // i.e., not in ancestor group ... but maybe change in future
            } catch (AccessDeniedException e) {
                return RestUtil.accessDeniedResponse();
            }
        }

        Set<User> users = todo.isAllGroupMembersAssigned() ? new HashSet<>() : todo.getAssignedMembers();
        List<MembershipResponseWrapper> assignedMembers = new ArrayList<>();
        for(User user: users) {
            assignedMembers.add(new MembershipResponseWrapper(user));
        }
        return RestUtil.okayResponseWithData(RestMessage.GROUP_MEMBERS, assignedMembers);

    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createLogbook(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String parentUid,
                                                         @RequestParam String title,
                                                         @RequestParam String description,
                                                         @RequestParam LocalDateTime dueDate,
                                                         @RequestParam int reminderMinutes,
                                                         @RequestParam(value="members", required = false) Set<String> members) {

        log.info("REST : received logbook create request... with local date time: {}, and members: {}",
                dueDate.toString(), members == null ? "null" : members.toString());

        User user = userManagementService.findByInputNumber(phoneNumber);
        Set<String> assignedMemberUids = (members == null) ? new HashSet<>() : members;

        // todo : handle negative reminderMinutes
        try {
            Todo lb = todoBroker.create(user.getUid(), JpaEntityType.GROUP, parentUid, title, dueDate, reminderMinutes,
                    false, assignedMemberUids);
            TaskDTO createdTask = taskBroker.load(user.getUid(), lb.getUid(), TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_CREATED, Collections.singletonList(createdTask));
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value ="update/{phoneNumber}/{code}/{uid}", method = RequestMethod.POST)
     public ResponseEntity<ResponseWrapper> updateLogbook(@PathVariable String phoneNumber, @PathVariable String code,
                                                          @PathVariable String uid,
                                                          @RequestParam String title,
                                                          @RequestParam LocalDateTime dueDate,
                                                          @RequestParam int reminderMinutes,
                                                          @RequestParam(value="members", required = false) Set<String> members) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        try {
            todoBroker.update(user.getUid(), uid, title, dueDate, reminderMinutes, members);
            TaskDTO taskDTO = taskBroker.load(user.getUid(), uid, TaskType.TODO);
            return RestUtil.okayResponseWithData(RestMessage.TODO_UPDATED, taskDTO);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

}
