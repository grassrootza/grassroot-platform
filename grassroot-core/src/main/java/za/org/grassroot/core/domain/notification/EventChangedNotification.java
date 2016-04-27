package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.Locale;

@Entity
@DiscriminatorValue("EVENT_CHANGED")
public class EventChangedNotification extends EventNotification {
	private EventChangedNotification() {
		// for JPA
	}

	public EventChangedNotification(User target, EventLog eventLog) {
		super(target, null, eventLog);
	}

	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		Locale locale = getUserLocale();
		Event event = getEventLog().getEvent();

		String messageKey = "sms.mtg.send.change";
		if (event.getEventType() == EventType.VOTE) {
			messageKey = "sms.vote.send.change";
		}
		return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
	}
}
