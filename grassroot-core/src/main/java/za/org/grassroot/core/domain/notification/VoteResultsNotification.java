package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("VOTE_RESULTS")
public class VoteResultsNotification extends EventNotification {
	private VoteResultsNotification() {
		// for JPA
	}

	public VoteResultsNotification(User target, EventLog eventLog) {
		super(target, null, eventLog);
	}

	protected String constructMessageText(MessageSourceAccessor messageSourceAccessor) {
		return null;
	}
}
