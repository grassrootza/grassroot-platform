package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

@Entity
@DiscriminatorValue("VOTE")
public class VoteRequest extends EventRequest {

	@ManyToOne
	@JoinColumn(name = "meeting_id")
	protected Meeting meeting;

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

	@Override
	public boolean isFilled() {
		return isFilledWithCommonFields();
	}

	private VoteRequest() {
		// for JPA
	}

	public static VoteRequest makeEmpty() {
		return makeEmpty(null, null);
	}

	public static VoteRequest makeEmpty(User user, Group group) {
		VoteRequest request = new VoteRequest();
		request.reminderType = EventReminderType.DISABLED;
		request.uid = UIDGenerator.generateId();
		request.createdByUser = user;
		request.appliesToGroup = group;
		return request;
	}
}
