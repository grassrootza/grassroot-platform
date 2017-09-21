package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("VOTE_RESULTS")
public class VoteResultsNotification extends EventNotification {
	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.VOTE_RESULTS;
	}

	private VoteResultsNotification() {
		// for JPA
	}

	public VoteResultsNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
