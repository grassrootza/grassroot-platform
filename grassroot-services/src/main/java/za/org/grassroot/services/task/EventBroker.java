package za.org.grassroot.services.task;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.services.enums.EventListTimeType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EventBroker {

	Event load(String eventUid);

	Meeting loadMeeting(String meetingUid);

	/**
	 * Create a meeting entity, also triggering notifications to be stored, and subsequently sent
	 * @param userUid The UID of the user who is calling the meeting
	 * @param parentUid The UID of the parent entity (group/meeting/etc)
	 * @param parentType The type of the parent entity
	 * @param name The "name", or "title", of the meeting (this will be included in the SMS/short notification that is sent)
	 * @param eventStartDateTime The date and time when the meeting will take place
	 * @param eventLocation The location of the meeting
	 * @param includeSubGroups Whether the meeting notifications should be sent to the members of all subgroups (ignored if the parent is not a group)
	 * @param reminderType The type of reminder time (group default / no reminder / custom time)
	 * @param customReminderMinutes If the reminder type is custom, the number of minutes in advance to send the reminder
	 * @param description An optional longer description -- can be null
     * @param assignMemberUids The UIDs of the assigned members. If an empty set, then all members in the parent entity will be assigned.
     * @param importance
	 * @return
     */
	Meeting createMeeting(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime, String eventLocation,
						  boolean includeSubGroups, EventReminderType reminderType, int customReminderMinutes, String description,
						  Set<String> assignMemberUids, MeetingImportance importance);

	// for commonly updated fields (in particular, the only fields that can be changed via USSD) -- returns true if field actually changed ...
	boolean updateMeeting(String userUid, String meetingUid, String name, LocalDateTime eventStartDateTime, String eventLocation);

	// for changing all or most fields
	void updateMeeting(String userUid, String meetingUid, String name, String description, LocalDateTime eventStartDateTime, String eventLocation,
	                   EventReminderType reminderType, int customReminderMinutes, Set<String> assignedMemberUids);

	Vote createVote(String userUid, String parentUid, JpaEntityType parentType, String name, LocalDateTime eventStartDateTime,
					boolean includeSubGroups, String description, Set<String> assignMemberUids);

    // votes cannot change topic or scope (groups included or not) after creation, just closing time & description field
    Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description);

	void updateVoteClosingTime(String userUid, String eventUid, LocalDateTime closingDateTime);

    void updateReminderSettings(String userUid, String eventUid, EventReminderType reminderType, int customReminderMinutes);

	void updateDescription(String userUid, String eventUid, String eventDescription);

	void cancel(String userUid, String eventUid);

	void sendScheduledReminder(String uid);

	void sendManualReminder(String userUid, String eventUid);

	void sendMeetingRSVPsToDate(String meetingUid);

	void sendMeetingAcknowledgements(String meetingUid);

	void sendVoteResults(String voteUid);

	void assignMembers(String userUid, String eventUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String eventUid, Set<String> memberUids);

	/*
	Methods for retrieving lists of upcoming events (all read only)
	 */

	List<Event> getOutstandingResponseForUser(User user, EventType eventType);

	boolean userHasResponsesOutstanding(User user, EventType eventType);

	EventListTimeType userHasEventsToView(User user, EventType type);

	boolean userHasEventsToView(User user, EventType type, EventListTimeType timeType);

	Map<User, EventRSVPResponse> getRSVPResponses(Event event);

	Page<Event> getEventsUserCanView(User user, EventType eventType, EventListTimeType timeType, int pageNumber, int pageSize);

	// pass null to eventType to get all events, and null to either of the timestamps to leave unlimited (i.e., all the way to future, or all the way to past
	List<Event> retrieveGroupEvents(Group group, EventType eventType, Instant periodStart, Instant periodEnd);

	Event getMostRecentEvent(String groupUid);
}
