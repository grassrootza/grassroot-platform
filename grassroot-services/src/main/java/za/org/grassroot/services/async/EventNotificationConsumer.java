package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.MessageProtocol;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.repository.LogBookLogRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.services.*;
import za.org.grassroot.services.util.CacheUtilService;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = LoggerFactory.getLogger(EventNotificationConsumer.class);

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private MessageSendingService messageSendingService;

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

    @JmsListener(destination = "processing-failure", containerFactory = "messagingJmsContainerFactory", concurrency = "1")
    public void sendReplyProcessingFailureNotification(User user){
        sendCouldNotProcessReply(user);
    }

    public void sendCouldNotProcessReply(User user){
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
    }
}

