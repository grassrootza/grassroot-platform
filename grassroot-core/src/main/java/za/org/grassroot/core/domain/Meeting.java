package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

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
		this(name, startDateTime, user, group, eventLocation, includeSubGroups, false, false, EventReminderType.DISABLED, 0, null);
	}

	public Meeting(String name, Timestamp startDateTime, User user, Group group, String eventLocation, boolean includeSubGroups,
				   boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {
		super(startDateTime, user, group, name, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		this.eventLocation = Objects.requireNonNull(eventLocation);
		setScheduledReminderActive(true);
	}

	public static Meeting makeEmpty(User user) {
		Meeting meeting = new Meeting();
		meeting.uid = UIDGenerator.generateId();
		meeting.setCreatedByUser(user);
		return meeting;
	}

	@Override
	public EventType getEventType() {
		return EventType.MEETING;
	}

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.MEETING;
	}

	public String getEventLocation() {
		return eventLocation;
	}

	public void setEventLocation(String eventLocation) {
		this.eventLocation = eventLocation;
	}
}
