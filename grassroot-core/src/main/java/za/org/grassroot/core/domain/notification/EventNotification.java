package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Locale;
import java.util.Objects;

public class EventNotification extends Notification {
	@ManyToOne
	@JoinColumn(name = "event_id")
	private Event event; // we ne3d this because this notification can come from GroupLog also

	public EventNotification(User destination, Event event, EventLog eventLog) {
		super(destination, eventLog, NotificationType.EVENT);
		this.event = Objects.requireNonNull(event);
	}

	public EventNotification(User destination, Event event, GroupLog groupLog) {
		super(destination, groupLog, NotificationType.EVENT);
		this.event = Objects.requireNonNull(event);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		//TODO fix the locale resolver in config
		String messageKey = "";
		if (event.getEventType() == EventType.VOTE) {
			messageKey = "sms.vote.send.new";
		} else {
			messageKey = event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";

		}
		Locale locale = getUserLocale();
		return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
	}
}
