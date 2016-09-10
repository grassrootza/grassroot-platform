package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/04/26.
 */
@Service
public class TaskBrokerImpl implements TaskBroker {

    private static final Logger log = LoggerFactory.getLogger(TaskBrokerImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private TodoLogRepository todoLogRepository;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Override
    @Transactional(readOnly = true)
    public TaskDTO load(String userUid, String taskUid, TaskType type) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(type);

        User user = userRepository.findOneByUid(userUid);
        TaskDTO taskToReturn;

        switch (type) {
            case MEETING:
            case VOTE:
                Event event = eventBroker.load(taskUid);
                taskToReturn = new TaskDTO(event, user, eventLogRepository);
                break;
            case TODO:
                Todo todo = todoBroker.load(taskUid);
                taskToReturn = new TaskDTO(todo, user);
                break;
            default:
                taskToReturn = null;
        }

        log.info("Task created by user: {}", taskToReturn.isCreatedByUser());
        return taskToReturn;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUpcomingIncompleteGroupTasks(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        Set<TaskDTO> taskDtos = new HashSet<>();

        Instant start = Instant.now();
        eventBroker.retrieveGroupEvents(group, null, start, null)
                .forEach(e -> new TaskDTO(e, user, eventLogRepository));

        // todo : hmm, actually, we may want this to find all incomplete actions, but to consider / adjust in future
        List<Todo> todos = todoRepository.findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThanAndCancelledFalse(group, Todo.COMPLETION_PERCENTAGE_BOUNDARY, start);
        for (Todo todo : todos) {
            taskDtos.add(new TaskDTO(todo, user));
        }

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public ChangedSinceData<TaskDTO> fetchGroupTasks(String userUid, String groupUid, Instant changedSince) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        Set<String> removedUids = new HashSet<>();

        List<Event> events = eventRepository.findByParentGroupAndCanceledFalse(group);
        if (changedSince != null) {
            List<Event> cancelledSinceEvents = eventRepository.findByParentGroupAndCanceledSince(group, changedSince);
            removedUids = cancelledSinceEvents.stream()
                    .map(AbstractEventEntity::getUid)
                    .collect(Collectors.toSet());
        }
        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, changedSince);

        List<Todo> todos = todoRepository.findByParentGroupAndCancelledFalse(group);
        Set<TaskDTO> logBookTaskDtos = resolveLogBookTaskDtos(todos, user, changedSince);
        taskDtos.addAll(logBookTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks, Collections.reverseOrder());

        return new ChangedSinceData<>(tasks, removedUids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUpcomingUserTasks(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Instant now = Instant.now();

        List<Event> events = eventRepository.findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(user, now);
        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, null);

        List<Todo> todos = todoRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThanAndCancelledFalse(user, now);
        Set<TaskDTO> logBookTaskDtos = resolveLogBookTaskDtos(todos, user, null);
        taskDtos.addAll(logBookTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        log.info("Retrieved all the user's upcoming tasks, took {} msecs", System.currentTimeMillis() - now.toEpochMilli());
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public ChangedSinceData<TaskDTO> fetchUpcomingTasksAndCancelled(String userUid, Instant changedSince) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        List<TaskDTO> upcomingTasks = fetchUpcomingUserTasks(userUid);
        Set<String> cancelledUids = new HashSet<>();

        if (changedSince != null) {
            List<Event> cancelledSinceEvents = eventRepository.findByMemberAndCanceledSince(user, changedSince);
            cancelledUids = cancelledSinceEvents.stream()
                    .map(AbstractEventEntity::getUid)
                    .collect(Collectors.toSet());
        }

        return new ChangedSinceData<>(upcomingTasks, cancelledUids);
    }

    @Override
	@Transactional(readOnly = true)
	public List<TaskDTO> searchForTasks(String userUid, String searchTerm) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(searchTerm);

		if (searchTerm.trim().isEmpty()) {
			throw new IllegalArgumentException("Error! Cannot use this method to search for blank search term");
		}

		Long startTime = System.currentTimeMillis();
		User user = userRepository.findOneByUid(userUid);
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm);

		List<Event> events = eventRepository.findByParentGroupMembershipsUserAndNameSearchTerm(user.getId(), tsQuery);
		Set<TaskDTO> taskDTOs = resolveEventTaskDtos(events, user, null);

        List<Todo> todos = todoRepository.findByParentGroupMembershipsUserAndMessageSearchTerm(user.getId(), tsQuery);
		Set<TaskDTO> todoTaskDTOs = resolveLogBookTaskDtos(todos, user, null);
		taskDTOs.addAll(todoTaskDTOs);

		List<TaskDTO> tasks = new ArrayList<>(taskDTOs);
		Collections.sort(tasks);
		log.info("Searched for the term {} among all of user's tasks, took {} msecs", searchTerm, System.currentTimeMillis() - startTime);

		return tasks;
	}

	private Set<TaskDTO> resolveEventTaskDtos(List<Event> events, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Event event : events) {
            EventLog userResponseLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
            if (changedSince == null || isEventAddedOrUpdatedSince(event, userResponseLog, changedSince)) {
                taskDtos.add(new TaskDTO(event, user, userResponseLog));
            }
        }
        return taskDtos;
    }

    private Set<TaskDTO> resolveLogBookTaskDtos(List<Todo> todos, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Todo todo : todos) {
            if (changedSince == null || isLogBookAddedOrUpdatedSince(todo, changedSince)) {
                taskDtos.add(new TaskDTO(todo, user));
            }
        }
        return taskDtos;
    }

    private boolean isEventAddedOrUpdatedSince(Event event, EventLog userResponseLog, Instant changedSince) {
        if (event.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        // to be honest, it can be that some change (CHANGED log) didn't affect the information that was presented before on UI,
        // but it is no big harm to return same data again compared to benefits in code simplicity
        EventLog lastChangeLog = eventLogRepository.findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(event, EventLogType.CHANGE);
        if (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        return (userResponseLog != null) && (userResponseLog.getCreatedDateTime().isAfter(changedSince));
    }

    private boolean isLogBookAddedOrUpdatedSince(Todo todo, Instant changedSince) {
        if (todo.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        } else {
            // to be honest, it can be that some change (CHANGED log) didn't affect the information that was presented before on UI,
            // but it is no big harm to return same data again compared to benefits in code simplicity
            TodoLog lastChangeLog = todoLogRepository.findFirstByTodoAndTypeOrderByCreatedDateTimeDesc(todo, TodoLogType.CHANGED);
            return (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince));
        }
    }
}
