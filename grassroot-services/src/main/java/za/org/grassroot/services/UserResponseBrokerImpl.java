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
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.TodoBrokerNew;
import za.org.grassroot.services.task.VoteBroker;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class UserResponseBrokerImpl implements UserResponseBroker {

    private final UserRepository userRepository;

    private final EventBroker eventBroker;
    private final VoteBroker voteBroker;
    private final EventLogBroker eventLogBroker;
    private final SafetyEventBroker safetyEventBroker;
    private final TodoBrokerNew todoBroker;

    private final UserLogRepository userLogRepository;
    private final GroupLogRepository groupLogRepository;

    public UserResponseBrokerImpl(UserRepository userRepository, EventBroker eventBroker, VoteBroker voteBroker, EventLogBroker eventLogBroker, SafetyEventBroker safetyEventBroker, TodoBrokerNew todoBroker, UserLogRepository userLogRepository, GroupLogRepository groupLogRepository) {
        this.userRepository = userRepository;
        this.eventBroker = eventBroker;
        this.voteBroker = voteBroker;
        this.eventLogBroker = eventLogBroker;
        this.safetyEventBroker = safetyEventBroker;
        this.todoBroker = todoBroker;
        this.userLogRepository = userLogRepository;
        this.groupLogRepository = groupLogRepository;
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
            return todo;
        }

        return null;
    }

    @Override
    public void recordUserResponse(String userUid, JpaEntityType entityType, String entityUid, String userResponse) {

    }

}
