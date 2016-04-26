package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;

import java.util.Locale;

public class EventReminderNotification extends Notification {

	public EventReminderNotification(User user, EventLog eventLog) {
		super(user, eventLog, NotificationType.EVENT);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		// if there is 'message' set in EventLog, it is treated as manually set content,
		// otherwise we construct it automatically
		String message = getEventLog().getMessage();
		if (message == null) {
			Locale locale = getUserLocale();
			String messageKey = "sms.mtg.send.reminder";
			Event event = getEventLog().getEvent();
			if (event.getEventType() == EventType.VOTE) {
				messageKey = "sms.vote.send.reminder";
			}
			return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);

		} else {
			return message;
		}
	}
}
