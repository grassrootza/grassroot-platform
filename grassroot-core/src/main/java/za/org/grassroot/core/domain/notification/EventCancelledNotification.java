package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import java.util.Locale;

public class EventCancelledNotification extends Notification {
	public EventCancelledNotification(User user, EventLog eventLog) {
		super(user, eventLog, NotificationType.EVENT);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		Locale locale = getUserLocale();
		String messageKey = "sms.mtg.send.cancel";
		if (getEventLog().getEvent().getEventType() == EventType.VOTE) {
			messageKey = "sms.vote.send.cancel";
		}
		return messageSourceAccessor.getMessage(messageKey, populateEventFields(getEventLog().getEvent()), locale);
	}
}
