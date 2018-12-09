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
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.domain.task.Vote;
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

        List<Event> events = eventBroker.getEventsNeedingResponseFromUser(user);
        if (events != null && !events.isEmpty()) {
            return events.get(0);
        }

        Todo todo = todoBroker.checkForTodoNeedingResponse(userUid);
        if (todo != null) {
            log.info("found a todo for user to respond: {}", todo);
            return todo;
        }

        return null;
    }

    // todo : handle with a bit more sophistication (e.g., look for 'yes it is done')
    @Override
    public boolean checkValidityOfResponse(EntityForUserResponse entity, String message) {
        switch (entity.getJpaEntityType()) {
            case MEETING:
                return EventRSVPResponse.fromString(message) != EventRSVPResponse.INVALID_RESPONSE;
            case VOTE:
                Vote vote = (Vote) entity;
                return !vote.getVoteOptions().isEmpty() ? vote.hasOption(message) :
                        EventRSVPResponse.fromString(message) != EventRSVPResponse.INVALID_RESPONSE;
            case TODO:
                Todo todo = (Todo) entity;
                return todo.getType().equals(TodoType.INFORMATION_REQUIRED);
            default:
                return false;
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
