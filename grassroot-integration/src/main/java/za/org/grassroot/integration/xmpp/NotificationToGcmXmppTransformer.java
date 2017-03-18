package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.UserNotification;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.GcmRegistrationRepository;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses za.org.grassroot.integration.xmpp.GcmXmppMessageCodec to create GCM XMPP message from Notification entity.
 */
@Component
@ConditionalOnProperty(name = "gcm.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class NotificationToGcmXmppTransformer {

    private static final Logger log = LoggerFactory.getLogger(NotificationToGcmXmppTransformer.class);

	private static final String TOPICS = "/topics/";

    private final GcmRegistrationRepository gcmRegistrationRepository;

	@Autowired
	public NotificationToGcmXmppTransformer(GcmRegistrationRepository gcmRegistrationRepository) {
		this.gcmRegistrationRepository = gcmRegistrationRepository;
	}

	@Transformer(inputChannel = "gcmOutboundChannel", outputChannel = "gcmXmppOutboundChannel")
    public Message<org.jivesoftware.smack.packet.Message> transform(Message<Object> message) throws Exception {

		Message<org.jivesoftware.smack.packet.Message> gcmMessage;
		if(message.getPayload() instanceof Notification){
			Notification notification = (Notification) message.getPayload();
			gcmMessage = constructGcmMessage(notification);
			log.debug("Message with id {}, transformed to {}", notification.getUid(),
					gcmMessage != null && gcmMessage.getPayload() != null ? gcmMessage.getPayload().toXML().toString() : "null");
		}else{
			gcmMessage = GcmXmppMessageCodec.encode(TOPICS.concat("keepalive"), UIDGenerator.generateId(), null);
		}
        return gcmMessage;
    }

    @Transactional(readOnly = true)
    private Message<org.jivesoftware.smack.packet.Message> constructGcmMessage(Notification notification) throws JsonProcessingException {

	    GcmRegistration gcmRegistration = gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(notification.getTarget());
        String registrationID = gcmRegistration.getRegistrationId();
        String messageId = notification.getUid();

        log.debug("Attempting to transform message for registration ID {}, with message id {}", messageId, registrationID);
        String collapseKey = generateCollapseKey(notification);
        log.debug("Generated collapseKey " + collapseKey);
        Map<String, Object> dataPart = createDataPart(notification);

        return GcmXmppMessageCodec.encode(registrationID, messageId, collapseKey, dataPart);
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

            case TODO:
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
						notification.getEventLog().getEvent().getAncestorGroup().getUid(),
						notification.getEventLog().getEvent().getUid(),
		                notification.getEventLog().getEvent().getEventType().name(),
		                notification);
	        case TODO:
                Todo todo = notification.getTodoLog().getTodo();
                return createDataPart(
		                todo.getAncestorGroup().getGroupName(),
		                todo.getAncestorGroup().getGroupName(),
						todo.getAncestorGroup().getUid(),
						todo.getUid(),
		                TaskType.TODO.name(),
		                notification);
	        case USER:
		        return userNotificationData((UserNotification) notification);

	        default:
		        return data;
        }
    }

	private Map<String, Object> createDataPart(final String title, final String group, String groupUid, final String entityUid,
											   final String entityType, Notification notification) {
		return GcmXmppMessageCodec.createDataPart(
				notification.getUid(),
				title,
				group,
				groupUid,
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
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_REMINDER:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_APPROVED:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			case JOIN_REQUEST_DENIED:
				return createDataPart(
						getGroupNameFromUserNotification(notification),
						getGroupNameFromUserNotification(notification),
						getGroupUidFromJoinRequestNotification(notification),
						getRequestUidFromJoinRequestNotification(notification),
						type.name(),
						notification);
			default:
				return createDataPart(
						"Grassroot",
						null,
						null, null,
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
            case TODO:
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