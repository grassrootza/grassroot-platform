package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.integration.domain.GcmEntity;


import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/04/04.
 */

@Component
public class GcmTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Transformer(inputChannel = "gcmOutboundChannel")
    public org.jivesoftware.smack.packet.Message transform(Message<Notification> message) throws Exception {
        Notification notification = message.getPayload();
        String registrationID = notification.getGcmRegistration().getRegistrationId();
        String messageId =  notification.getUid();
        String collapseKey = generateCollapseKey(notification);
        Map<String, Object> data = createData(notification);
        GcmEntity gcmPayload = new GcmEntity(messageId,registrationID,collapseKey,data);
        String gcmJsonPayload = mapper.writeValueAsString(gcmPayload);
        org.jivesoftware.smack.packet.Message xmppMessage = new org.jivesoftware.smack.packet.Message();
        xmppMessage.addExtension(new GcmPacketExtension(gcmJsonPayload));

        return xmppMessage;
    }

    private String generateCollapseKey(Notification notification){
        StringBuilder sb = new StringBuilder();
        switch (notification.getNotificationType()) {
            case EVENT:
                return sb.append(notification.getEventLog().getUid())
                        .append("_")
                        .append(notification
                                .getEventLog()
                                .getEvent()
                                .resolveGroup()
                                .getGroupName())
                        .toString();

            case LOGBOOK:
                return sb.append(notification.getEventLog().getUid())
                        .append("_")
                        .append(notification
                                .getEventLog()
                                .getEvent()
                                .resolveGroup()
                                .getGroupName())
                        .toString();
            default:
                return null;
        }


    }
    private Map<String,Object> createData(Notification notification){

        Map<String,Object> data = new HashMap<>();

        switch (notification.getNotificationType()) {
            case EVENT:
                data.put("title", notification.getEventLog().getEvent().getName());
                data.put("description", notification.getEventLog().getMessage());
                data.put("id", notification.getEventLog().getEvent().getUid());
                data.put("created_date_time", notification.getCreatedDateTime());
                data.put("alert_type", notification.getEventLog().getEventLogType().name());
                data.put("entity_type", notification.getEventLog().getEvent().getEventType().name());
                break;

            case LOGBOOK:
                data.put("title", notification.getEventLog().getEvent().getName());
                data.put("description", notification.getEventLog().getMessage());
                data.put("id", notification.getLogBookLog().getLogBookId());
                data.put("created_date_time", notification.getCreatedDateTime());
                data.put("alert_type", LogBook.class.getCanonicalName());
                data.put("entity_type", notification.getEventLog().getEvent().getEventType().name());
                break;

        }

        return data;


    }


}
