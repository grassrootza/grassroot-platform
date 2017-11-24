package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.Instant;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event<VoteContainer> {

	@ManyToOne
	@JoinColumn(name = "parent_meeting_id")
	protected Meeting parentMeeting;

	private Vote() {
		// for JPA
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent) {
		this(name, startDateTime, user, parent, false);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups) {
		this(name, startDateTime, user, parent, includeSubGroups, null);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups, String description) {
		super(startDateTime, user, parent, name, includeSubGroups, EventReminderType.DISABLED, 0, description, true, false);
		setParent(parent);
	}

	public boolean hasOption(String option) {
		if (getTags() != null) {
			for (String tag : getTags()) {
				if (tag.equalsIgnoreCase(option))
					return true;
			}
		}
		return false;
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

    @Override
    public TaskType getTaskType() {
        return TaskType.VOTE;
    }

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.VOTE;
	}

	public VoteContainer getParent() {
		if (parentGroup != null) {
			return parentGroup;
		} else if (parentTodo != null) {
			return parentTodo;
		} else if (parentMeeting != null) {
			return parentMeeting;
		} else {
			throw new IllegalStateException("There is no " + VoteContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

    public void setParent(VoteContainer parent) {
		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
		} else if (parent instanceof Todo) {
			this.parentTodo = (Todo) parent;
		} else if (parent instanceof Meeting) {
			this.parentMeeting = (Meeting) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
