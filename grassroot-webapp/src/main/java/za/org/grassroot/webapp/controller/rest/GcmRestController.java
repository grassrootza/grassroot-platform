package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.services.user.GcmRegistrationBroker;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

/**
 * Created by paballo on 2016/04/07.
 */

@RestController
@RequestMapping(value = "/api/gcm")
public class GcmRestController {

    private static final Logger logger = LoggerFactory.getLogger(GcmRestController.class);

    private final UserManagementService userManagementService;
    private final GcmRegistrationBroker gcmRegistrationBroker;

    @Autowired
    public GcmRestController(UserManagementService userManagementService, GcmRegistrationBroker gcmRegistrationBroker) {
        this.userManagementService = userManagementService;
        this.gcmRegistrationBroker = gcmRegistrationBroker;
    }


    @RequestMapping(value = "/register/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> registerForGcm(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code,
                                                          @RequestParam("registration_id") String registrationId) {

        logger.info("Inside GCM registration ... for ID: {}", registrationId);
        User user = userManagementService.findByInputNumber(phoneNumber);
        gcmRegistrationBroker.registerUser(user, registrationId);
        gcmRegistrationBroker.refreshAllGroupTopicSubscriptions(user.getUid(), registrationId); // separate this call from above as can be _very_ long so needs to be async
        userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.ANDROID_APP);
        return RestUtil.messageOkayResponse(RestMessage.REGISTERED_FOR_PUSH);
    }

    @RequestMapping(value = "/deregister/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> deRegister(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code)
            throws NoSuchProfileException{
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.SMS);
        return RestUtil.messageOkayResponse(RestMessage.DEREGISTERED_FOR_PUSH);
    }



    @ExceptionHandler(NoSuchProfileException.class)
    public ResponseEntity<ResponseWrapper>handleException(){
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.USER_NOT_REGISTERED_FOR_PUSH);
    }

}
