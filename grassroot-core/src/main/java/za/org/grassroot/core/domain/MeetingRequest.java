package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MEETING")
public class MeetingRequest extends EventRequest {
	@Column(name = "location", length = 50)
	private String eventLocation;

	@Override
	public EventType getEventType() {
		return EventType.MEETING;
	}

	@Override
	public boolean isFilled() {
		if (eventLocation == null || eventLocation.trim().equals("")) {
			return false;
		}
		return isFilledWithCommonFields();
	}

	private MeetingRequest() {
		// for JPA
	}

	public static MeetingRequest makeEmpty() {
		return makeEmpty(null, null);
	}

	public static MeetingRequest makeEmpty(User user, Group group) {
		MeetingRequest request = new MeetingRequest();
		request.reminderType = EventReminderType.DISABLED;
		request.uid = UIDGenerator.generateId();
		request.createdByUser = user;
		request.appliesToGroup = group;
		return request;
	}

	public static MeetingRequest makeCopy(Meeting meeting) {
		MeetingRequest request = new MeetingRequest();
		request.uid = UIDGenerator.generateId(); // be careful not confuse with original meeting's Uid
		request.name = meeting.getName();
        request.appliesToGroup = meeting.getAppliesToGroup();
        request.createdByUser = meeting.getCreatedByUser();
        request.eventStartDateTime = meeting.getEventStartDateTime();
        request.eventLocation = meeting.getEventLocation();
        request.reminderType = meeting.getReminderType();
        request.customReminderMinutes = meeting.getCustomReminderMinutes();
        request.description = meeting.getDescription();
        return request;
	}

	public String getEventLocation() {
		return eventLocation;
	}

	public void setEventLocation(String eventLocation) {
		this.eventLocation = eventLocation;
	}
}
