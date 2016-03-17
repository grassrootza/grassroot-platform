package za.org.grassroot.services;

import za.org.grassroot.core.domain.MeetingRequest;
import za.org.grassroot.core.domain.VoteRequest;

import java.sql.Timestamp;

public interface EventRequestBroker {
	MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid);

	VoteRequest createEmptyVoteRequest(String userUid, String groupUid);

	void updateName(String userUid, String eventRequestUid, String name);

	void updateStartTimestamp(String userUid, String eventRequestUid, Timestamp startTimestamp);

	void updateMeetingLocation(String userUid, String meetingRequestUid, String location);

	void finish(String userUid, String eventRequestUid);
}
