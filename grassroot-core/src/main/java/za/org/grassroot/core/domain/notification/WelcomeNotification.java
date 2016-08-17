package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("WELCOME")
public class WelcomeNotification extends UserNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.WELCOME;
	}

	private WelcomeNotification() {
		// for JPA
	}

	public WelcomeNotification(User target, String message, UserLog userLog) {
		super(target, message, userLog);
	}
}
