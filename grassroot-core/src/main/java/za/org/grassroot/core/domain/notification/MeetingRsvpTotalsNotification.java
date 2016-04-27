package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.Objects;

@Entity
@DiscriminatorValue("MEETING_RSVP_TOTALS")
public class MeetingRsvpTotalsNotification extends EventNotification {
	private MeetingRsvpTotalsNotification() {
		// for JPA
	}

	public MeetingRsvpTotalsNotification(User user, EventLog eventLog, String message) {
		super(user, message, eventLog);
	}

	@Override
	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return message;
	}
}
