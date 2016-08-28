package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

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
    public ResponseEntity<ResponseWrapper> registerForGcm(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code,
                                                          @RequestParam("registration_id") String registrationId) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        gcmService.registerUser(user, registrationId);
        userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.ANDROID_APP);
        return RestUtil.messageOkayResponse(RestMessage.REGISTERED_FOR_PUSH);

    }

    @RequestMapping(value = "/deregister/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> deRegister(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code)
            throws NoSuchProfileException{
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.SMS);
        return RestUtil.messageOkayResponse(RestMessage.DEREGISTERED_FOR_PUSH);
    }

    @ExceptionHandler(NoSuchProfileException.class)
    public ResponseEntity<ResponseWrapper>handleException(){
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.USER_NOT_REGISTERED_FOR_PUSH);
    }

}
