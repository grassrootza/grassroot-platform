package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.core.repository.EventRequestRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.EventRequestNotFilledException;

import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class EventRequestBrokerImpl implements EventRequestBroker {

    private static final Logger log = LoggerFactory.getLogger(EventRequestBrokerImpl.class);

	@Value("${grassroot.vote.option.maxlength:20}")
	private int MAX_OPTION_LENGTH;

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
	public String createNewStyleEmptyVote(String userUid, String subject) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(subject);

		User user = userRepository.findOneByUid(userUid);
		VoteRequest voteRequest = new VoteRequest(user, subject);
		eventRequestRepository.save(voteRequest);
		return voteRequest.getUid();
	}

	@Override
	@Transactional
	public void updateName(String userUid, String eventRequestUid, String name) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);
		Objects.requireNonNull(name);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		String sizedName = name.length() > 40 ? name.substring(0, 40) : name;
		request.setName(sizedName);
	}

	@Override
	@Transactional
	public void updateEventDateTime(String userUid, String eventRequestUid, LocalDateTime eventDateTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);
		Objects.requireNonNull(eventDateTime);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		request.setEventStartDateTime(convertToSystemTime(eventDateTime, getSAST()));
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
	@Transactional
    public int addVoteOption(String userUid, String voteRequestUid, String voteOption) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(voteRequestUid);

		User user = userRepository.findOneByUid(userUid);
		VoteRequest vote = (VoteRequest) eventRequestRepository.findOneByUid(voteRequestUid);

		if (!vote.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Only user who created a WIP vote request can add options to it");
		}

		if (StringUtils.isEmpty(voteOption)) {
			throw new IllegalArgumentException("Error! Vote option cannot be an empty string");
		}

		if (voteOption.length() > MAX_OPTION_LENGTH) {
			throw new InvalidParameterException("Error! Vote option description is too long");
		}

		vote.addVoteOption(voteOption);

		return vote.getVoteOptions().size();
    }

	@Override
	@Transactional
	public void updateVoteGroup(String userUid, String voteRequestUid, String groupUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(voteRequestUid);
		Objects.requireNonNull(groupUid);

		User user = userRepository.findOneByUid(userUid);
		VoteRequest voteRequest = (VoteRequest) eventRequestRepository.findOneByUid(voteRequestUid);

		if (!voteRequest.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Only user who created a vote request can change its group");
		}

		Group group = groupRepository.findOneByUid(groupUid);
		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);

		voteRequest.setParent(group);
	}


	@Override
	@Transactional
	public String finish(String userUid, String eventRequestUid, boolean rsvpRequired) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(eventRequestUid);

		EventRequest request = eventRequestRepository.findOneByUid(eventRequestUid);
		if (!request.isFilled()) {
			throw new EventRequestNotFilledException("Request is not filled yet: " + request);
		}

		if (request.getDescription() == null) request.setDescription(""); // since we don't ask for description in USSD

		Stream<User> stream = request.getAssignedMembers().stream();
		Set<String> assignedMemberUids = stream.map(User::getUid).collect(Collectors.toSet());

		String createdEntityUid;
		if (request instanceof MeetingRequest) {
			MeetingRequest meetingRequest = (MeetingRequest) request;
			MeetingContainer parent = meetingRequest.getParent();
            if (meetingRequest.getReminderType() == null) {
            	meetingRequest.setReminderType(EventReminderType.GROUP_CONFIGURED);
			}

			createdEntityUid = eventBroker.createMeeting(new MeetingBuilderHelper()
					.userUid(userUid)
					.parentUid(parent.getUid())
					.parentType(parent.getJpaEntityType())
					.name(meetingRequest.getName())
					.startDateTime(meetingRequest.getEventDateTimeAtSAST())
					.location(meetingRequest.getEventLocation())
					.includeSubGroups(meetingRequest.isIncludeSubGroups())
					.reminderType(meetingRequest.getReminderType())
					.customReminderMinutes(meetingRequest.getCustomReminderMinutes())
					.description(meetingRequest.getDescription())
					.assignedMemberUids(assignedMemberUids)).getUid();
		} else {
			VoteRequest voteRequest = (VoteRequest) request;
			VoteContainer parent = voteRequest.getParent();
			createdEntityUid = eventBroker.createVote(userUid, parent.getUid(), parent.getJpaEntityType(), voteRequest.getName(),
					voteRequest.getEventDateTimeAtSAST(), voteRequest.isIncludeSubGroups(),
					voteRequest.getDescription(), assignedMemberUids, voteRequest.getVoteOptions()).getUid();
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

		if (!meeting.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created meeting can change it");
		}

		MeetingRequest changeRequest = MeetingRequest.makeCopy(meeting);
		return eventRequestRepository.save(changeRequest);
	}

    @Override
	@Transactional
    public void finishEdit(String userUid, String eventUid, String changeRequestUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(changeRequestUid);

		User user = userRepository.findOneByUid(userUid);
        Event event = eventBroker.load(eventUid);

		if (!event.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only user who created event can modify it");
		}

		EventRequest request = eventRequestRepository.findOneByUid(changeRequestUid);
        if (request instanceof MeetingRequest) {
			MeetingRequest meetingChangeRequest = (MeetingRequest) request;
			eventBroker.updateMeeting(userUid, eventUid, meetingChangeRequest.getName(),
						meetingChangeRequest.getEventDateTimeAtSAST(),
						meetingChangeRequest.getEventLocation());
        } else {
            VoteRequest voteRequest = (VoteRequest) request;
            eventBroker.updateVote(userUid, eventUid, voteRequest.getEventDateTimeAtSAST(), voteRequest.getDescription());
        }

        eventRequestRepository.delete(request);
    }
}
