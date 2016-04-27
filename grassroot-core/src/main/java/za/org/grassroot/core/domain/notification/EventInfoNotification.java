package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.Locale;

@Entity
@DiscriminatorValue("EVENT_INFO")
public class EventInfoNotification extends EventNotification {

	private EventInfoNotification() {
		// for JPA
	}

	public EventInfoNotification(User destination, GroupLog groupLog, Event event) {
		super(destination, null, groupLog, event);
	}

	public EventInfoNotification(User destination, EventLog eventLog) {
		super(destination, null, eventLog);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		//TODO fix the locale resolver in config
		String messageKey = "";
		if (getEvent().getEventType() == EventType.VOTE) {
			messageKey = "sms.vote.send.new";
		} else {
			messageKey = getEvent().isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";

		}
		Locale locale = getUserLocale();
		return messageSourceAccessor.getMessage(messageKey, populateEventFields(getEvent()), locale);
	}
}
