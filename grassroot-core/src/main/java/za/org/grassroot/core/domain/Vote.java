package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.sql.Timestamp;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event {

	private Vote() {
		// for JPA
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group) {
		this(name, startDateTime, user, group, false);
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group, boolean includeSubGroups) {
		this(name, startDateTime, user, group, includeSubGroups, false, false, false, EventReminderType.DISABLED, 0);
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group, boolean includeSubGroups,
				boolean canceled, boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes) {
		super(startDateTime, user, group, canceled, name, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes);
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}
}
