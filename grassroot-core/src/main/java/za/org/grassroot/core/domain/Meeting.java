package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

@Entity
@DiscriminatorValue("MEETING")
public class Meeting extends Event<MeetingContainer> implements VoteContainer {

	@Column(name = "location", length = 50)
	private String eventLocation;

	private Meeting() {
		// for JPA
	}

	public Meeting(String name, Timestamp startDateTime, User user, MeetingContainer parent, String eventLocation) {
		this(name, startDateTime, user, parent, eventLocation, false);
	}

	public Meeting(String name, Timestamp startDateTime, User user, MeetingContainer parent, String eventLocation, boolean includeSubGroups) {
		this(name, startDateTime, user, parent, eventLocation, includeSubGroups, false, false, EventReminderType.DISABLED, 0, null);
	}

	public Meeting(String name, Timestamp startDateTime, User user, MeetingContainer parent, String eventLocation, boolean includeSubGroups,
				   boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {
		super(startDateTime, user, parent, name, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
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

	public MeetingContainer getParent() {
		if (appliesToGroup != null) {
			return appliesToGroup;
		} else if (logBook != null) {
			return logBook;
		} else {
			throw new IllegalStateException("There is no " + MeetingContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

	public void setParent(MeetingContainer parent) {
		if (parent instanceof Group) {
			this.appliesToGroup = (Group) parent;
		} else if (parent instanceof LogBook) {
			this.logBook = (LogBook) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
