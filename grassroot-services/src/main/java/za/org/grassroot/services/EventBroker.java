package za.org.grassroot.services;

import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.Vote;

import java.sql.Timestamp;

public interface EventBroker {
	Meeting createMeeting(String userUid, String groupUid, String name, Timestamp eventStartDateTime, String eventLocation,
				   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
				   int customReminderMinutes);

	void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime, String eventLocation,
					   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
					   int customReminderMinutes);

	Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime,
					   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
					   int customReminderMinutes);

	void cancel(String userUid, String eventUid);

	void updateName(String userUid, String eventUid, String name);

	void updateStartTimestamp(String userUid, String eventUid, Timestamp eventStartDateTime);

	void updateMeetingLocation(String userUid, String meetingUid, String eventLocation);

	void sendScheduledReminders();

	void sendManualReminder(String userUid, String eventUid, String message);
}
