package za.org.grassroot.services;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface EventBroker {

	Event load(String eventUid);

	Meeting loadMeeting(String meetingUid);

	/**
	 * Returns a list of events which the user is participating in and/or a member of.
	 * @param userUid The user being queried
	 * @param eventType The event type to return (passing null returns any)
	 * @param createdEventsOnly Whether to find only events which the user created, or all those where they are a member
	 * @param futureEventsOnly Whether to fetch all events for the user or just those in the future
     * @return
     */
	List<Event> loadUserEvents(String userUid, EventType eventType, boolean createdEventsOnly, boolean futureEventsOnly);

	Meeting createMeeting(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime, String eventLocation,
						  boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
						  int customReminderMinutes, String description, Set<String> assignMemberUids);

	// for commonly updated fields (in particular, the only fields that can be changed via USSD)
	void updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation);

	// for changing all or most fields
	void updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation,
					   boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
					   int customReminderMinutes, String description);

	Vote createVote(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime,
					boolean includeSubGroups, boolean relayable, String description, Set<String> assignMemberUids);

    // votes cannot change topic or scope (groups included or not) after creation, just closing time & description field
    Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description);

	void updateReminderSettings(String userUid, String eventUid, EventReminderType reminderType, int customReminderMinutes);

	void cancel(String userUid, String eventUid);

	void sendScheduledReminder(String uid);

	void sendManualReminder(String userUid, String eventUid, String message);

	void sendMeetingRSVPsToDate(String meetingUid);

	void sendMeetingAcknowledgements(String meetingUid);

	void sendVoteResults(String voteUid);

	void assignMembers(String userUid, String eventUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String eventUid, Set<String> memberUids);
}
