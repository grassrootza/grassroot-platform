package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.services.user.GcmRegistrationBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by paballo on 2016/04/07.
 */

@RestController
@RequestMapping(value = "/api/gcm")
public class GcmRestController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(GcmRestController.class);

    private final UserManagementService userManagementService;
    private final GcmRegistrationBroker gcmRegistrationBroker;

    @Autowired
    public GcmRestController(UserManagementService userManagementService, GcmRegistrationBroker gcmRegistrationBroker, JwtService jwtService) {
        super(jwtService, userManagementService);
        this.userManagementService = userManagementService;
        this.gcmRegistrationBroker = gcmRegistrationBroker;
    }


    @RequestMapping(value = "/register")
    public ResponseEntity<Boolean> registerForGcm(@RequestParam String gcmToken, HttpServletRequest request) {
        User user = getUserFromRequest(request);
        if (user != null) {
            gcmRegistrationBroker.registerUser(user, gcmToken);
            userManagementService.setMessagingPreference(user.getUid(), DeliveryRoute.ANDROID_APP);
            return new ResponseEntity<>(Boolean.TRUE, HttpStatus.OK);
        }
        return new ResponseEntity<>(Boolean.FALSE, HttpStatus.OK);
    }

    @RequestMapping(value = "/deregister/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> deRegister(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code)
            throws NoSuchProfileException{
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.setMessagingPreference(user.getUid(), DeliveryRoute.SMS);
        return RestUtil.messageOkayResponse(RestMessage.DEREGISTERED_FOR_PUSH);
    }



    @ExceptionHandler(NoSuchProfileException.class)
    public ResponseEntity<ResponseWrapper>handleException(){
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.USER_NOT_REGISTERED_FOR_PUSH);
    }

}
