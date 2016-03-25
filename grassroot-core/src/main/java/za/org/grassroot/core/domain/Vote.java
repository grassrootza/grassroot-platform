package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.sql.Timestamp;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event {

	@ManyToOne
	@JoinColumn(name = "meeting_id")
	protected Meeting meeting;

	private Vote() {
		// for JPA
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group) {
		this(name, startDateTime, user, group, false);
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group, boolean includeSubGroups) {
		this(name, startDateTime, user, group, includeSubGroups, false, null);
	}

	public Vote(String name, Timestamp startDateTime, User user, Group group, boolean includeSubGroups, boolean relayable, String description) {
		super(startDateTime, user, group, name, includeSubGroups, true, relayable, EventReminderType.DISABLED, 0, description);
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.VOTE;
	}

	public VoteContainer getParent() {
		if (appliesToGroup != null) {
			return appliesToGroup;
		} else if (logBook != null) {
			return logBook;
		} else if (meeting != null) {
			return meeting;
		} else {
			throw new IllegalStateException("There is no " + VoteContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

	public void setParent(VoteContainer parent) {
		if (parent instanceof Group) {
			this.appliesToGroup = (Group) parent;
		} else if (parent instanceof LogBook) {
			this.logBook = (LogBook) parent;
		} else if (parent instanceof Meeting) {
			this.meeting = (Meeting) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
