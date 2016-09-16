package za.org.grassroot.integration.xmpp;

import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.MessengerSettings;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;
import za.org.grassroot.integration.exception.MessengerSettingNotFoundException;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.MessengerSettingsService;
import za.org.grassroot.integration.services.NotificationService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */
@Component
public class InboundGcmMessageHandler {

    private Logger log = LoggerFactory.getLogger(InboundGcmMessageHandler.class);

    private static final String TOPICS = "/topics/";

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
    private MessengerSettingsService messengerSettingsService;


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
        log.info("Gcm acknowledges receipt of message {}, with payload {}", messageId, data);

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
                    handleChatMessage(input);
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
        messageSendingService.sendMessage(UserMessagingPreference.SMS.name(), notification);
    }

    private void handleDeliveryReceipts(GcmUpstreamMessage input) {
        String messageId = String.valueOf(input.getData().get(ORIGINAL_MESSAGE_ID));
        log.info("Message " + messageId + " delivery successful, updating notification to delivered status.");
        notificationService.markNotificationAsDelivered(messageId);
    }


    private void sendAcknowledment(String registrationId, String messageId) {
        org.springframework.messaging.Message<Message> gcmMessage = GcmXmppMessageCodec.encode(registrationId, messageId, "ack");
        log.info("Acknowledging message with id ={}", messageId);
        gcmXmppOutboundChannel.send(gcmMessage);
    }

    public void registerUser(String registrationId, String phoneNumber) {
        String convertedNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        User user = userRepository.findByPhoneNumber(convertedNumber);
        log.info("Registering user with phoneNumber={} as a push notification receipient, found user={}", convertedNumber, user);
        gcmService.registerUser(user, registrationId);
        user.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
        userRepository.save(user);
    }

    public void updateReadStatus(String messageId) {
        log.info("Marking notification with id={} as read", messageId);
        notificationService.updateNotificationReadStatus(messageId, true);

    }

    public void handleChatMessage(GcmUpstreamMessage input) {
        String phoneNumber = (String) input.getData().get("phoneNumber");
        String groupUid = (String) input.getData().get("groupUid");
        User user = userRepository.findByPhoneNumber(phoneNumber);
        MessengerSettings messengerSettings = messengerSettingsService.load(user.getUid(),groupUid);
        Group group = messengerSettings.getGroup();
        log.info("Posting to topic with id={}",groupUid);
        try {
            if(messengerSettingsService.isCanSend(user.getUid(),groupUid)){
                log.info("Posting to topic with id={}", groupUid);
                org.springframework.messaging.Message<Message> message = generateChatMessage(user, input,group);
                gcmXmppOutboundChannel.send(message);
            }
        } catch (MessengerSettingNotFoundException e) {
            log.info("User with phoneNumber={} is not enabled to send messages to this group", phoneNumber);
        }

    }

    private org.springframework.messaging.Message<Message> generateChatMessage(User user, GcmUpstreamMessage input, Group group){

        String groupUid = (String) input.getData().get("groupUid");
        String topic = TOPICS.concat(groupUid);
        String message = (String) input.getData().get("message");
        String messageId = UIDGenerator.generateId();

        Map<String, Object> data = new HashMap<>();
        data.put("groupUid", groupUid);
        data.put("groupName", group.getGroupName());
        data.put("groupIcon", group.getImageUrl());
        data.put("body", message);
        data.put("id", messageId);
        data.put("uid", input.getMessageId());
        data.put("title", user.nameToDisplay());
        data.put("phone_number", user.getPhoneNumber());
        data.put("userUid", user.getUid());
        data.put("created_date_time", Instant.now().toString());
        data.put("entity_type", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));
        
        org.springframework.messaging.Message<Message> gcmMessage = GcmXmppMessageCodec.encode(topic, messageId,
                null, null, null,
                AndroidClickActionType.CHAT_MESSAGE.name(), data);

        return gcmMessage;


    }
}
