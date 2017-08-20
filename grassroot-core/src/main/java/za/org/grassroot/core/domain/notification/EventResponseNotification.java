package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_RESPONSE")
public class EventResponseNotification extends EventNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.EVENT_INFO;
	}

	private EventResponseNotification() {
		// for JPA
	}

	public EventResponseNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
		this.priority = AlertPreference.NOTIFY_EVERYTHING.getPriority(); // i.e., lowest priority
	}
}
