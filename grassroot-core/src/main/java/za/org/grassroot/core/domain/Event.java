package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 * <p/>
 * todo - aakil - add event duration
 */


import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Event<P extends UidIdentifiable> extends AbstractEventEntity
		implements TodoContainer, Task<P>, Serializable {

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

	@OneToMany(mappedBy = "parentEvent")
	private Set<Todo> todos = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "ancestor_group_id", nullable = false)
	private Group ancestorGroup;

	public abstract EventType getEventType();

	protected Event() {
		// for JPA
	}

	protected Event(Instant eventStartDateTime, User user, UidIdentifiable parent, String name, boolean includeSubGroups,
					boolean rsvpRequired, boolean relayable, EventReminderType reminderType, int customReminderMinutes, String description) {
		super(name, eventStartDateTime, user, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		this.canceled = false;
		this.noRemindersSent = 0;
		this.ancestorGroup = parent.getThisOrAncestorGroup();
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
		Group group = getAncestorGroup();
		if (getReminderType().equals(EventReminderType.CUSTOM)) {
			this.scheduledReminderTime = getEventStartDateTime().minus(getCustomReminderMinutes(), ChronoUnit.MINUTES);
		} else if (getReminderType().equals(EventReminderType.GROUP_CONFIGURED) && group.getReminderMinutes() > 0) {
			this.scheduledReminderTime = getEventStartDateTime().minus(group.getReminderMinutes(), ChronoUnit.MINUTES);
		} else {
			this.scheduledReminderTime = null;
		}

        if (this.scheduledReminderTime != null) {
            final ZoneId userZone = ZoneId.of("Africa/Johannesburg");
            ZonedDateTime currentDateTime = ZonedDateTime.ofInstant(this.scheduledReminderTime, userZone);
            if (currentDateTime.getHour() < 7) {
                // todo: there _must_ be an easier way to do this, but am getting confused by Java 8 zone offsets etc
                LocalDate reminderDate = currentDateTime.toLocalDate();
                LocalTime adjustedReminderTime = LocalTime.of(7, 0);
                ZonedDateTime revisedDateTime = ZonedDateTime.of(reminderDate, adjustedReminderTime, userZone);
                this.scheduledReminderTime = revisedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toInstant();
            }
        }
	}

	@Override
	public Set<User> fetchAssignedMembersCollection() {
		return assignedMembers;
	}

	@Override
	public Instant getDeadlineTime() {
		return eventStartDateTime;
	}

	@Override
	public void putAssignedMembersCollection(Set<User> assignedMembersCollection) {
		this.assignedMembers = assignedMembersCollection;
	}

	public Set<Todo> getTodos() {
		if (todos == null) {
			todos = new HashSet<>();
		}
		return todos;
	}

	// STRANGE: dunno why this <User> generics is not recognized by rest of code!?
	public Set<User> getAllMembers() {
		// todo: replace this with calling the parent and/or just using assigned members
		if (isIncludeSubGroups()) {
			return getAncestorGroup().getMembersWithChildrenIncluded();
		} else if (isAllGroupMembersAssigned()) {
			return getAncestorGroup().getMembers();
		} else {
			return getAssignedMembers();
		}
	}

	@Override
	public Group getAncestorGroup() {
		return ancestorGroup;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append("{id=").append(id);
		sb.append(", uid='").append(uid).append('\'');
		sb.append(", name='").append(name).append('\'');
		sb.append(", eventStartDateTime='").append(eventStartDateTime).append('\'');
		sb.append(", canceled=").append(canceled);
		sb.append(", includeSubGroups=").append(includeSubGroups);
		sb.append(", parent=").append(getParent());
		sb.append('}');
		return sb.toString();
	}
}
