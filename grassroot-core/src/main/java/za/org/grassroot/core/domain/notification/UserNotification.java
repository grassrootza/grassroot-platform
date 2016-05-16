package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class UserNotification extends Notification {
	protected UserNotification() {
	}

	protected UserNotification(User target, String message, UserLog userLog) {
		super(target, message, userLog);
	}

	@Override
	public NotificationType getNotificationType() {
		return NotificationType.USER;
	}

	@Override
	protected void appendToString(StringBuilder sb) {
	}
}
