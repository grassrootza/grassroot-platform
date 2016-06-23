package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("WELCOME")
public class WelcomeNotification extends UserNotification {

	private WelcomeNotification() {
		// for JPA
	}

	public WelcomeNotification(User target, String message, UserLog userLog) {
		super(target, message, userLog);
	}
}
