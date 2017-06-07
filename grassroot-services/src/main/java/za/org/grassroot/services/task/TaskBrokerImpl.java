package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.specifications.TodoSpecifications;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.EventLogSpecifications.forEvent;
import static za.org.grassroot.core.specifications.EventLogSpecifications.forUser;
import static za.org.grassroot.core.specifications.EventLogSpecifications.isResponseToAnEvent;
import static za.org.grassroot.services.specifications.TodoSpecifications.*;

/**
 * Created by luke on 2016/04/26.
 */
@Service
public class TaskBrokerImpl implements TaskBroker {

    private static final Logger log = LoggerFactory.getLogger(TaskBrokerImpl.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Value("${grassroot.todos.days_over.prompt:7}")
    private int DAYS_PAST_FOR_TODO_CHECKING;

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
    public TaskDTO load(String userUid, String taskUid) {
        User user = userRepository.findOneByUid(userUid);
        Event event = eventRepository.findOneByUid(taskUid);
        if (event != null) {
            return new TaskDTO(event, user, eventLogRepository);
        } else {
            Todo todo = todoRepository.findOneByUid(taskUid);
            if (todo != null) {
                return new TaskDTO(todo, user);
            } else {
                return null;
            }
        }
    }

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

        log.debug("Task created by user: {}", taskToReturn.isCreatedByUser());
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

        eventBroker.retrieveGroupEvents(group, null, Instant.now(), null).stream()
                .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                .forEach(e -> taskDtos.add(new TaskDTO(e, user, eventLogRepository)));

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoRepository.findAll(Specifications.where(notCancelled())
                .and(hasGroupAsParent(group))
                .and(actionByDateBetween(todoStart, todoEnd))
                .and(completionConfirmsBelow(COMPLETION_PERCENTAGE_BOUNDARY, false)));

        for (Todo todo : todos) {
            taskDtos.add(new TaskDTO(todo, user));
        }

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchGroupTasksInPeriod(String userUid, String groupUid, Instant start, Instant end) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        Set<TaskDTO> taskDtos = new HashSet<>();

        eventBroker.retrieveGroupEvents(group, null, start, end)
                .forEach(e -> taskDtos.add(new TaskDTO(e, user, eventLogRepository)));

        todoRepository.findAll(Specifications.where(hasGroupAsParent(group))
                .and(actionByDateBetween(start, end)))
                .forEach(t -> taskDtos.add(new TaskDTO(t, user)));

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

        @SuppressWarnings("unchecked")
        List<Todo> todos = todoRepository.findAll(Specifications.where(notCancelled())
                .and(TodoSpecifications.hasGroupAsParent(group)));
        Set<TaskDTO> todoTaskDtos  = resolveTodoTaskDtos(todos, user, changedSince);
        taskDtos.addAll(todoTaskDtos);

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

        // todo : switch all of these to using assignment instead of just group
        // todo : use specifications when those are wired up properly
        List<Event> events = eventRepository.findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(user, now);

        if (events != null) {
            events = events.stream()
                    .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                    .collect(Collectors.toList());
        }

        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, null);

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoRepository.findAll(Specifications.where(notCancelled())
                .and(actionByDateBetween(todoStart, todoEnd))
                .and(completionConfirmsBelow(COMPLETION_PERCENTAGE_BOUNDARY, false))
                .and(userPartOfGroup(user)));

        Set<TaskDTO> todoTaskDtos = resolveTodoTaskDtos(todos, user, null);
        taskDtos.addAll(todoTaskDtos);

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
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, false);

		List<Event> events = eventRepository.findByParentGroupMembershipsUserAndNameSearchTerm(user.getId(), tsQuery);
		Set<TaskDTO> taskDTOs = resolveEventTaskDtos(events, user, null);

        List<Todo> todos = todoRepository.findByParentGroupMembershipsUserAndMessageSearchTerm(user.getId(), tsQuery);
		Set<TaskDTO> todoTaskDTOs = resolveTodoTaskDtos(todos, user, null);
		taskDTOs.addAll(todoTaskDTOs);

		List<TaskDTO> tasks = new ArrayList<>(taskDTOs);
		Collections.sort(tasks);
		log.info("Searched for the term {} among all of user's tasks, took {} msecs", searchTerm, System.currentTimeMillis() - startTime);

		return tasks;
	}

	private Set<TaskDTO> resolveEventTaskDtos(List<Event> events, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Event event : events) {
            EventLog userResponseLog = eventLogRepository.findOne(where(forEvent(event))
                    .and(forUser(user)).and(isResponseToAnEvent()));
            if (changedSince == null || isEventAddedOrUpdatedSince(event, userResponseLog, changedSince)) {
                taskDtos.add(new TaskDTO(event, user, userResponseLog));
            }
        }
        return taskDtos;
    }

    private boolean partOfGroupBeforeVoteCalled(Event event, User user) {
        Membership membership = event.getAncestorGroup().getMembership(user);
        return membership != null && event.getCreatedDateTime().isAfter(membership.getJoinTime());
    }

    private Set<TaskDTO> resolveTodoTaskDtos(List<Todo> todos, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Todo todo : todos) {
            if (changedSince == null || isTodoAddedOrUpdatedSince(todo, changedSince)) {
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

    private boolean isTodoAddedOrUpdatedSince(Todo todo, Instant changedSince) {
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
