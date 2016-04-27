package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LOG_BOOK_REMINDER")
public class LogBookReminderNotification extends LogBookNotification {
	private LogBookReminderNotification() {
		// for JPA
	}

	public LogBookReminderNotification(User user, String message, LogBookLog logBookLog) {
		super(user, message, logBookLog);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return message;
	}
}
