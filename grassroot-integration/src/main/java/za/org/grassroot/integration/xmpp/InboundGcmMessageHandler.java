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
import za.org.grassroot.integration.GroupChatService;
import za.org.grassroot.integration.MessageSendingService;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;

/**
 * Created by paballo on 2016/04/04.
 * major todo: more intelligent handling of GCM return messages (e.g., unregister if not registered, etc)
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

    @Autowired
    private GroupChatService groupChatService;

    private static final String ORIGINAL_MESSAGE_ID = "original_message_id";

    @ServiceActivator(inputChannel = "gcmInboundChannel")
    public void handleUpstreamMessage(GcmUpstreamMessage message) throws Exception {

        String message_type = message.getMessageType();
        log.info(message.toString());
        if (message_type == null) {
            handleOrdinaryMessage(message);
        } else {
            switch (message_type) {
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

        }
    }

    private void handleAcknowledgementReceipt(GcmUpstreamMessage input) {
        String messageId = input.getMessageId();
        String data = String.valueOf(input.getData());
        log.debug("Gcm acknowledges receipt of message {}, with payload {}", messageId, data);

    }

    private void handleOrdinaryMessage(GcmUpstreamMessage input) {
        log.info("Ordinary message received");
        String messageId = input.getMessageId();
        String from = input.getFrom();

        String action = String.valueOf(input.getData().get("action"));
        if (action != null) {
            switch (action) {
                case "REGISTER":
                    String phoneNumber = (String) input.getData().get("phoneNumber");
                    registerUser(from, phoneNumber);
                    break;
                case "UPDATE_READ":
                    String notificationId = (String) input.getData().get("notificationId");
                    updateReadStatus(notificationId);
                    break;
                case "CHAT":
                    groupChatService.processAndRouteIncomingChatMessage(input);
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
        if (notification != null) {
            log.info("Push Notification delivery failed, now sending SMS to  {}", notification.getTarget().getPhoneNumber());
            messageSendingService.sendMessage(UserMessagingPreference.SMS.name(), notification);
        } else {
            log.info("Received an upstream message without notification, must be chat message");
        }
    }

    private void handleDeliveryReceipts(GcmUpstreamMessage input) {
        String messageId = String.valueOf(input.getData().get(ORIGINAL_MESSAGE_ID));
        log.debug("Message " + messageId + " delivery successful, updating notification to delivered status.");
        notificationService.markNotificationAsDelivered(messageId);
    }


    private void sendAcknowledment(String registrationId, String messageId) {
        org.springframework.messaging.Message<Message> gcmMessage = GcmXmppMessageCodec.encode(registrationId, messageId, "ack");
        log.debug("Acknowledging message with id ={}", messageId);
        gcmXmppOutboundChannel.send(gcmMessage);
    }

    public void registerUser(String registrationId, String phoneNumber) {
        String convertedNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        User user = userRepository.findByPhoneNumber(convertedNumber);
        log.debug("Registering user with phoneNumber={} as a push notification receipient, found user={}", convertedNumber, user);
        gcmService.registerUser(user, registrationId);
        user.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
        userRepository.save(user);
    }

    public void updateReadStatus(String messageId) {
        log.debug("Marking notification with id={} as read", messageId);
        notificationService.updateNotificationReadStatus(messageId, true);

    }

}


