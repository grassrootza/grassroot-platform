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

	void updateMeeting(String userUid, String meetingUid, String name, Timestamp eventStartDateTime, String eventLocation,
					   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
					   int customReminderMinutes, String description);

	Vote createVote(String userUid, String groupUid, String name, Timestamp eventStartDateTime,
					boolean includeSubGroups, boolean relayable, String description);

	void cancel(String userUid, String eventUid);

	void updateName(String userUid, String eventUid, String name, boolean sendNotifications);

	void updateStartTimestamp(String userUid, String eventUid, Timestamp eventStartDateTime, boolean sendNotifications);

	void updateMeetingLocation(String userUid, String meetingUid, String eventLocation, boolean sendNotifications);

	void sendChangeNotifications(String eventUid, boolean startDateTimeChanged);

	void sendScheduledReminders();

	void sendManualReminder(String userUid, String eventUid, String message);

	void sendVoteResults();

}
