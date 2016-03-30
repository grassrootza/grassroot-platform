package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

@Entity
@DiscriminatorValue("VOTE")
public class VoteRequest extends EventRequest<VoteContainer> {

	@ManyToOne
	@JoinColumn(name = "meeting_id")
	protected Meeting meeting;

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

	@Override
	public boolean isFilled() {
		if (getParent() == null) {
			return false;
		}
		return isFilledWithCommonFields();
	}

	private VoteRequest() {
		// for JPA
	}

	public static VoteRequest makeEmpty() {
		return makeEmpty(null, null);
	}

	public static VoteRequest makeEmpty(User user, VoteContainer parent) {
		VoteRequest request = new VoteRequest();
		request.reminderType = EventReminderType.DISABLED;
		request.uid = UIDGenerator.generateId();
		request.createdByUser = user;
		if (parent != null) request.setParent(parent);
		return request;
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
