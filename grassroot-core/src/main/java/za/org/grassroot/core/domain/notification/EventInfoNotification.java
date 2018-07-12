package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.AlertPreference;
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

	@Override
	public User getSender() {
		return getEventLog().getUser();
	}

	public EventInfoNotification(User target, String message, GroupLog groupLog, Event event) {
        super(target, message, groupLog, event);
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority();
	}

	public EventInfoNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
		this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority();
	}
}
