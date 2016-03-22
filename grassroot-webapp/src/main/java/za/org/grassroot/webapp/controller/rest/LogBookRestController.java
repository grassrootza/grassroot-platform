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
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class LogBookRestController {

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    LogBookService logBookService;



    @RequestMapping(value ="/complete/do/{id}/{phoneNumber/{code}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code")
    String code, @PathVariable("id") String id){
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        LogBook logBook = logBookService.load(Long.parseLong(id));
        ResponseWrapper responseWrapper;
        if(!logBook.isCompleted()){
            logBook.setCompletedByUser(user);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.TODO_SET_COMPLETED, RestStatus.SUCCESS);
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.TODO_ALREADY_COMPLETED, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }


}
