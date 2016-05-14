package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.LogBookStatus;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.core.dto.TaskDTO;

import java.util.Collections;
import java.util.List;

/**
 * Created by paballo on 2016/03/17.
 */

@RestController
@RequestMapping(value = "/api/task")
public class TaskRestController {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private TaskBroker taskBroker;


    @RequestMapping(value = "/list/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getTasks(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                    @PathVariable("id") String groupUid) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        List<TaskDTO> tasks = taskBroker.fetchGroupTasks(user.getUid(), groupUid, false, LogBookStatus.BOTH);
        Collections.sort(tasks, Collections.reverseOrder()); // todo: double check this is right ordering
        ResponseWrapper responseWrapper;
        RestMessage message = (tasks.isEmpty()) ? RestMessage.NO_GROUP_ACTIVITIES : RestMessage.GROUP_ACTIVITIES;
        // note: should not return a failure or 404 on this if task list empty, should instead use the rest message to differentiate
        responseWrapper = new GenericResponseWrapper(HttpStatus.OK, message, RestStatus.SUCCESS, tasks);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

}
