package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_INFO")
public class EventInfoNotification extends EventNotification {

	private EventInfoNotification() {
		// for JPA
	}

	public EventInfoNotification(User target, String message, GroupLog groupLog, Event event) {
		super(target, message, groupLog, event);
	}

	public EventInfoNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
