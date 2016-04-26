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
import za.org.grassroot.core.repository.GcmRegistrationRepository;

import java.time.Instant;
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
		GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(notification.getUser());
		String registrationID = gcmRegistration.getRegistrationId();

		String messageId = notification.getUid();

		log.info("Attempting to transform message with id " + messageId);
		String collapseKey = generateCollapseKey(notification);
		log.info("Generated collapseKey " + collapseKey);

		Map<String, Object> dataPart = createDataPart(notification);

		String title = null;
		String body = null;
		switch (notification.getNotificationType()) {
			case EVENT:
				title = notification.getEventLog().getEvent().resolveGroup().getGroupName();
				body = notification.getEventLog().getMessage();
				break;

			case LOGBOOK:
				LogBook logBook = notification.getLogBookLog().getLogBook();
				title = logBook.resolveGroup().getGroupName();
				body = notification.getLogBookLog().getMessage();
				break;
		}

		return GcmXmppMessageCodec.encode(registrationID, messageId, collapseKey, title, body, dataPart);
	}

	private String generateCollapseKey(Notification notification) {
		StringBuilder sb = new StringBuilder();
		switch (notification.getNotificationType()) {
			case EVENT:
				String groupName = notification.getEventLog().getEvent().resolveGroup().getGroupName();
				String eventLogUid = notification.getEventLog().getUid();
				return sb.append(eventLogUid).append("_").append(groupName).toString();

			case LOGBOOK:
				//todo:  add uid field to logbooklog entity
				Long logBookLogId = notification.getLogBookLog().getId();
				Instant logBookLogCreatedTime = notification.getLogBookLog().getCreatedDateTime().toInstant();
				return sb.append(logBookLogId).append("_").append(logBookLogCreatedTime).toString();
		}
		return null;
	}

	private Map<String, Object> createDataPart(Notification notification) {
		Map<String, Object> data = new HashMap<>();

		switch (notification.getNotificationType()) {
			case EVENT:
				return GcmXmppMessageCodec.createDataPart(
						notification.getEventLog().getEvent().getName(),
						notification.getEventLog().getEvent().resolveGroup().getGroupName(),
						notification.getEventLog().getMessage(),
						notification.getEventLog().getEvent().getUid(),
						notification.getCreatedDateTime(),
						notification.getNotificationType(),
						notification.getEventLog().getEvent().getEventType()
				);

			case LOGBOOK:
				LogBook logBook = notification.getLogBookLog().getLogBook();

				return GcmXmppMessageCodec.createDataPart(
						logBook.resolveGroup().getGroupName(),
						null,
						notification.getLogBookLog().getMessage(),
						notification.getLogBookLog().getLogBook().getId(),
						notification.getCreatedDateTime(),
						notification.getNotificationType(),
						notification.getEventLog().getEvent().getEventType()
				);
		}
		return data;
	}
}
