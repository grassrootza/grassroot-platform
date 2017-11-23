package za.org.grassroot.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.VoteBroker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserResponseBrokerImpl implements UserResponseBroker {

    private final UserRepository userRepository;

    private final EventBroker eventBroker;
    private final VoteBroker voteBroker;
    private final EventLogBroker eventLogBroker;
    private final SafetyEventBroker safetyEventBroker;
    private final TodoBroker todoBroker;

    public UserResponseBrokerImpl(UserRepository userRepository, EventBroker eventBroker, VoteBroker voteBroker, EventLogBroker eventLogBroker, SafetyEventBroker safetyEventBroker, TodoBroker todoBroker, UserLogRepository userLogRepository, GroupLogRepository groupLogRepository) {
        this.userRepository = userRepository;
        this.eventBroker = eventBroker;
        this.voteBroker = voteBroker;
        this.eventLogBroker = eventLogBroker;
        this.safetyEventBroker = safetyEventBroker;
        this.todoBroker = todoBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public EntityForUserResponse checkForEntityForUserResponse(String userUid, boolean checkSafetyAlerts) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);

        if (checkSafetyAlerts) {
            List<SafetyEvent> events = safetyEventBroker.getOutstandingUserSafetyEventsResponse(userUid);
            if (events != null && !events.isEmpty()) {
                return events.get(0);
            }
        }

        // todo : consolidate next two
        List<Event> votes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
        if (votes != null && !votes.isEmpty()) {
            return votes.get(0);
        }

        List<Event> meetings = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING);
        if (meetings != null && !meetings.isEmpty()) {
            return meetings.get(0);
        }

        Todo todo = todoBroker.checkForTodoNeedingResponse(userUid);
        if (todo != null) {
            log.info("found a todo for user to respond: {}", todo);
            return todo;
        }

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public EntityForUserResponse checkForPossibleEntityResponding(String userUid, String response, boolean checkAlerts) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(response);

        User user = userRepository.findOneByUid(userUid);

        // first check for an event response

        EventRSVPResponse responseType = EventRSVPResponse.fromString(response);
        boolean isYesNoResponse = responseType == EventRSVPResponse.YES || responseType == EventRSVPResponse.NO || responseType == EventRSVPResponse.MAYBE;

        List<Event> outstandingVotes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
        List<Event> outstandingYesNoVotes = outstandingVotes.stream()
                .filter(vote -> vote.getTags() == null || vote.getTags().length == 0)
                .collect(Collectors.toList());

        List<Event> outstandingOptionsVotes = outstandingVotes.stream()
                .filter(vote -> hasVoteOption(response, vote))
                .collect(Collectors.toList());

        List<Event> outstandingMeetings = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING);

        // add in check on response
        Todo outstandingTodo = todoBroker.checkForTodoNeedingResponse(userUid);

        if (isYesNoResponse && !outstandingYesNoVotes.isEmpty()) { // user sent yes-no response and there is a vote awaiting yes-no response
            log.info("User response is {}, type {} and there are outstanding YES_NO votes for that use", response, responseType);
            return outstandingYesNoVotes.get(0);
        } else if (isYesNoResponse && !outstandingMeetings.isEmpty()) {  // user sent yes-no response and there is a meeting awaiting yes-no response
            log.info("User response is {}, type {} and there are outstanding meetings for that user", response, responseType);
            return outstandingMeetings.get(0);
        } else if (!outstandingOptionsVotes.isEmpty()) { // user sent something other then yes-no, and there is a vote that has this option (tag)
            log.info("User response is {}, type {} and there are outstanding votes with custom option matching user's answer. Recording vote...", response, responseType);
            return outstandingOptionsVotes.get(0);
        } else if (outstandingTodo != null) {
            return outstandingTodo;
        } else {// we have not found any meetings or votes that this could be response to
            return null;
        }
    }

    private boolean hasVoteOption(String option, Event vote) {
        if (vote.getTags() != null) {
            for (String tag : vote.getTags()) {
                if (tag.equalsIgnoreCase(option))
                    return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void recordUserResponse(String userUid, JpaEntityType entityType, String entityUid, String userResponse) {
        switch (entityType) {
            case MEETING:
                eventLogBroker.rsvpForEvent(entityUid, userUid, EventRSVPResponse.fromString(userResponse));
                break;
            case VOTE:
                voteBroker.recordUserVote(userUid, entityUid, userResponse);
                break;
            case TODO:
                todoBroker.recordResponse(userUid, entityUid, userResponse, false);
                break;
            case SAFETY:
                safetyEventBroker.recordResponse(userUid, entityUid, !"false_alarm".equalsIgnoreCase(userResponse));
                break;
            default:
                throw new IllegalArgumentException("Error! Response attempted for non-respondable entity");
        }
    }

}
