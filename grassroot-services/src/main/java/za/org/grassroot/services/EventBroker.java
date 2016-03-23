package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.Vote;
import za.org.grassroot.core.enums.EventType;

import java.sql.Timestamp;
import java.util.List;

public interface EventBroker {

	Event load(String eventUid);

	Meeting loadMeeting(String meetingUid);

	List<Event> loadEventsUserCanManage(String userUid, EventType eventType, int pageNumber, int pageSize);

	Meeting createMeeting(String userUid, String groupUid, String name, Timestamp eventStartDateTime, String eventLocation,
						  boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
						  int customReminderMinutes, String description);

	// for commonly updated fields (in particular, the only fields that can be changed via USSD)
	void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime, String eventLocation);

	// for changing all or most fields
	void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime, String eventLocation,
					   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
					   int customReminderMinutes, String description);

	Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime,
					boolean includeSubGroups, boolean relayable, String description);

    // votes cannot change topic or scope (groups included or not) after creation, just closing time & description field
    Vote updateVote(String userUid, String voteUid, Timestamp eventStartDateTime, String description);

	void updateReminderSettings(String userUid, String eventUid, EventReminderType reminderType, int customReminderMinutes);

	void cancel(String userUid, String eventUid);

	void sendScheduledReminders();

	void sendManualReminder(String userUid, String eventUid, String message);

	void sendVoteResults();

}
