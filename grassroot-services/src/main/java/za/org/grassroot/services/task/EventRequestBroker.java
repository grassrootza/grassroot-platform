package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.EventRequest;
import za.org.grassroot.core.domain.task.MeetingRequest;
import za.org.grassroot.core.domain.task.VoteRequest;

import java.time.LocalDateTime;

public interface EventRequestBroker {

	EventRequest load(String eventRequestUid);

	MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid);

	VoteRequest createEmptyVoteRequest(String userUid, String groupUid);

	String createNewStyleEmptyVote(String userUid, String subject);

	void updateName(String userUid, String eventRequestUid, String name);

	void updateEventDateTime(String userUid, String eventRequestUid, LocalDateTime eventDateTime);

	void updateMeetingLocation(String userUid, String meetingRequestUid, String location);

	int addVoteOption(String userUid, String voteRequestUid, String voteOption);

	void updateVoteGroup(String userUid, String voteRequestUid, String groupUid);

	String finish(String userUid, String eventRequestUid, boolean rsvpRequired);

	MeetingRequest createChangeRequest(String userUid, String meetingUid);

    void finishEdit(String userUid, String eventUid, String changeRequestUid);
}
