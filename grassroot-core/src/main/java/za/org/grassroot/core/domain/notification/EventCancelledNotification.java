package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_CANCELLED")
public class EventCancelledNotification extends EventNotification {
	private EventCancelledNotification() {
		// for JPA
	}

	public EventCancelledNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
