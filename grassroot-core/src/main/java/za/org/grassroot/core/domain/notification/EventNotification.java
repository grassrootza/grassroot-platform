package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import java.util.Locale;
import java.util.Objects;

public class EventNotification extends Notification {
	private Event event;

	public EventNotification(User destination, EventLog eventLog, Event event) {
		super(destination, eventLog, NotificationType.EVENT);
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
