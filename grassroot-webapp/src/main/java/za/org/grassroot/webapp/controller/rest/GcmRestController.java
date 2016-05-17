package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

/**
 * Created by paballo on 2016/04/07.
 */

@RestController
@RequestMapping(value = "/api/gcm")
public class GcmRestController {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GcmService gcmService;

    @RequestMapping(value = "/register/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable("phoneNumber") String phoneNumber,
                                                       @PathVariable("code") String code,
                                                       @RequestParam("registration_id") String registrationId) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        gcmService.registerUser(user, registrationId);

        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.REGISTERED_FOR_PUSH, RestStatus.SUCCESS),
                HttpStatus.CREATED);

    }

    @RequestMapping(value = "/deregister/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> deRegister(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code) throws NoSuchProfileException{
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        userManagementService.deleteAndroidUserProfile(user);
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.DEREGISTERED_FOR_PUSH, RestStatus.SUCCESS);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @ExceptionHandler(NoSuchProfileException.class)
    public ResponseEntity<ResponseWrapper>handleException(){
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.USER_NOT_REGISTERED_FOR_PUSH, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

}
