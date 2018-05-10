package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("TODO_CANCELLED")
public class TodoCancelledNotification extends TodoNotification {
	private TodoCancelledNotification() {
		// for JPA
	}

	@Override
	public User getSender() {
		return getTodoLog().getUser();
	}

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.TODO_CANCELLED;
	}

	public TodoCancelledNotification(User target, String message, TodoLog todoLog) {
		super(target, message, todoLog);
		this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority();
	}
}
