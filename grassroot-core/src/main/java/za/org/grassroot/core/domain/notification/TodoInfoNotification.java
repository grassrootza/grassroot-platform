package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LOG_BOOK_INFO")
public class TodoInfoNotification extends TodoNotification {
	private TodoInfoNotification() {
		// for JPA
	}
	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return null;
	}
	public TodoInfoNotification(User target, String message, TodoLog todoLog) {
		super(target, message, todoLog);
	}
}
