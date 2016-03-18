package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.LogBookDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/logbook")
public class LogBookRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    LogBookService logBookService;



    @RequestMapping(value ="/complete/do/{id}/{phoneNumber/{code}", method =  RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> setComplete(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code")
    String code, @PathVariable("id") String id){
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        LogBook logBook = logBookService.load(Long.parseLong(id));
        logBook.setCompletedByUserId(user.getId());
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.SET_COMPLETED, RestStatus.SUCCESS);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));


    }





}
