package za.org.grassroot.core.domain.task;

/**
 * Created by luke on 2015/07/16.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Event<P extends UidIdentifiable> extends AbstractEventEntity
		implements TodoContainer, Task<P>, Serializable, TagHolder {

	private static final Logger logger = LoggerFactory.getLogger(Event.class);

	@Column(name = "canceled")
	private boolean canceled;

    /*
	Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */
	@Version
	private Integer version;

	/*
	Used to see if reminders have already been sent for the event. It is not the number of messages
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

	// note : cannot call this "public" (reserved), cannot be set on a request entity, and is always false on
	// a vote, but need to use it on event specifications, hence defining here
	@Column(name="public")
	private boolean isPublic;

	// we use these just to simplify some internal methods, hence transient - actual logic is to persist via eventlogs
    @Transient
    private String imageUrl;

	public abstract EventType getEventType();

	protected Event() {
		// for JPA
	}

	protected Event(Instant eventStartDateTime, User user, UidIdentifiable parent, String name, boolean includeSubGroups,
					EventReminderType reminderType, int customReminderMinutes, String description, boolean rsvpRequired, boolean relayable) {
		super(name, eventStartDateTime, user, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
		this.canceled = false;
		this.isPublic = false;
		this.noRemindersSent = 0;

		this.ancestorGroup = parent.getThisOrAncestorGroup();
		this.ancestorGroup.addDescendantEvent(this); // note: without this JPA is not mapping both sides of the relationship

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

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean aPublic) {
		isPublic = aPublic;
	}

	public Instant getScheduledReminderTime() {
		return scheduledReminderTime;
	}

	public LocalDateTime getScheduledReminderTimeAtSAST() {
		return scheduledReminderTime == null ? null : scheduledReminderTime.atZone(DateTimeUtil.getSAST()).toLocalDateTime();
	}

	public boolean isScheduledReminderActive() {
		return scheduledReminderActive;
	}

	public void setScheduledReminderActive(boolean scheduledReminderActive) {
		this.scheduledReminderActive = scheduledReminderActive;
	}

	public void updateScheduledReminderTime() {
		Group group = getAncestorGroup();
		logger.debug("updating scheduled reminder time, type: {}, group minutes: {}", getReminderType(), group.getReminderMinutes());
		if (getReminderType().equals(EventReminderType.CUSTOM)) {
			this.scheduledReminderTime = getEventStartDateTime().minus(getCustomReminderMinutes(), ChronoUnit.MINUTES);
		} else if (getReminderType().equals(EventReminderType.GROUP_CONFIGURED) && group.getReminderMinutes() > 0) {
			this.scheduledReminderTime = getEventStartDateTime().minus(group.getReminderMinutes(), ChronoUnit.MINUTES);
		} else {
			this.scheduledReminderTime = null;
		}

		logger.debug("inside meeting, scheduled reminder time: {}", scheduledReminderTime);

        if (this.scheduledReminderTime != null) {
            this.scheduledReminderTime = DateTimeUtil.restrictToDaytime(this.scheduledReminderTime, this.eventStartDateTime,
					DateTimeUtil.getSAST());
	        if (this.scheduledReminderTime.isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
		        this.scheduledReminderActive = false; // disable it if it's already significantly past
	        }
        }
	}

    public boolean isHasImage() {
        return !StringUtils.isEmpty(imageUrl);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isHighImportance() {
	    return false;
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

	@Override
	public Integer getTodoReminderMinutes() {
		return EventReminderType.CUSTOM.equals(reminderType) ? customReminderMinutes : ancestorGroup.getReminderMinutes();
	}

	@Override
	public Set<Todo> getTodos() {
		if (todos == null) {
			todos = new HashSet<>();
		}
		return todos;
	}

	// STRANGE: dunno why this <User> generics is not recognized by rest of code!?
	public Set<User> getAllMembers() {
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
