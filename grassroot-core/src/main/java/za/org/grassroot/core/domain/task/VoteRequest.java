package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.*;
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
	@JoinColumn(name = "parent_meeting_id")
	protected Meeting parentMeeting;

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

	@Override
	public boolean isFilled() {
		return getParent() != null && isFilledWithCommonFields();
	}

	private VoteRequest() {
		// for JPA
	}

	public VoteRequest(User creatingUser, String subject) {
		this.uid = UIDGenerator.generateId();
		this.createdByUser = creatingUser;
		this.name = subject;
		this.reminderType = EventReminderType.DISABLED;
		this.tags = new String[0];
	}

	public static VoteRequest makeEmpty() {
		return makeEmpty(null, null);
	}

	public static VoteRequest makeEmpty(User user, VoteContainer parent) {
		VoteRequest request = new VoteRequest();
		request.reminderType = EventReminderType.DISABLED;
		request.uid = UIDGenerator.generateId();
		request.createdByUser = user;
		request.tags = new String[0];
		if (parent != null) request.setParent(parent);
		return request;
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
