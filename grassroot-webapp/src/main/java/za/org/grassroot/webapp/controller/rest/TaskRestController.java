package za.org.grassroot.webapp.controller.rest;

import edu.emory.mathcs.backport.java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.model.rest.TaskDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/03/17.
 */

@RestController
@RequestMapping(value = "/api/task")
public class TaskRestController {

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    LogBookService logBookService;

    @Autowired
    GroupBroker groupBroker;


    @RequestMapping(value = "/list/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getTasks(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                    @PathVariable("id") String uid) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Group group = groupBroker.load(uid);
        List<TaskDTO> tasks = getTasks(user, group);
        ResponseWrapper responseWrapper;
        if (tasks.isEmpty()) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.NO_GROUP_ACTIVITIES, RestStatus.SUCCESS);
        } else {
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.GROUP_ACTIVITIES, RestStatus.FAILURE, tasks);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    private List<TaskDTO> getTasks(User user, Group group) {

        Set<TaskDTO> taskSet = new HashSet<>();

        for (Event event : groupBroker.retrieveGroupEvents(group, null, null, null)) {
            EventLog eventLog = eventLogManagementService.getEventLogOfUser(event, user, EventLogType.EventRSVP);
            boolean hasResponded = eventLogManagementService.userRsvpForEvent(event, user);
            if (event.getEventStartDateTime() != null) {
                taskSet.add(new TaskDTO(event, eventLog, user, hasResponded));
            }
        }

        for (LogBook logBook : logBookService.getAllLogBookEntriesForGroup(group.getId())) {
            if (logBook.getCreatedByUser().equals(user)) {
                taskSet.add(new TaskDTO(logBook, user, user));
            } else {
                User creatingUser = logBook.getCreatedByUser();
                taskSet.add(new TaskDTO(logBook, user, creatingUser));
            }
        }

        List<TaskDTO> tasks = new ArrayList<>(taskSet);
        Collections.sort(tasks);
        return tasks;
    }




}
