package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.repository.LogBookLogRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.services.*;
import za.org.grassroot.services.util.CacheUtilService;

import java.util.Map;
import java.util.Set;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = LoggerFactory.getLogger(EventNotificationConsumer.class);

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private MessageSendingService messageSendingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventBroker eventBroker;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    CacheUtilService cacheUtilService;

    @Autowired
    LogBookRepository logBookRepository;

    @Autowired
    LogBookLogRepository logBookLogRepository;

    @Autowired
    AccountManagementService accountManagementService;

    @Autowired
    PasswordTokenService passwordTokenService;

    @Autowired
    GcmService gcmService;

    @Autowired
    MeetingRepository meetingRepository;

    /*
    change this to add messages or stop messages altogether by leaving it empty.
     */
    private final String[] welcomeMessages = new String[] {
            "sms.welcome.1",
            "sms.welcome.2",
            "sms.welcome.3"
    };

    /*
    As far as I can tell, this isn't used anywhere (instead the methods loop in here and clear the cache themselves.
    Since it's the only reason for cache manager wiring user manager, am going to comment out until / unless needed again.
     */
    /*@JmsListener(destination = "clear-groupcache", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void clearGroupCache(EventDTO event) {
        log.info("clearGroupCache...event.id..." + event.getId());
        cacheUtilService.clearCacheForAllUsersInGroup(event);


    }*/

    @JmsListener(destination = "generic-async", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void genericAsyncProcessor(GenericAsyncDTO genericAsyncDTO) {
        log.info("genericAsyncProcessor...identifier..." + genericAsyncDTO.getIdentifier() + "...object..." + genericAsyncDTO.getObject().toString());
        switch (genericAsyncDTO.getIdentifier()) {
            case "welcome-messages":
                sendWelcomeMessages((UserDTO) genericAsyncDTO.getObject());
                break;
            default:
                log.info("genericAsyncProcessor NO implementation for..." + genericAsyncDTO.getIdentifier());
        }

    }

    @JmsListener(destination = "processing-failure", containerFactory = "messagingJmsContainerFactory", concurrency = "1")
    public void sendReplyProcessingFailureNotification(User user){
        sendCouldNotProcessReply(user);
    }

    @JmsListener(destination = "send-verification", containerFactory = "messagingJmsContainerFactory", concurrency = "3")
    public void sendPhoneNumberVerificationCode(String phoneNumber){
        VerificationTokenCode token =  passwordTokenService.generateAndroidVerificationCode(phoneNumber);
        sendVerificationToken(token);

    }

    private void sendWelcomeMessages(UserDTO userDTO) {
        log.info("sendWelcomeMessages..." + userDTO + "...messages..." + welcomeMessages);
        for (String messageId : welcomeMessages) {
            String message = messageAssemblingService.createWelcomeMessage(messageId, userDTO);
            messageSendingService.sendMessage(message,userDTO.getPhoneNumber(),MessageProtocol.SMS);
        }
    }

    private void sendVerificationToken(VerificationTokenCode verificationTokenCode){
        log.info("sendingVerficationToken..."+verificationTokenCode.getUsername()+ "...token "+verificationTokenCode.getCode());
        String message =verificationTokenCode.getCode();
        messageSendingService.sendMessage(message,verificationTokenCode.getUsername(),MessageProtocol.SMS);
    }

    public void sendCouldNotProcessReply(User user){
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
    }

    private String getRegistrationId(User user){
        return gcmService.getGcmKey(user);
    }


}

