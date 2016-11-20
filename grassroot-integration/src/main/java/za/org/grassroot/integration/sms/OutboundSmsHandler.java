package za.org.grassroot.integration.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.integration.NotificationService;

/**
 * Created by paballo on 2016/04/06.
 * major todo: decide how to handle non-delivered SMSs, e.g., decide if should update the next delivery time? also, keep an eye out on possible loops
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
        log.info("SMS outbound channel received message={}", message.getPayload().getMessage());
        Notification notification = message.getPayload();
        String destination = notification.getTarget().getPhoneNumber();
        String msg = notification.getMessage();
        SmsGatewayResponse response = smsSendingService.sendSMS(msg,destination);
        // since even if the gateway didn't respond, we have delivered it there, and not marking it here can lead to a lot of duplicate calls
        notificationService.markNotificationAsDelivered(notification.getUid());
        if (response.isSuccessful()) {
            notificationService.updateNotificationReadStatus(notification.getUid(), true);
        } else {
            log.error("error delivering SMS, response from gateway: {}", response.toString());
        }
    }
}
