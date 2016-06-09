package za.org.grassroot.integration.xmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.MessageBuilder;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;
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
		logger.info("Generated collapseKey " + collapseKey);;
		Map<String, Object> notificatonPart = createNotificatonPart(title, body, clickAction);
		GcmEntity gcmPayload = new GcmEntity(messageId, registrationID, collapseKey, dataPart, notificatonPart);
		return constructGcmMessage(gcmPayload);
	}

	private static Map<String, Object> createNotificatonPart(String title, String body, String clickAction) {
		Map<String, Object> data = new HashMap<>();
		data.put("title", title);
		data.put("body", body);
		data.put("click_action", clickAction);
		return data;
	}

	private static org.springframework.messaging.Message<Message> constructGcmMessage(GcmEntity gcmPayload) {
		Message xmppMessage = new Message();
		try {
			String gcmPayloadJson = mapper.writeValueAsString(gcmPayload);
			xmppMessage.addExtension(new GcmPacketExtension(gcmPayloadJson));

			return MessageBuilder.withPayload(xmppMessage).build();

		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error while trying to encode GCM XMPP message: " + e.getMessage(), e);
		}
	}

	public static Map<String, Object> createDataPart(String notificationUid, String title, String group, String description, Object id,
															Instant createdDateTime, NotificationType alertType, String entityType) {
		Map<String, Object> data = new HashMap<>();
		data.put("title", title);
		if (group != null) {
			data.put("group", group);
		}
		data.put("notificationUid", notificationUid);
		data.put("body", description);
		data.put("id", id);
		data.put("created_date_time", createdDateTime);
		data.put("alert_type", alertType);
		data.put("entity_type", entityType);
		return data;
	}
}
