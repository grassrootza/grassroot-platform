package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.TextUtils;
import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.MessageBuilder;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GcmEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Constructs Spring Integration message wth Jivesoftware's XMPP message as payload, ready to be sent to
 * org.springframework.integration.xmpp.outbound.ChatMessageSendingMessageHandler.
 */
public class GcmXmppMessageCodec {

	private static final Logger logger = LoggerFactory.getLogger(GcmXmppMessageCodec.class);

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String DEFAULT_ACTION = "NOTIFICATION";

	private static final String notificationIcon = "@drawable/app_icon";

	private GcmXmppMessageCodec() {
		// utility
	}

	/**
	 * Used currently of ACK messages
	 */
	public static org.springframework.messaging.Message<Message> encode(String registrationID, String messageId, String messageType) {
		GcmEntity gcmPayload = new GcmEntity(messageId, registrationID, messageType);
		return constructGcmMessage(gcmPayload);
	}

	public static org.springframework.messaging.Message<Message> encode(String registrationID, String messageId, String collapseKey,
																		String title, String body, String clickAction, Map<String, Object> dataPart) {
		logger.debug("Generated collapseKey " + collapseKey);

		GcmEntity gcmPayload = new GcmEntity(messageId, registrationID, collapseKey, dataPart, null);
		return constructGcmMessage(gcmPayload);
	}

	private static org.springframework.messaging.Message<Message> constructGcmMessage(GcmEntity gcmPayload) {
		Message xmppMessage = new Message();
		try {
			String gcmPayloadJson = mapper.writeValueAsString(gcmPayload);
			logger.info("payload "+gcmPayloadJson);
			xmppMessage.addExtension(new GcmPacketExtension(gcmPayloadJson));

			return MessageBuilder.withPayload(xmppMessage).build();

		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Error while trying to encode GCM XMPP message: " + e.getMessage(), e);
		}
	}

	public static Map<String, Object> createDataPart(String notificationUid, String title, String groupName, String groupUid, String description, Object id,
													 Instant createdDateTime, NotificationType alertType, String entityType,
													 AndroidClickActionType clickAction, int priority) {
		Map<String, Object> data = new HashMap<>();
		///data.put("title", title);

		if (groupName != null) {
			data.put("group", groupName);
		}

		if (!TextUtils.isEmpty(groupUid)) {
			data.put("groupUid", groupUid);
		}

		data.put("notificationUid", notificationUid);
	    data.put("body", description);
		data.put("id", id);
		data.put("created_date_time", createdDateTime);
		data.put("alert_type", alertType);
		data.put("entity_type", entityType);
		data.put("click_action", clickAction);
		data.put("priority", priority);

		return data;
	}


}
