package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.integration.GroupChatService;
import za.org.grassroot.integration.domain.GroupChatMessage;
import za.org.grassroot.integration.domain.RelayedChatMessage;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.xmpp.GcmService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.exception.NoSuchProfileException;
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

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GcmService gcmService;

    @Autowired
    private GroupChatService groupChatService;

    @RequestMapping(value = "/register/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> registerForGcm(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code,
                                                          @RequestParam("registration_id") String registrationId) {

        logger.info("Inside GCM registration ... for ID: {}", registrationId);
        User user = userManagementService.findByInputNumber(phoneNumber);
        gcmService.registerUser(user, registrationId);
        gcmService.refreshAllGroupTopicSubscriptions(user.getUid(), registrationId); // separate this call from above as can be _very_ long so needs to be async
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

    @RequestMapping(value = "/chat/send/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> relayChatMessage(@PathVariable String phoneNumber, @PathVariable String groupUid,
                                                            @RequestParam String messageText, @RequestParam String messageUid,
                                                            @RequestParam String gcmKey) {
        try {

            groupChatService.relayChatMessage(phoneNumber, groupUid, messageText, messageUid, gcmKey);
            return RestUtil.messageOkayResponse(RestMessage.CHAT_SENT);
        } catch (GroupChatSettingNotFoundException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.MESSAGE_SETTING_NOT_FOUND);
        }
    }

    @ExceptionHandler(NoSuchProfileException.class)
    public ResponseEntity<ResponseWrapper>handleException(){
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.USER_NOT_REGISTERED_FOR_PUSH);
    }

}
