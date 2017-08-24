package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_CHANGED")
public class EventChangedNotification extends EventNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.EVENT_CHANGED;
	}

	private EventChangedNotification() {
		// for JPA
	}

	public EventChangedNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
