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
     * @return The list of events which the user is part of
     */
	List<Event> loadUserEvents(String userUid, EventType eventType, boolean createdEventsOnly, boolean futureEventsOnly);

	/**
	 * Create a meeting entity, also triggering notifications to be stored, and subsequently sent
	 * @param userUid The UID of the user who is calling the meeting
	 * @param parentUid The UID of the parent entity (group/meeting/etc)
	 * @param parentType The type of the parent entity
	 * @param name The "name", or "title", of the meeting (this will be included in the SMS/short notification that is sent)
	 * @param eventStartDateTime The date and time when the meeting will take place
	 * @param eventLocation The location of the meeting
	 * @param includeSubGroups Whether the meeting notifications should be sent to the members of all subgroups (ignored if the parent is not a group)
	 * @param rsvpRequired Whether to ask members to confirm attendance or not. Almost always true.
	 * @param relayable Whether the meeting invite can be relayed to other people. Currently not implemented.
	 * @param reminderType The type of reminder time (group default / no reminder / custom time)
	 * @param customReminderMinutes If the reminder type is custom, the number of minutes in advance to send the reminder
	 * @param description An optional longer description -- can be null
     * @param assignMemberUids The UIDs of the assigned members. If an empty set, then all members in the parent entity will be assigned.
     * @return
     */
	Meeting createMeeting(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime, String eventLocation,
						  boolean includeSubGroups, boolean rsvpRequired, boolean relayable, EventReminderType reminderType,
						  int customReminderMinutes, String description, Set<String> assignMemberUids);

	// for commonly updated fields (in particular, the only fields that can be changed via USSD)
	void updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation);

	// for changing all or most fields
	void updateMeeting(String userUid, String meetingUid, String name, String description, LocalDateTime eventStartDateTime, String eventLocation,
	                   EventReminderType reminderType, int customReminderMinutes, Set<String> assignedMemberUids);

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
