package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LOG_BOOK_REMINDER")
public class LogBookReminderNotification extends LogBookNotification {
	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.LOG_BOOK_REMINDER;
	}

	private LogBookReminderNotification() {
		// for JPA
	}

	public LogBookReminderNotification(User target, String message, LogBookLog logBookLog) {
		super(target, message, logBookLog);
	}
}
