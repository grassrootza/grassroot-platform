package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LOG_BOOK_INFO")
public class LogBookInfoNotification extends LogBookNotification {
	private LogBookInfoNotification() {
		// for JPA
	}

	public LogBookInfoNotification(User target, String message, LogBookLog logBookLog) {
		super(target, message, logBookLog);
	}
}
