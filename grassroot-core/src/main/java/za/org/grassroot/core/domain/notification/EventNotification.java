package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import java.util.Objects;

@MappedSuperclass
public abstract class EventNotification extends Notification {
	@ManyToOne
	@JoinColumn(name = "event_id")
	private Event event;

	@Override
	public NotificationType getNotificationType() {
		return NotificationType.EVENT;
	}

	@Override
	public abstract NotificationDetailedType getNotificationDetailedType();

	protected EventNotification() {
		// for JPA
	}

	@Override
	protected void appendToString(StringBuilder sb) {
		sb.append(", event=").append(event);
	}

	protected EventNotification(User destination, String message, EventLog eventLog) {
        this(destination, message, eventLog, eventLog.getEvent());
    }

    protected EventNotification(User destination, String message, ActionLog actionLog, Event event) {
        super(destination, message, actionLog);
        this.event = Objects.requireNonNull(event);
	}

	public Event getEvent() {
		return event;
	}
}
