package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

public class LogBookNotification extends Notification {
	public LogBookNotification(User user, LogBookLog logBookLog, String message) {
		super(user, logBookLog, NotificationType.LOGBOOK);
		this.message = message;
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return message;
	}
}
