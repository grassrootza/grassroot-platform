package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class LogBookRestController {

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    LogBookBroker logBookBroker;

    @RequestMapping(value ="/complete/{phoneNumber}/{code}/{id}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber,
                                                       @PathVariable("code") String code, @PathVariable("id") String id) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        LogBook logBook = logBookBroker.load(id);

        ResponseWrapper responseWrapper;
        if(!logBook.isCompleted()){
            logBookBroker.complete(user.getUid(), id, LocalDateTime.now(), null); // todo: watch timezones on this
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.TODO_SET_COMPLETED, RestStatus.SUCCESS);
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.TODO_ALREADY_COMPLETED, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }


}
