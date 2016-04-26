package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

import java.util.Objects;

public class MeetingRsvpTotalsNotification extends Notification {

	public MeetingRsvpTotalsNotification(User user, EventLog eventLog, String message) {
		super(user, eventLog, NotificationType.EVENT);
		this.message = Objects.requireNonNull(message);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return message;
	}
}
