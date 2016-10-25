package za.org.grassroot.integration.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.integration.NotificationService;

/**
 * Created by luke on 2016/10/24.
 */
@MessageEndpoint
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class OutboundEmailHandler {

    private static final Logger log = LoggerFactory.getLogger(OutboundEmailHandler.class);

    private EmailSendingBroker emailSendingBroker;

    private NotificationService notificationService;

    @Autowired
    public OutboundEmailHandler(EmailSendingBroker emailSendingBroker, NotificationService notificationService) {
        this.emailSendingBroker = emailSendingBroker;
        this.notificationService = notificationService;
    }

    @ServiceActivator(inputChannel = "emailOutboundChannel")
    public void handleMessage(Message<Notification> message) throws Exception {
        log.info("Sending email outbound ..."); // todo : error handling, and so much else
        Notification notification = message.getPayload();
        GrassrootEmail email = new GrassrootEmail.EmailBuilder("Message from Grassroot")
                .address(notification.getTarget().getDisplayName())
                .content(notification.getMessage())
                .build();
        emailSendingBroker.sendMail(email);
        notificationService.markNotificationAsDelivered(notification.getUid());
    }

}
