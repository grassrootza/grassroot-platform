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
		this(name, startDateTime, user, group, includeSubGroups, false);
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group, boolean includeSubGroups, boolean relayable) {
		super(startDateTime, user, group, name, includeSubGroups, true, relayable, EventReminderType.DISABLED, 0);
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}
}
