package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("TODO_REMINDER")
public class TodoReminderNotification extends TodoNotification {
	private TodoReminderNotification() {
		// for JPA
	}
	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.TODO_REMINDER;
	}
	public TodoReminderNotification(User target, String message, TodoLog todoLog) {
		super(target, message, todoLog);
	}
}
