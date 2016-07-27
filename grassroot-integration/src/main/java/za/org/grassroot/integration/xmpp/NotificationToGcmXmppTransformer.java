package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.domain.AndroidClickActionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses za.org.grassroot.integration.xmpp.GcmXmppMessageCodec to create GCM XMPP message from Notification entity.
 */
@Component
public class NotificationToGcmXmppTransformer {

    private static final Logger log = LoggerFactory.getLogger(NotificationToGcmXmppTransformer.class);

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Transformer(inputChannel = "gcmOutboundChannel", outputChannel = "gcmXmppOutboundChannel")
    public Message<org.jivesoftware.smack.packet.Message> transform(Message<Notification> message) throws Exception {
        Notification notification = message.getPayload();

        Message<org.jivesoftware.smack.packet.Message> gcmMessage = constructGcmMessage(notification);
        log.info("Message with id " + notification.getUid() + " transformed to " + gcmMessage.getPayload().toXML().toString());
        return gcmMessage;
    }

    @Transactional
    private Message<org.jivesoftware.smack.packet.Message> constructGcmMessage(Notification notification) throws JsonProcessingException {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(notification.getTarget());

        // todo : move this to somewhere earlier
        if (gcmRegistration == null) {
            // this sometimes happens with bad connections : throwing here ensures picker will try notification again
            // and then this should stop, with preference reset, but maybe try move it earlier
            User user = notification.getTarget();
            user.setMessagingPreference(UserMessagingPreference.SMS);
            log.info("Error! User had no gcm registration but had gcm preference; resetting");
            return null;
        }

        String registrationID = gcmRegistration.getRegistrationId();
        String messageId = notification.getUid();

        log.info("Attempting to transform message for registration ID {}, with message id {}", messageId, registrationID);
        String collapseKey = generateCollapseKey(notification);
        log.info("Generated collapseKey " + collapseKey);
        Map<String, Object> dataPart = createDataPart(notification);

        String title;
        String body;

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

            case ACCOUNT:
                title = "Grassroot Message"; // todo : need to put group in here
                body = notification.getMessage();
                break;
            case USER:
                title = "Grassroot Message";
                body = notification.getMessage();
                break;

            default:
                throw new UnsupportedOperationException("Have to add support for notification type: " + notification.getNotificationType());
        }

        return GcmXmppMessageCodec.encode(registrationID, messageId, collapseKey, title, body, null, dataPart);
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
                        notification.getUid(),
                        notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
                        notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
                        notification.getMessage(),
                        notification.getEventLog().getEvent().getUid(),
                        notification.getCreatedDateTime(),
                        notification.getNotificationType(),
                        notification.getEventLog().getEvent().getEventType().name(),
		                getActionType(notification),
		                notification.getPriority());

            case LOGBOOK:
                LogBook logBook = notification.getLogBookLog().getLogBook();

                return GcmXmppMessageCodec.createDataPart(
                        notification.getUid(),
                        logBook.getAncestorGroup().getGroupName(),
                        null,
                        notification.getMessage(),
                        logBook.getUid(),
                        notification.getCreatedDateTime(),
                        notification.getNotificationType(),
		                TaskType.TODO.name(),
		                getActionType(notification),
		                notification.getPriority());
        }
        return data;
    }

    private AndroidClickActionType getActionType(Notification notification) {
        AndroidClickActionType actionType = null;
        switch (notification.getNotificationType()) {
            case EVENT:
	            EventLog eventLog = notification.getEventLog();
	            switch (eventLog.getEventLogType()) {
		            case CREATED:
			            actionType = AndroidClickActionType.TASK_CREATED;
			            break;
		            case CHANGE:
			            actionType = AndroidClickActionType.TASK_CHANGED;
			            break;
		            case CANCELLED:
			            actionType = AndroidClickActionType.TASK_CANCELLED;
			            break;
		            case REMINDER:
			            actionType = AndroidClickActionType.TASK_REMINDER;
			            break;
		            default:
			            actionType = AndroidClickActionType.VIEW_TASK;
			            break;
	            }
                break;
            case LOGBOOK:
                LogBookLog logBookLog = notification.getLogBookLog();
	            switch (logBookLog.getType()) {
		            case CREATED:
			            actionType = AndroidClickActionType.TASK_CREATED;
			            break;
		            case CHANGED:
			            actionType = AndroidClickActionType.TASK_CHANGED;
			            break;
		            case REMINDER_SENT:
			            actionType = AndroidClickActionType.TASK_REMINDER;
			            break;
		            default:
			            actionType = AndroidClickActionType.VIEW_TASK;
			            break;
	            }
	            break;
        }
        return actionType;
    }
}