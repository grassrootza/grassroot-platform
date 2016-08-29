package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("EVENT_INFO")
public class EventInfoNotification extends EventNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.EVENT_INFO;
	}

	private EventInfoNotification() {
		// for JPA
	}

	public EventInfoNotification(User target, String message, GroupLog groupLog, Event event) {
		super(target, message, groupLog, event, true);
	}

	public EventInfoNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
