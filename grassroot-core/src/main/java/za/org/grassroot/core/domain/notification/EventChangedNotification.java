package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_CHANGED")
public class EventChangedNotification extends EventNotification {
	private EventChangedNotification() {
		// for JPA
	}

	public EventChangedNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
