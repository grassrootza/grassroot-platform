package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("VOTE")
public class VoteRequest extends EventRequest {

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
