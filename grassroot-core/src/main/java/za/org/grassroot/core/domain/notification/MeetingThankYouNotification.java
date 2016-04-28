package za.org.grassroot.core.domain.notification;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.time.format.DateTimeFormatter;

@Entity
@DiscriminatorValue("MEETING_THANKYOU")
public class MeetingThankYouNotification extends EventNotification {
	private static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

	private MeetingThankYouNotification() {
		// for JPA
	}

	public MeetingThankYouNotification(User target, String message, EventLog eventLog) {
		super(target, message, eventLog);
	}
}
