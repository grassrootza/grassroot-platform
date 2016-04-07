package za.org.grassroot.services;

import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.MeetingRequest;
import za.org.grassroot.core.domain.VoteRequest;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public interface EventRequestBroker {
	EventRequest load(String eventRequestUid);

	MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid);

	VoteRequest createEmptyVoteRequest(String userUid, String groupUid);

	void updateName(String userUid, String eventRequestUid, String name);

	void updateEventDateTime(String userUid, String eventRequestUid, LocalDateTime eventDateTime);

	void updateMeetingLocation(String userUid, String meetingRequestUid, String location);

	String finish(String userUid, String eventRequestUid, boolean rsvpRequired);

	MeetingRequest createChangeRequest(String userUid, String meetingUid);

    void finishEdit(String userUid, String eventUid, String changeRequestUid);
}
