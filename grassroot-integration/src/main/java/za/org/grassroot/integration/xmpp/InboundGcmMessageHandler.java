package za.org.grassroot.integration.xmpp;

import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.NotificationService;

/**
 * Created by paballo on 2016/04/04.
 */
@Component
public class InboundGcmMessageHandler {

    private Logger log = LoggerFactory.getLogger(InboundGcmMessageHandler.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MessageSendingService messageSendingService;

    @Autowired
    private GcmService gcmService;

    @Autowired
    private MessageChannel gcmXmppOutboundChannel;

    @Autowired
    private UserRepository userRepository;

    private static final String ORIGINAL_MESSAGE_ID = "original_message_id";


    @ServiceActivator(inputChannel = "gcmInboundChannel")
    public void handleUpstreamMessage(GcmUpstreamMessage message) throws Exception {

        String message_type = message.getMessageType();
        log.info(message.toString());
        if(message_type == null){
            handleOrdinaryMessage(message);
        }else{
            switch(message_type){
            case "ack":
                handleAcknowledgementReceipt(message);
                break;
            case "nack":
                handleNotAcknowledged(message);
                break;
            case "receipt":
                handleDeliveryReceipts(message);
                break;
            case "control":
                break;
            default:
                break;
        }

    }}

    private void handleAcknowledgementReceipt(GcmUpstreamMessage input) {
        String messageId = input.getMessageId();
        String data = String.valueOf(input.getData());
        log.info("Gcm acknowledges receipt of message {}, with payload {}", messageId, data);

    }

    private void handleOrdinaryMessage(GcmUpstreamMessage input){
       log.info("Ordinary message received");
        String messageId = input.getMessageId();
        String from = input.getFrom();


        String action = String.valueOf(input.getData().get("action"));
        if(action != null) {
            switch (action) {
                case "REGISTER":
                    String phoneNumber = (String) input.getData().get("phoneNumber");
                    registerUser(from, phoneNumber);
                    break;
                case "UPDATE_READ":
                    String notificationId = (String) input.getData().get("notificationId");
                    updateReadStatus(notificationId);
                    break;
                default: //action unknown ignore
                    break;

            }
        }
        sendAcknowledment(from, messageId);
    }

    private void handleNotAcknowledged(GcmUpstreamMessage input) {
        String messageId = input.getMessageId();
        Notification notification = notificationService.loadNotification(messageId);
        log.info("Push Notification delivery failed, now sending SMS");
        log.info("Sending SMS to " + notification.getTarget().getPhoneNumber());
        messageSendingService.sendMessage(UserMessagingPreference.SMS.name(),notification);
    }

    private void handleDeliveryReceipts(GcmUpstreamMessage input){
        String messageId = String.valueOf(input.getData().get(ORIGINAL_MESSAGE_ID));
        log.info("Message " + messageId + " delivery successful, updating notification to delivered status.");
        notificationService.markNotificationAsDelivered(messageId);

    }


    private void sendAcknowledment(String registrationId, String messageId){
        org.springframework.messaging.Message<Message> gcmMessage = GcmXmppMessageCodec.encode(registrationId, messageId, "ack");
        log.info("Acknowledging message with id ={}", messageId);
		gcmXmppOutboundChannel.send(gcmMessage);
    }

    //todo move all ordinary message handling logic to a separate file
    public void registerUser(String registrationId, String phoneNumber){
        String convertedNumber= PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        User user = userRepository.findByPhoneNumber(convertedNumber);
        log.info("Registering user with phoneNumber={} as a push notification receipient, found user={}",convertedNumber, user);
        gcmService.registerUser(user,registrationId);
        user.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
        userRepository.save(user);
    }

    public void updateReadStatus(String messageId){
        log.info("Marking notification with id={} as read", messageId);
        notificationService.updateNotificationReadStatus(messageId,true);

    }
}
