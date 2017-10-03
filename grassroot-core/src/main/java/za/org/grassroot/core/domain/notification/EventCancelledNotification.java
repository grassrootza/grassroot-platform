package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_CANCELLED")
public class EventCancelledNotification extends EventNotification {
	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.EVENT_CANCELLED;
	}

	private EventCancelledNotification() {
		// for JPA
	}

	public EventCancelledNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
		this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority(); // since a cancel is pretty important ...
	}
}
