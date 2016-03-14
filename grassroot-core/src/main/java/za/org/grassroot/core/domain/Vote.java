package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event {

	private Vote() {
		// for JPA
	}

	public Vote(String name, User createdByUser, Group appliesToGroup, boolean rsvpRequired) {
		super(Timestamp.from(Instant.now()), null, createdByUser, appliesToGroup, false, name, false, 0, rsvpRequired, false, false);
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}
}
