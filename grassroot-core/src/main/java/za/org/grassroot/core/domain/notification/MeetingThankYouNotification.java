package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MEETING_THANKYOU")
public class MeetingThankYouNotification extends EventNotification {

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.MEETING_THANKYOU;
	}

	private MeetingThankYouNotification() {
		// for JPA
	}

	public MeetingThankYouNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
