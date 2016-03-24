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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Event extends AbstractEventEntity implements AssignedMembersContainer, Serializable {

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

	@Column(name = "scheduled_reminder_time")
	private Instant scheduledReminderTime;

	@Column(name = "scheduled_reminder_active")
	private boolean scheduledReminderActive = false;

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "event_assigned_members",
			joinColumns = @JoinColumn(name = "event_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "user_id", nullable = false)
	)
	private Set<User> assignedMembers = new HashSet<>();

	public abstract EventType getEventType();

	protected Event() {
		// for JPA
	}

	protected Event(Timestamp eventStartDateTime, User user, Group group, String name, boolean includeSubGroups,
					boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {
		super(name, eventStartDateTime, user, group, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		this.canceled = false;
		this.noRemindersSent = 0;
		updateScheduledReminderTime();
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

	public Instant getScheduledReminderTime() {
		return scheduledReminderTime;
	}

	public boolean isScheduledReminderActive() {
		return scheduledReminderActive;
	}

	public void setScheduledReminderActive(boolean scheduledReminderActive) {
		this.scheduledReminderActive = scheduledReminderActive;
	}

	public void updateScheduledReminderTime() {
		if (getReminderType().equals(EventReminderType.CUSTOM)) {
			this.scheduledReminderTime = getEventStartDateTime().toInstant().minus(getCustomReminderMinutes(), ChronoUnit.MINUTES);

		} else if (getReminderType().equals(EventReminderType.GROUP_CONFIGURED) && getAppliesToGroup().getReminderMinutes() > 0) {
			this.scheduledReminderTime = getEventStartDateTime().toInstant().minus(getAppliesToGroup().getReminderMinutes(), ChronoUnit.MINUTES);

		} else {
			this.scheduledReminderTime = null;
		}
	}

	@Override
	public Set<User> fetchAssignedMembersCollection() {
		return assignedMembers;
	}

	@Override
	public void putAssignedMembersCollection(Set<User> assignedMembersCollection) {
		this.assignedMembers = assignedMembersCollection;
	}

	@Override
	public Group getGroup() {
		return appliesToGroup;
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
