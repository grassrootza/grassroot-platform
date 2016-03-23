package za.org.grassroot.services;

import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.MeetingRequest;
import za.org.grassroot.core.domain.VoteRequest;

import java.sql.Timestamp;

public interface EventRequestBroker {
	EventRequest load(String eventRequestUid);

	MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid);

	VoteRequest createEmptyVoteRequest(String userUid, String groupUid);

	void updateName(String userUid, String eventRequestUid, String name);

	void updateStartTimestamp(String userUid, String eventRequestUid, Timestamp startTimestamp);

	void updateMeetingLocation(String userUid, String meetingRequestUid, String location);

	void finish(String userUid, String eventRequestUid, boolean rsvpRequired);

	MeetingRequest createChangeRequest(String userUid, String meetingUid);

    void finishEdit(String userUid, String eventUid, String changeRequestUid);
}
