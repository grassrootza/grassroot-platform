package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.EventRequestRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.exception.EventRequestNotFilledException;

import java.sql.Timestamp;
import java.util.Objects;

@Service
public class EventRequestBrokerImpl implements EventRequestBroker {
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EventRequestRepository eventRequestRepository;
	@Autowired
	private EventBroker eventBroker;

	@Override
	@Transactional
	public MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		MeetingRequest request = MeetingRequest.makeEmpty(user, group);

		return eventRequestRepository.save(request);
	}

	@Override
	@Transactional
	public VoteRequest createEmptyVoteRequest(String userUid, String groupUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		VoteRequest request = VoteRequest.makeEmpty(user, group);

		return eventRequestRepository.save(request);
	}

	@Override
	@Transactional
	public void updateName(String userUid, String eventRequestUid, String name) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);
		Objects.requireNonNull(name);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		request.setName(name);
	}

	@Override
	@Transactional
	public void updateStartTimestamp(String userUid, String eventRequestUid, Timestamp startTimestamp) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);
		Objects.requireNonNull(startTimestamp);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		request.setEventStartDateTime(startTimestamp);
	}

	@Override
	@Transactional
	public void updateMeetingLocation(String userUid, String meetingRequestUid, String location) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(meetingRequestUid);
		Objects.requireNonNull(location);

		MeetingRequest request = (MeetingRequest) eventRequestRepository.findOneByUid(meetingRequestUid);
		request.setEventLocation(location);
	}

	@Override
	public void finish(String userUid, String eventRequestUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		if (!request.isFilled()) {
			throw new EventRequestNotFilledException("Request is not filled yet: " + request);
		}

		if (request instanceof MeetingRequest) {
			MeetingRequest meetingRequest = (MeetingRequest) request;
			eventBroker.createMeeting(userUid, meetingRequest.getAppliesToGroup().getUid(), meetingRequest.getName(),
					meetingRequest.getEventStartDateTime(), meetingRequest.getEventLocation(), meetingRequest.isIncludeSubGroups(),
					meetingRequest.isRsvpRequired(), meetingRequest.isRelayable(), meetingRequest.getReminderType(), meetingRequest.getCustomReminderMinutes());
		} else {
			VoteRequest voteRequest = (VoteRequest) request;
			eventBroker.createVote(userUid, voteRequest.getAppliesToGroup().getUid(), voteRequest.getName(),
					voteRequest.getEventStartDateTime(), voteRequest.isIncludeSubGroups(), voteRequest.isRsvpRequired(),
					voteRequest.isRelayable(), voteRequest.getReminderType(), voteRequest.getCustomReminderMinutes());
		}

		eventRequestRepository.delete(request);
	}
}
