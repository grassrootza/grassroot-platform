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
import za.org.grassroot.core.domain.notification.UserNotification;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.integration.domain.AndroidClickActionType;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        log.info("Message with id {}, transformed to {}", notification.getUid(),
		        gcmMessage != null && gcmMessage.getPayload() != null ? gcmMessage.getPayload().toXML().toString() : "null");
        return gcmMessage;
    }

    @Transactional
    private Message<org.jivesoftware.smack.packet.Message> constructGcmMessage(Notification notification) throws JsonProcessingException {

	    GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(notification.getTarget());
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
                Todo todo = notification.getTodoLog().getTodo();
                title = todo.getAncestorGroup().getGroupName();
                body = notification.getMessage();
                break;

            case ACCOUNT:
                title = notification.getAccountLog().getAccount().getAccountName();
                body = notification.getMessage();
                break;

            case USER:
                title = getGroupNameFromUserNotification((UserNotification) notification);
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
                return sb.append(notification.getEventLog().getEvent().getUid())
		                .append("_")
		                .append(groupName)
		                .toString();

            case LOGBOOK:
                return sb.append(notification.getTodoLog().getTodo().getUid())
		                .append("_")
		                .append(notification.getTodoLog().getTodo().getAncestorGroup().getGroupName())
		                .toString();

	        default:
		        return null;
        }
    }

    private Map<String, Object> createDataPart(Notification notification) {
        Map<String, Object> data = new HashMap<>();

        switch (notification.getNotificationType()) {
            case EVENT:
                return createDataPart(
		                notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
		                notification.getEventLog().getEvent().getAncestorGroup().getGroupName(),
		                notification.getEventLog().getEvent().getUid(),
		                notification.getEventLog().getEvent().getEventType().name(),
		                notification);

	        case LOGBOOK: // todo : switch name to to-do
                Todo todo = notification.getTodoLog().getTodo();
                return createDataPart(
		                todo.getAncestorGroup().getGroupName(),
		                null,
		                todo.getUid(),
		                TaskType.TODO.name(),
		                notification);
	        case USER:
		        return userNotificationData((UserNotification) notification);

	        default:
		        return data;
        }
    }

	private Map<String, Object> createDataPart(final String title, final String groupName, final String entityUid,
	                                           final String entityType, Notification notification) {
		return GcmXmppMessageCodec.createDataPart(
				notification.getUid(),
				title,
				groupName,
				notification.getMessage(),
				entityUid,
				notification.getCreatedDateTime(),
				notification.getNotificationType(),
				entityType,
				getActionType(notification),
				notification.getPriority());
	}

	private Map<String, Object> userNotificationData(UserNotification notification) {
		final UserLogType type = notification.getUserLog().getUserLogType();
		switch (type) {
			case JOIN_REQUEST:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_REMINDER:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_APPROVED:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_DENIED:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupNameFromUserNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			default:
				return createDataPart(
						"Grassroot",
						null,
						null,
						type.name(),
						notification);
		}
	}

	private String getGroupNameFromUserNotification(UserNotification notification) {
		final String desc = notification.getUserLog().getDescription();
		final Matcher matchBeg = Pattern.compile("<xgn>").matcher(desc);
		final Matcher matchEnd = Pattern.compile("</xgn>").matcher(desc);
		if (matchBeg.find() && matchEnd.find()) {
			return desc.substring(matchBeg.end(), matchEnd.start());
		} else {
			return "Grassroot";
		}
	}

	private String getGroupUidFromJoinRequestNotification(UserNotification notification) {
		final String desc = notification.getUserLog().getDescription();
		final Matcher matchBeg = Pattern.compile("<xguid>").matcher(desc);
		final Matcher matchEnd = Pattern.compile("</xguid>").matcher(desc);
		if (matchBeg.find() && matchEnd.find()) {
			return desc.substring(matchBeg.end(), matchEnd.start());
		} else {
			return null;
		}
	}

	private String getRequestUidFromJoinRequestNotification(UserNotification notification) {
		final String desc = notification.getUserLog().getDescription();
		final Matcher matchBeg = Pattern.compile("<xruid>").matcher(desc);
		final Matcher matchEnd = Pattern.compile("</xruid>").matcher(desc);
		if (matchBeg.find() && matchEnd.find()) {
			return desc.substring(matchBeg.end(), matchEnd.start());
		} else {
			return null;
		}
	}

    private AndroidClickActionType getActionType(Notification notification) {
        AndroidClickActionType actionType;
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
		            case RESULT:
			            actionType = AndroidClickActionType.TASK_RESULTS;
			            break;
		            default:
			            actionType = AndroidClickActionType.SHOW_MESSAGE;
			            break;
	            }
                break;
            case LOGBOOK:
                TodoLog todoLog = notification.getTodoLog();
	            switch (todoLog.getType()) {
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
			            actionType = AndroidClickActionType.SHOW_MESSAGE;
			            break;
	            }
	            break;
	        case USER:
		        switch (notification.getUserLog().getUserLogType()) {
			        case JOIN_REQUEST:
			        case JOIN_REQUEST_REMINDER:
				        actionType = AndroidClickActionType.SHOW_JOIN_REQ;
				        break;
			        case JOIN_REQUEST_APPROVED:
				        actionType = AndroidClickActionType.JOIN_APPROVED;
				        break;
			        default:
				        actionType = AndroidClickActionType.SHOW_MESSAGE;
				        break;
		        }
		        break;
	        default:
		        actionType = AndroidClickActionType.SHOW_MESSAGE;
        }

        return actionType;
    }
}