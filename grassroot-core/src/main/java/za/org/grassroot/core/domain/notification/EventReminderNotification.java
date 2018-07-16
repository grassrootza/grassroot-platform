package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_REMINDER")
public class EventReminderNotification extends EventNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return null;
	}

	@Override
	public User getSender() {
		return getEventLog().getUser();
	}

	private EventReminderNotification() {
		// for JPA
	}

	public EventReminderNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
