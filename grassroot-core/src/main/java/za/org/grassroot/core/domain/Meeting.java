package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

@Entity
@DiscriminatorValue("MEETING")
public class Meeting extends Event {

	@Column(name = "location", length = 50)
	private String eventLocation;

	private Meeting() {
		// for JPA
	}

	public Meeting(String name, User user, Group group) {
		this(name, user, group, false);
	}

	public Meeting(String name, User user, Group group, boolean includeSubGroups) {
		super(Timestamp.from(Instant.now()), null, user, group, false, name, includeSubGroups, 0, false, false, false);
		this.eventLocation = Objects.requireNonNull(eventLocation);
	}

	@Override
	public EventType getEventType() {
		return EventType.MEETING;
	}

	public String getEventLocation() {
		return eventLocation;
	}

	public void setEventLocation(String eventLocation) {
		this.eventLocation = eventLocation;
	}
}
