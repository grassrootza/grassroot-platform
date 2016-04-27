package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.Locale;

@Entity
@DiscriminatorValue("EVENT_CANCELLED")
public class EventCancelledNotification extends EventNotification {
	private EventCancelledNotification() {
		// for JPA
	}

	public EventCancelledNotification(User target, EventLog eventLog) {
		super(target, null, eventLog);
	}

	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		Locale locale = getUserLocale();
		String messageKey = "sms.mtg.send.cancel";
		if (getEventLog().getEvent().getEventType() == EventType.VOTE) {
			messageKey = "sms.vote.send.cancel";
		}
		return messageSourceAccessor.getMessage(messageKey, populateEventFields(getEventLog().getEvent()), locale);
	}
}
