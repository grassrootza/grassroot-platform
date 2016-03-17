package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.sql.Timestamp;
import java.util.Objects;

@Entity
@DiscriminatorValue("MEETING")
public class Meeting extends Event {

	@Column(name = "location", length = 50)
	private String eventLocation;

	private Meeting() {
		// for JPA
	}

	public Meeting(String name, Timestamp startDateTime, User user, Group group, String eventLocation) {
		this(name, startDateTime, user, group, eventLocation, false);
	}

	public Meeting(String name, Timestamp startDateTime, User user, Group group, String eventLocation, boolean includeSubGroups) {
		this(name, startDateTime, user, group, eventLocation, includeSubGroups, false, false, EventReminderType.DISABLED, 0);
	}

	public Meeting(String name, Timestamp startDateTime, User user, Group group, String eventLocation, boolean includeSubGroups,
				   boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes) {
		super(startDateTime, user, group, name, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes);
		this.eventLocation = Objects.requireNonNull(eventLocation);
		updateScheduledReminderTime();
		setScheduledReminderActive(true);
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
