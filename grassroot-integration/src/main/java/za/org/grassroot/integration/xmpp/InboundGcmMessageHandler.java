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
import za.org.grassroot.core.enums.UserMessagingPreference;
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
    private MessageChannel requestChannel;

    @Autowired
    private NotificationService notificationService;


    @Autowired
    private MessageSendingService messageSendingService;


    @ServiceActivator(inputChannel = "gcmInboundChannel")
    public void handleUpstreamMessage(Message message) throws Exception {

        GcmPacketExtension gcmPacket =
                (GcmPacketExtension) message.
                        getExtension(GcmPacketExtension.GCM_NAMESPACE);
        String json = gcmPacket.getJson();
        ObjectMapper mapper = new ObjectMapper();

        Map<String,Object> response = mapper.readValue(json, new TypeReference<Map<String, Object>>(){});
        String message_type = (String) response.get("message_type");
        log.info(response.toString());
        if(message_type == null){
            handleOrdinaryMessage(response);
        } else {
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
        String from = String.valueOf(input.get("data"));
        sendAcknowledment(createAcknowledgeMessage(from,messageId));
    }

    private void handleNotAcknowledged(Map<String, Object> input) {
        String messageId = (String) input.get("message_id");
        Notification notification = notificationService.loadNotification(messageId);
        log.info("Push Notification delivery failed, now sending sms");
        log.info("Sending sms to " + notification.getUser().getPhoneNumber());
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

    protected static Map<String,Object> createAcknowledgeMessage(String to, String messageId) {
        Map<String, Object> message = new HashMap<>();
        message.put("message_type", "ack");
        message.put("to", to);
        message.put("message_id", messageId);
        return message;
    }
    
    private void sendAcknowledment(Map<String,Object> response){
     //   messageSendingService.sendMessage(UserMessagingPreference.ANDROID_APP.toString(),response);

    }





}
