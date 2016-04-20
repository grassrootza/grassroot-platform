package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.NotificationService;

import java.util.HashMap;
import java.util.Map;

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

    @ServiceActivator(inputChannel = "gcmInboundChannel")
    public void handleUpstreamMessage(Message message) throws Exception {

        GcmPacketExtension gcmPacket =
                (GcmPacketExtension) message.
                        getExtension(GcmPacketExtension.GCM_NAMESPACE);
        String json = gcmPacket.getJson();
        log.info("Parsed to json = {}", json);
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> response = mapper.readValue(json, new TypeReference<Map<String, Object>>(){});
        String message_type = (String) response.get("message_type");
        log.info(response.toString());
        if(message_type == null){
            handleOrdinaryMessage(response);
        }else{
            switch(message_type){
            case "ack":
                handleAcknowledgementReceipt(response);
                break;
            case "nack":
                handleNotAcknowledged(response);
                break;
            case "receipt":
                handleDeliveryReceipts(response);
                break;
            case "control":
                handleControlMessage(response);
                break;
            default:
                break;
        }

    }}

    private void handleAcknowledgementReceipt(Map<String, Object> input) {
        String messageId = (String) input.get("message_id");
        String data = String.valueOf(input.get("data"));
        log.info("Gcm acknowledges receipt of message " + messageId+ " with payload "+data);

    }

    private void handleOrdinaryMessage(Map<String, Object> input){
       log.info("Ordinary message received");
        String messageId = (String) input.get("message_id");
        String from = String.valueOf(input.get("from"));
        HashMap<String, Object> data = (HashMap)input.get("data");
        String action = String.valueOf(data.get("action"));
        if(action != null) {
            switch (action) {
                case "REGISTER":
                    String phoneNumber = (String) data.get("phoneNumber");
                    registerUser(from, phoneNumber);
                    break;
                case "UPDATE_READ":
                    String notificationId = (String) data.get("notificationId");
                    updateReadStatus(notificationId);
                    break;
                default: //acton unknown ignore
                    break;

            }
        }
        sendAcknowledment(from, messageId);
    }

    private void handleNotAcknowledged(Map<String, Object> input) {
        String messageId = (String) input.get("message_id");
        Notification notification = notificationService.loadNotification(messageId);
        log.info("Push Notification delivery failed, now sending SMS");
        log.info("Sending SMS to " + notification.getUser().getPhoneNumber());
        messageSendingService.sendMessage(UserMessagingPreference.SMS.name(),notification);
    }

    private void handleDeliveryReceipts(Map<String,Object> input){
        String messageId = String.valueOf(input.get("messageId"));
        log.info("Message " + messageId + " delivery successful, updating notification to read status.");
        notificationService.updateNotificationDeliveryStatus(messageId,true);

    }

    private void handleControlMessage(Map<String, Object> input) {
        String controlType = (String) input.get("control_type");
        if ("CONNECTION_DRAINING".equals(controlType)) {
          //todo: open a new connection
        }
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
        log.info("Marking notificatio with id={} as read", messageId);
        notificationService.updateNotificationReadStatus(messageId,true);

    }
}
