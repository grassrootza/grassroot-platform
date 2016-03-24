package za.org.grassroot.services;

import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventRequestBrokerImpl implements EventRequestBroker {

    private static final Logger log = LoggerFactory.getLogger(EventRequestBrokerImpl.class);

	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EventRequestRepository eventRequestRepository;
	@Autowired
	private EventBroker eventBroker;
	@Autowired
	private PermissionBroker permissionBroker;

	@Override
	public EventRequest load(String eventRequestUid) {
		return eventRequestRepository.findOneByUid(eventRequestUid);
	}

	@Override
	@Transactional
	public MeetingRequest createEmptyMeetingRequest(String userUid, String groupUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
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

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
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
        log.info("Setting location to " + location + " ... on request ... " + request);
        request.setEventLocation(location);
	}

	@Override
	public String finish(String userUid, String eventRequestUid, boolean rsvpRequired) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		if (!request.isFilled()) {
			throw new EventRequestNotFilledException("Request is not filled yet: " + request);
		}

		if (request.getDescription() == null) request.setDescription(""); // since we don't ask for description in USSD

		Set<String> assignedMemberUids = request.getAssignedMembers().stream().map(User::getUid).collect(Collectors.toSet());

		String createdEntityUid;
		if (request instanceof MeetingRequest) {
			MeetingRequest meetingRequest = (MeetingRequest) request;
			createdEntityUid = eventBroker.createMeeting(userUid, meetingRequest.getAppliesToGroup().getUid(), meetingRequest.getName(),
					meetingRequest.getEventStartDateTime(), meetingRequest.getEventLocation(), meetingRequest.isIncludeSubGroups(),
					rsvpRequired, meetingRequest.isRelayable(), meetingRequest.getReminderType(), meetingRequest.getCustomReminderMinutes(),
					meetingRequest.getDescription(), assignedMemberUids).getUid();
		} else {
			VoteRequest voteRequest = (VoteRequest) request;
			createdEntityUid = eventBroker.createVote(userUid, voteRequest.getAppliesToGroup().getUid(), voteRequest.getName(),
					voteRequest.getEventStartDateTime(), voteRequest.isIncludeSubGroups(), voteRequest.isRelayable(),
					voteRequest.getDescription(), assignedMemberUids).getUid();
		}

		eventRequestRepository.delete(request);
		return createdEntityUid;
	}

	@Override
    @Transactional
	public MeetingRequest createChangeRequest(String userUid, String meetingUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(meetingUid);

        User user = userRepository.findOneByUid(userUid);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);

        // todo: check for permissions (once worked out what to check)
        MeetingRequest changeRequest = MeetingRequest.makeCopy(meeting);
		return eventRequestRepository.save(changeRequest);
	}

    @Override
    public void finishEdit(String userUid, String eventUid, String changeRequestUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(changeRequestUid);

        User user = userRepository.findOneByUid(userUid);
        Event event = eventBroker.load(eventUid);
        EventRequest request = eventRequestRepository.findOneByUid(changeRequestUid);

        if (request instanceof MeetingRequest) {
            MeetingRequest meetingChangeRequest = (MeetingRequest) request;
            eventBroker.updateMeeting(userUid, eventUid, meetingChangeRequest.getName(), meetingChangeRequest.getEventStartDateTime(),
                                      meetingChangeRequest.getEventLocation());
        } else {
            VoteRequest voteRequest = (VoteRequest) request;
            eventBroker.updateVote(userUid, eventUid, voteRequest.getEventStartDateTime(), voteRequest.getDescription());
        }

        eventRequestRepository.delete(request);
    }
}
