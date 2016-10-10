package za.org.grassroot.integration.xmpp;

import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;
import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.services.*;
import za.org.grassroot.integration.utils.MessageUtils;

import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 * major todo: more intelligent handling of GCM return messages (e.g., unregister if not registered, etc)
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
    private GroupChatSettingsService groupChatSettingsService;

    @Autowired
    private LearningService learningService;

    @Autowired
    @Qualifier("integrationMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;


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
                    handleNonServiceMessage(input);
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
        log.info("Push Notification delivery failed, now sending SMS to  {}", notification.getTarget().getPhoneNumber());
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

    public void handleNonServiceMessage(GcmUpstreamMessage input) {
        String phoneNumber = (String) input.getData().get("phoneNumber");
        String groupUid = (String) input.getData().get("groupUid");
        User user = userRepository.findByPhoneNumber(phoneNumber);
        GroupChatSettings groupChatSettings = groupChatSettingsService.load(user.getUid(), groupUid);
        if (groupChatSettings != null) {
            Group group = groupChatSettings.getGroup();
            org.springframework.messaging.Message<Message> message;
            log.info("Posting to topic with id={}", groupUid);
            try {
                if (groupChatSettingsService.isCanSend(user.getUid(), groupUid)) {
                    log.info("Posting to topic with id={}", groupUid);
                    message = generateMessage(user, input, group);
                } else {
                    message = generateCannotSendMessage(input, group);
                }
                gcmXmppOutboundChannel.send(message);
            } catch (GroupChatSettingNotFoundException e) {
                log.info("User with phoneNumber={} is not enabled to send messages to this group", phoneNumber);
            }
        }
    }

    public org.springframework.messaging.Message<Message> generateCannotSendMessage(GcmUpstreamMessage input, Group group){
        Map<String, Object> data = MessageUtils.generateUserMutedResponseData(messageSourceAccessor,input,group);
        return GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                null, null, null,
                AndroidClickActionType.CHAT_MESSAGE.name(), data);

    }


    public org.springframework.messaging.Message<Message> generateMessage(User user, GcmUpstreamMessage input, Group group) {
        org.springframework.messaging.Message<Message> gcmMessage;
        Map<String, Object> data;
        if (!MessageUtils.isCommand((input))) {
            String topic = TOPICS.concat(group.getUid());
            data = MessageUtils.generateChatMessageData(input, user, group);
            gcmMessage = GcmXmppMessageCodec.encode(topic, String.valueOf(data.get("messageId")),
                    null, null, null,
                    AndroidClickActionType.CHAT_MESSAGE.name(), data);
        } else {
            String[] tokens = MessageUtils.tokenize(String.valueOf(input.getData().get("message")));
            if (tokens.length < 2) {
                data = MessageUtils.generateInvalidCommandResponseData(messageSourceAccessor,input, group);
            } else {
                try {
                    tokens[1] = learningService.parse(tokens[1]).toString();
                    data = MessageUtils.generateCommandResponseData(messageSourceAccessor,input, group, tokens);
                } catch (SeloParseDateTimeFailure e) {
                    data = MessageUtils.generateInvalidCommandResponseData(messageSourceAccessor,input, group);
                }
            }
                gcmMessage = GcmXmppMessageCodec.encode(input.getFrom(), String.valueOf(data.get("messageId")),
                        null, null, null,
                        AndroidClickActionType.CHAT_MESSAGE.name(), data);
            }
            return gcmMessage;
        }

    }


