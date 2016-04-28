package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MEETING_RSVP_TOTALS")
public class MeetingRsvpTotalsNotification extends EventNotification {
	private MeetingRsvpTotalsNotification() {
		// for JPA
	}

	public MeetingRsvpTotalsNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
