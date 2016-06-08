package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.GcmRegistrationRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses za.org.grassroot.integration.xmpp.GcmXmppMessageCodec to create GCM XMPP message from Notification entity.
 */
@Component
public class NotificationToGcmXmppTransformer {

    private static Logger log = LoggerFactory.getLogger(NotificationToGcmXmppTransformer.class);

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Transformer(inputChannel = "gcmOutboundChannel", outputChannel = "gcmXmppOutboundChannel")
    public Message<org.jivesoftware.smack.packet.Message> transform(Message<Notification> message) throws Exception {
        Notification notification = message.getPayload();

        Message<org.jivesoftware.smack.packet.Message> gcmMessage = constructGcmMessage(notification);
        log.info("Message with id " + notification.getUid() + " transformed to " + gcmMessage.getPayload().toXML().toString());
        return gcmMessage;
    }

    private Message<org.jivesoftware.smack.packet.Message> constructGcmMessage(Notification notification) throws JsonProcessingException {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(notification.getTarget());
        String registrationID = gcmRegistration.getRegistrationId();

        String messageId = notification.getUid();

        log.info("Attempting to transform message with id " + messageId);
        String collapseKey = generateCollapseKey(notification);
        log.info("Generated collapseKey " + collapseKey);
        Map<String, Object> dataPart = createDataPart(notification);

        String title = null;
        String body = null;
        String clickAction = getClickAction(notification);

        switch (notification.getNotificationType()) {
            case EVENT:
                title = notification.getEventLog().getEvent().getAncestorGroup().getGroupName();
                body = notification.getMessage();
                break;

            case LOGBOOK:
                LogBook logBook = notification.getLogBookLog().getLogBook();
                title = logBook.getAncestorGroup().getGroupName();
                body = notification.getMessage();
                break;

            default:
                throw new UnsupportedOperationException("Have to add support for notification type: " + notification.getNotificationType());
        }

        return GcmXmppMessageCodec.encode(registrationID, messageId, collapseKey, title, body, clickAction, dataPart);
    }

    private String generateCollapseKey(Notification notification) {
        StringBuilder sb = new StringBuilder();
        switch (notification.getNotificationType()) {
            case EVENT:
                String groupName = notification.getEventLog().getEvent().getAncestorGroup().getGroupName();
                return sb.append(notification.getEventLog().getEvent().getUid()).append("_").append(groupName).toString();

            case LOGBOOK:
                return sb.append(notification.getLogBookLog().getLogBook().getUid()).append("_").
                        append(notification.getLogBookLog().getLogBook().getAncestorGroup().getGroupName()).toString();
        }
        return null;
    }

    private Map<String, Object> createDataPart(Notification notification) {
        Map<String, Object> data = new HashMap<>();

        switch (notification.getNotificationType()) {
            case EVENT:
                return GcmXmppMessageCodec.createDataPart(
                        notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
                        notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
                        notification.getMessage(),
                        notification.getEventLog().getEvent().getUid(),
                        notification.getCreatedDateTime(),
                        notification.getNotificationType(),
                        notification.getEventLog().getEvent().getEventType().name()
                );

            case LOGBOOK:
                LogBook logBook = notification.getLogBookLog().getLogBook();

                return GcmXmppMessageCodec.createDataPart(
                        logBook.getAncestorGroup().getGroupName(),
                        null,
                        notification.getMessage(),
                        notification.getLogBookLog().getLogBook().getId(),
                        notification.getCreatedDateTime(),
                        notification.getNotificationType(), TaskType.TODO.name()
                );
        }
        return data;
    }

    private String getClickAction(Notification notification) {
        String clickAction = null;
        switch (notification.getNotificationType()) {
            case EVENT:
                return notification.getNotificationType().name();
            case LOGBOOK:
                return TaskType.TODO.name();
        }
        return clickAction;
    }
}