package za.org.grassroot.integration.sms;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.integration.services.SmsSendingService;

/**
 * Created by paballo on 2016/04/06.
 */

@MessageEndpoint
public class OutboundSmsHandler {

    @Autowired
    SmsSendingService smsSendingService;

    @Autowired
    NotificationService notificationService;

    @ServiceActivator(inputChannel = "smsOutboundChannel")
    public void handleMessage(Message<Notification> message) throws Exception {
        Notification notification = message.getPayload();
        String destination =notification.getUser().getPhoneNumber();
        String msg = notification.getEventLog().getMessage();
        smsSendingService.sendSMS(msg,destination);
        notificationService.updateNotificationDeliveryStatus(notification.getUid(),true);
        notificationService.updateNotificationReadStatus(notification.getUid(),true);
    }

    }
