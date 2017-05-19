package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.EventLog;
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
		this(destination, message, eventLog, eventLog.getEvent(), true);
	}

	protected EventNotification(User destination, String message, ActionLog actionLog, Event event, boolean forAndroidTL) {
		super(destination, message, actionLog, forAndroidTL);
		this.event = Objects.requireNonNull(event);
	}

	public Event getEvent() {
		return event;
	}
}
