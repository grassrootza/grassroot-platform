package za.org.grassroot.webapp.controller.rest;

import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.LocalDateTimePropertyEditor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class LogBookRestController {

    private static final Logger log = LoggerFactory.getLogger(LogBookRestController.class);

    // todo : move a bunch of this to a superclass
    @InitBinder
    public void initBinder(ServletRequestDataBinder binder) {
        binder.registerCustomEditor(LocalDateTime.class, new LocalDateTimePropertyEditor(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private LogBookBroker logBookBroker;

    @Autowired
    private TaskBroker taskBroker;

    @RequestMapping(value ="/complete/{phoneNumber}/{code}/{id}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber,
                                                       @PathVariable("code") String code, @PathVariable("id") String id) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        LogBook logBook = logBookBroker.load(id);

        ResponseWrapper responseWrapper;
        if(!logBook.isCompleted()){
            logBookBroker.complete(user.getUid(), id, LocalDateTime.now(), null); // todo: watch timezones on this
            TaskDTO updatedTask = taskBroker.load(user.getUid(), id, TaskType.TODO);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.TODO_SET_COMPLETED,
                                                         RestStatus.SUCCESS, Collections.singletonList(updatedTask));
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.TODO_ALREADY_COMPLETED, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createMeeting(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String parentUid,
                                                         @RequestParam String title,
                                                         @RequestParam String description,
                                                         @RequestParam LocalDateTime dueDate,
                                                         @RequestParam int reminderMinutes,
                                                         @RequestParam(value="members", required = false) Set<String> members) {

        log.info("REST : received logbook create request... with local date time: {}, and members: {}",
                 dueDate.toString(), members == null ? "null" : members.toString());

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Set<String> assignedMemberUids = (members == null) ? new HashSet<>() : members;

        // todo : handle negative reminderMinutes
        LogBook lb = logBookBroker.create(user.getUid(), JpaEntityType.GROUP, parentUid, title, dueDate, reminderMinutes,
                                          false, assignedMemberUids);
        TaskDTO createdTask = taskBroker.load(user.getUid(), lb.getUid(), TaskType.TODO);

        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.CREATED, RestMessage.TODO_CREATED,
                                                                     RestStatus.SUCCESS, Collections.singletonList(createdTask));

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


}
