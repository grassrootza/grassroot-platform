package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("VOTE")
public class MeetingRequest extends EventRequest {
	@Column(name = "location", length = 50)
	private String eventLocation;

	@Override
	public EventType getEventType() {
		return EventType.MEETING;
	}

	private MeetingRequest() {
		// for JPA
	}

	public static MeetingRequest makeEmpty() {
		MeetingRequest request = new MeetingRequest();
		request.reminderType = EventReminderType.DISABLED;
		request.uid = UIDGenerator.generateId();
		return request;
	}

	public String getEventLocation() {
		return eventLocation;
	}
}
