package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

public class VoteResultsNotification extends Notification {
	public VoteResultsNotification(User user, EventLog eventLog) {
		super(user, eventLog, NotificationType.EVENT);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return null;
	}
}
