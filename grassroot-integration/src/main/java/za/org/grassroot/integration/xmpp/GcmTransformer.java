package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jivesoftware.smack.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.integration.domain.GcmEntity;


import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */

@Component
public class GcmTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static Logger log = LoggerFactory.getLogger(GcmTransformer.class);

    @Transformer(inputChannel = "gcmOutboundChannel")
    public org.jivesoftware.smack.packet.Message transform(Message<Notification> message) throws Exception {

        org.jivesoftware.smack.packet.Message xmppMessage = new org.jivesoftware.smack.packet.Message();

            Notification notification = (Notification) message.getPayload();
            String registrationID = notification.getGcmRegistration().getRegistrationId();
            String messageId = notification.getUid();
            log.info("Attempting to transform message with id " + messageId);
            String collapseKey = generateCollapseKey(notification);
            Map<String, Object> data = createData(notification);
            GcmEntity gcmPayload = new GcmEntity(messageId, registrationID, collapseKey, data);
            String gcmJsonPayload = mapper.writeValueAsString(gcmPayload);
            log.info(xmppMessage.toString());
            xmppMessage.addExtension(new GcmPacketExtension(gcmJsonPayload));
            log.info("Message with id " + messageId + " transformed to " + xmppMessage.toXML().toString());
    //    }
     /*   else{
            HashMap<String,Object> payload = (HashMap<String, Object>) message.getPayload();
            String gcmJsonPayload = mapper.writeValueAsString(payload);
            log.info(xmppMessage.toString());
            xmppMessage.addExtension(new GcmPacketExtension(gcmJsonPayload));
            log.info("Message with id " + payload.get("message_id") + "to be acknowledged was transformed  to" + xmppMessage.toXML().toString());
        }*/


        return xmppMessage;
    }



    private String generateCollapseKey(Notification notification) {
        StringBuilder sb = new StringBuilder();
        String collapseKey = null;
        switch (notification.getNotificationType()) {
            case EVENT:
                collapseKey = sb.append(notification.getEventLog().getUid())
                        .append("_")
                        .append(notification
                                .getEventLog()
                                .getEvent()
                                .resolveGroup()
                                .getGroupName())
                        .toString();
                log.info("Generated collapseKey " + collapseKey);
                break;

            case LOGBOOK:
                collapseKey = sb.append(notification.getEventLog().getUid())
                        .append("_")
                        .append(notification
                                .getEventLog()
                                .getEvent()
                                .resolveGroup()
                                .getGroupName())
                        .toString();
                break;
        }
        return collapseKey;

    }

    private Map<String, Object> createData(Notification notification) {

        Map<String, Object> data = new HashMap<>();

        switch (notification.getNotificationType()) {
            case EVENT:
                data.put("title", notification.getEventLog().getEvent().getName());
                data.put("description", notification.getEventLog().getMessage());
                data.put("id", notification.getEventLog().getEvent().getUid());
                data.put("created_date_time", notification.getCreatedDateTime());
                data.put("alert_type", notification.getNotificationType());
                data.put("entity_type", notification.getEventLog().getEvent().getEventType().name());
                break;

            case LOGBOOK:
                data.put("title", notification.getEventLog().getEvent().getName());
                data.put("description", notification.getEventLog().getMessage());
                data.put("id", notification.getLogBookLog().getLogBookId());
                data.put("created_date_time", notification.getCreatedDateTime());
                data.put("alert_type", notification.getNotificationType());
                data.put("entity_type", notification.getEventLog().getEvent().getEventType().name());
                break;

        }

        return data;

    }

    private String removeSMSSpecificStrings(String message) {
        // if(message.contains())
        return null;

    }


}
