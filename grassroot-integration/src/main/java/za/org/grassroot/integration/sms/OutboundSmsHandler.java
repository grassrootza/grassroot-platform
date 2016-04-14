package za.org.grassroot.integration.sms;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.integration.services.SmsSendingService;

/**
 * Created by paballo on 2016/04/06.
 */

@MessageEndpoint
public class OutboundSmsHandler {

    private static final Logger log = LoggerFactory.getLogger(OutboundSmsHandler.class);

    @Autowired
    private SmsSendingService smsSendingService;

    @Autowired
    private NotificationService notificationService;

    @ServiceActivator(inputChannel = "smsOutboundChannel")
    public void handleMessage(Message<Notification> message) throws Exception {
        log.info("SMS outbound channel received message={}", message.getPayload().toString());
        Notification notification = message.getPayload();
        String destination =notification.getUser().getPhoneNumber();
        log.info("Sms outbound channel sending forwarding message to ={}", destination);
        String msg = notification.getMessage();
        log.info("Sms outbound channel sending forwarding message  ={}", msg);
        smsSendingService.sendSMS(msg,destination);
        notificationService.updateNotificationDeliveryStatus(notification.getUid(),true);
        notificationService.updateNotificationReadStatus(notification.getUid(),true);
    }

    }
