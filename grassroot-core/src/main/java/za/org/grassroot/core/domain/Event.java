package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 * <p/>
 * Major todo: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * todo - aakil - add event duration
 */


import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;

@Entity
@Table(name = "event")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Event extends AbstractEventEntity implements Serializable {

	@Column(name = "canceled")
	private boolean canceled;

    /*
	Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */

	@Version
	private Integer version;

	/*
	Used to see if reminders have allready been sent for the event. It is not the number of messages
	sent but rather how many times we have sent reminders to participants.
	At the moment we only send once but thought in the future we might send more than once
	therefore the number rather than a boolean.

	N.B. will use this field for voting notification as well to determine if we already sent out the
	the vote results.
	 */
	@Column(name = "noreminderssent")
	private int noRemindersSent;

	public abstract EventType getEventType();

	protected Event() {
		// for JPA
	}

/*
	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired, boolean relayable) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.Meeting;
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.relayable = relayable;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.includeSubGroups = includeSubGroups;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.sendBlocked = false;
	}

	public Event(User createdByUser, EventType eventType, boolean rsvpRequired) {
		this.createdByUser = createdByUser;
		this.eventType = eventType;
		this.rsvpRequired = rsvpRequired;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.rsvpRequired = true; // this is our default
		this.sendBlocked = false;
	}

	public Event(User createdByUser, EventType eventType) {
		this.createdByUser = createdByUser;
		this.eventType = eventType;
		this.sendBlocked = false;
	}
*/

	protected Event(Timestamp eventStartDateTime, User user, Group group, boolean canceled, String name, boolean includeSubGroups,
					boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes) {
		super(name, eventStartDateTime, user, group, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes);
		this.canceled = canceled;
		this.noRemindersSent = 0;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public int getNoRemindersSent() {
		return noRemindersSent;
	}

	public void setNoRemindersSent(int noRemindersSent) {
		this.noRemindersSent = noRemindersSent;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}

	public boolean isCanceled() {
		return canceled;
	}

	@PreUpdate
	@PrePersist
	public void updateTimeStamps() {
		if (createdDateTime == null) {
			createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Event event = (Event) o;

		if (uid != null ? !uid.equals(event.uid) : event.uid != null) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("Event{");
		sb.append("id=").append(id);
		sb.append(", uid='").append(uid).append('\'');
		sb.append(", createdDateTime=").append(createdDateTime);
		sb.append(", canceled=").append(canceled);
		sb.append(", version=").append(version);
		sb.append(", noRemindersSent=").append(noRemindersSent);
		sb.append('}');
		return sb.toString();
	}
}
