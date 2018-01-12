package za.org.grassroot.services.task;

import com.google.common.collect.ComparisonChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.EventLogSpecifications.*;

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

    private final UserRepository userRepository;
    private final GroupBroker groupBroker;
    private final EventBroker eventBroker;
    private final TodoBroker todoBroker;

    private final UidIdentifiableRepository genericRepository;
    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final TodoLogRepository todoLogRepository;

    private final VoteBroker voteBroker;
    private final PermissionBroker permissionBroker;

    @Autowired
    public TaskBrokerImpl(UserRepository userRepository, GroupBroker groupBroker, EventBroker eventBroker, UidIdentifiableRepository genericRepository, EventRepository eventRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, TodoBroker todoBroker, PermissionBroker permissionBroker, VoteBroker voteBroker) {
        this.userRepository = userRepository;
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.genericRepository = genericRepository;
        this.todoBroker = todoBroker;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.todoLogRepository = todoLogRepository;
        this.permissionBroker = permissionBroker;
        this.voteBroker = voteBroker;
    }

    @Override
    public TaskDTO load(String userUid, String taskUid) {
        User user = userRepository.findOneByUid(userUid);
        Event event = eventBroker.load(taskUid);
        if (event != null) {
            return new TaskDTO(event, user, eventLogRepository);
        } else {
            Todo todo = todoBroker.load(taskUid);
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
        List<Todo> todos = todoBroker.fetchTodosForGroup(userUid, groupUid, false, true, todoStart, todoEnd, null);

        log.info("number of todos fetched for group = {}", todos.size());

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

        todoBroker.fetchTodosForGroup(userUid, groupUid, false, false, start, end, null)
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

        List<Todo> todos = todoBroker.fetchTodosForGroup(userUid, groupUid, false, false, null, null, null);
        Set<TaskDTO> todoTaskDtos  = resolveTodoTaskDtos(todos, user, changedSince);
        taskDtos.addAll(todoTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        tasks.sort(Collections.reverseOrder());

        return new ChangedSinceData<>(tasks, removedUids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUpcomingUserTasks(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Instant now = Instant.now();

        // todo : switch most of these to using assignment instead of just group
        List<Event> events = eventRepository.findAll(Specifications
                .where(EventSpecifications.userPartOfGroup(user))
                .and(EventSpecifications.notCancelled())
                .and(EventSpecifications.startDateTimeAfter(now)));

        if (events != null) {
            events = events.stream()
                    .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                    .collect(Collectors.toList());
        }

        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, null);

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoBroker.fetchTodosForUser(userUid, true, true, todoStart, todoEnd, null);

        Set<TaskDTO> todoTaskDtos = resolveTodoTaskDtos(todos, user, null);
        taskDtos.addAll(todoTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        log.info("Retrieved all the user's upcoming tasks, took {} msecs", System.currentTimeMillis() - now.toEpochMilli());
        return tasks;
    }

    @Override
    public List<TaskFullDTO> fetchUpcomingUserTasksFull(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Instant now = Instant.now();

        // todo : switch most of these to using assignment instead of just group
        List<Event> events = eventRepository.findAll(Specifications
                .where(EventSpecifications.userPartOfGroup(user))
                .and(EventSpecifications.notCancelled())
                .and(EventSpecifications.startDateTimeAfter(now)));

        if (events != null) {
            events = events.stream()
                    .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                    .collect(Collectors.toList());
        }

        Set<TaskFullDTO> taskDtos = new HashSet<>();
        events.stream().filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                .forEach(e -> taskDtos.add(new TaskFullDTO(e, user, e.getCreatedDateTime(), getUserResponse(e, user))));

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoBroker.fetchTodosForUser(userUid, true, true, todoStart, todoEnd, null);


        for (Todo todo : todos) {
            taskDtos.add(new TaskFullDTO(todo, user, todo.getCreatedDateTime(), getUserResponse(todo, user)));
        }


        List<TaskFullDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks, (o1, o2) -> ComparisonChain.start()
                .compare(o1.getDeadlineMillis(), o2.getDeadlineMillis())
                .compareFalseFirst(o1.isHasResponded(), o2.isHasResponded())
                .result());
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

        List<Todo> todos = todoBroker.searchUserTodos(userUid, searchTerm);
		Set<TaskDTO> todoTaskDTOs = resolveTodoTaskDtos(todos, user, null);
		taskDTOs.addAll(todoTaskDTOs);

		List<TaskDTO> tasks = new ArrayList<>(taskDTOs);
		Collections.sort(tasks);
		log.info("Searched for the term {} among all of user's tasks, took {} msecs", searchTerm, System.currentTimeMillis() - startTime);

		return tasks;
	}

    @Override
    @Transactional(readOnly = true)
    public List<TaskMinimalDTO> findNewlyChangedTasks(String userUid, Map<String, Long> knownTasksByTimeChanged) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(knownTasksByTimeChanged);

        User user = userRepository.findOneByUid(userUid);

        List<TaskTimeChangedDTO> userEvents = eventRepository.fetchEventsWithTimeChangedForUser(user);
        List<TaskTimeChangedDTO> userTodos = todoBroker.fetchUserTodosWithTimeChanged(userUid);

        Set<Event> events = loadChangedOrNewEvents(userEvents, knownTasksByTimeChanged);
        Set<Todo> todos = loadChangedOrNewTodos(userTodos, knownTasksByTimeChanged);

        Map<String, Instant> uidInstantMap = Stream.concat(userEvents.stream(), userTodos.stream())
                .collect(taskTimeChangedCollector());

        return combineTasks(events, todos, uidInstantMap);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskMinimalDTO> fetchNewlyChangedTasksForGroup(String userUid, String groupUid,
                                                               Map<String, Long> knownTasksByTimeChanged) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(knownTasksByTimeChanged);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        List<TaskTimeChangedDTO> groupEvents = eventRepository.fetchGroupEventsWithTimeChanged(group);
        Set<Event> newOrUpdatedEvents = loadChangedOrNewEvents(groupEvents, knownTasksByTimeChanged);

        List<TaskTimeChangedDTO> groupTodos = todoBroker.fetchGroupTodosWithTimeChanged(groupUid);
        Set<Todo> newOrUpdatedTodos = loadChangedOrNewTodos(groupTodos, knownTasksByTimeChanged);

        Map<String, Instant> uidInstantMap = Stream.concat(groupEvents.stream(), groupTodos.stream())
                .collect(taskTimeChangedCollector());

        return combineTasks(newOrUpdatedEvents, newOrUpdatedTodos, uidInstantMap);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFullDTO> fetchAllUserTasksSorted(String userUid, TaskSortType sortType) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);

        Set<Task> userEvents = eventRepository
                .findAll(Specifications.where(
                        EventSpecifications.notCancelled()).and(
                        EventSpecifications.userPartOfGroup(user)))
                .stream().map(e -> (Task) e).collect(Collectors.toSet());
        Set<Task> userTodos = todoBroker.fetchTodosForUser(userUid, false, false, null, null, null)
                .stream().map(t -> (Task) t).collect(Collectors.toSet());

        Map<String, Instant> uidTimeMap = Stream.concat(
                eventRepository.fetchEventsWithTimeChangedForUser(user).stream(),
                todoBroker.fetchUserTodosWithTimeChanged(user.getUid()).stream()).
                collect(taskTimeChangedCollector());

        return Stream.concat(userEvents.stream(), userTodos.stream())
                .distinct()
                .map(transformToDTO(user, uidTimeMap))
                .sorted(compareByType(sortType))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFullDTO> fetchSpecifiedTasks(String userUid, Map<String, TaskType> taskUidsAndTypes, TaskSortType taskSortType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUidsAndTypes);

        User user = userRepository.findOneByUid(userUid);

        Set<String> eventUids = taskUidsAndTypes.keySet().stream()
                .filter(uid -> !taskUidsAndTypes.get(uid).equals(TaskType.TODO)).collect(Collectors.toSet());
        Set<String> todoUids = taskUidsAndTypes.keySet().stream()
                .filter(uid -> taskUidsAndTypes.get(uid).equals(TaskType.TODO)).collect(Collectors.toSet());

        if (eventUids.isEmpty() && todoUids.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Event> events = eventRepository.findByUidIn(eventUids);
        Set<Todo> todos = genericRepository.findByUidIn(Todo.class, JpaEntityType.TODO, todoUids);

        Stream<TaskTimeChangedDTO> taskStream =
                todoUids.isEmpty() ? eventRepository.fetchEventsWithTimeChanged(eventUids).stream() :
                eventUids.isEmpty() ? todoBroker.fetchTodosWithTimeChanged(todoUids).stream() :
                Stream.concat(eventRepository.fetchEventsWithTimeChanged(eventUids).stream(),
                        todoBroker.fetchTodosWithTimeChanged(todoUids).stream());

        Map<String, Instant> uidTimeMap = taskStream.collect(taskTimeChangedCollector());

        return Stream.concat(events.stream().map(e -> (Task) e), todos.stream().map(t -> (Task) t))
                .distinct()
                .map(transformToDTO(user, uidTimeMap))
                .sorted(compareByType(taskSortType))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> fetchTasksRequiringUserResponse(String userUid, String userResponse) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);

        List<Task> tasks = new ArrayList<>();

        List<Event> outstandingVotes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
        List<Event> outstandingYesNoVotes = outstandingVotes.stream()
                .filter(vote -> vote.getTags() == null || vote.getTags().length == 0)
                .collect(Collectors.toList());

        List<Event> outstandingOptionsVotes = outstandingVotes.stream()
                .filter(v -> ((Vote) v).hasOption(userResponse.trim()))
                .collect(Collectors.toList());

        List<Event> outstandingMeetings = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING);

        tasks.addAll(outstandingYesNoVotes);
        tasks.addAll(outstandingOptionsVotes);
        tasks.addAll(outstandingMeetings);

        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFullDTO> fetchUpcomingGroupTasks(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        Set<TaskFullDTO> taskDtos = new HashSet<>();

        eventBroker.retrieveGroupEvents(group, null, Instant.now(), null).stream()
                .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                .forEach(e -> taskDtos.add(new TaskFullDTO(e, user, e.getCreatedDateTime(), getUserResponse(e, user))));

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoBroker.fetchTodosForGroup(userUid, groupUid, false, true, todoStart, todoEnd, null);

        log.info("number of todos fetched for group = {}", todos.size());

        for (Todo todo : todos) {
            taskDtos.add(new TaskFullDTO(todo, user, todo.getCreatedDateTime(), todo.getResponseTag()));
        }

        List<TaskFullDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks, (o1, o2) -> ComparisonChain.start()
                .compare(o1.getDeadlineMillis(), o2.getDeadlineMillis())
                .compareFalseFirst(o1.isHasResponded(), o2.isHasResponded())
                .result());
        return tasks;
    }

    private Function<Task, TaskFullDTO> transformToDTO(User user, Map<String, Instant> uidTimeMap) {
        return t-> {
            TaskFullDTO taskFullDTO = new TaskFullDTO(t, user, uidTimeMap.get(t.getUid()), getUserResponse(t, user));
            if (t instanceof Vote) {
                taskFullDTO.setVoteResults(voteBroker.fetchVoteResults(user.getUid(), t.getUid(), true));
            }
            return taskFullDTO;
        };
    }

    private Comparator<TaskFullDTO> compareByType(TaskSortType type) {
        if (type == null) {
            return Comparator.comparing(TaskFullDTO::getLastServerChangeMillis);
        }

        switch (type) {
            case TIME_CREATED:
                return Comparator.comparing(TaskFullDTO::getCreatedTimeMillis);
            case TIME_CHANGED:
                return Comparator.comparing(TaskFullDTO::getLastServerChangeMillis);
            case DEADLINE:
                return Comparator.comparing(TaskFullDTO::getDeadlineMillis);
            case RESPONSE_NEEDED:
                return Comparator.comparing(TaskFullDTO::isHasResponded);
            default:
                return Comparator.comparing(TaskFullDTO::getLastServerChangeMillis);
        }
    }

    private Set<Event> loadChangedOrNewEvents(List<TaskTimeChangedDTO> events, Map<String, Long> knownTasksByTimeChanged) {
        return eventRepository.findByUidIn(events.stream()
                .filter(newOrUpdatedTask(knownTasksByTimeChanged))
                .map(TaskTimeChangedDTO::getTaskUid)
                .collect(Collectors.toList()));
    }

    private Set<Todo> loadChangedOrNewTodos(List<TaskTimeChangedDTO> todos, Map<String, Long> knownTasksByTimeChanged) {
        return genericRepository.findByUidIn(Todo.class, JpaEntityType.TODO, todos.stream()
                .filter(newOrUpdatedTask(knownTasksByTimeChanged))
                .map(TaskTimeChangedDTO::getTaskUid)
                .collect(Collectors.toSet()));
    }

    private List<TaskMinimalDTO> combineTasks(Set<Event> events, Set<Todo> todos, Map<String, Instant> uidInstantMap) {
        List<TaskMinimalDTO> tasksToReturn = events.stream()
                .map(e -> new TaskMinimalDTO(e, uidInstantMap.get(e.getUid())))
                .distinct()
                .collect(Collectors.toList());

        tasksToReturn.addAll(todos.stream()
                .map(t -> new TaskMinimalDTO(t, uidInstantMap.get(t.getUid())))
                .distinct()
                .collect(Collectors.toList()));

        tasksToReturn
                .sort(Comparator.comparing(TaskMinimalDTO::getLastChangeTimeServerMillis));
        return tasksToReturn;
    }

    private Predicate<TaskTimeChangedDTO> newOrUpdatedTask(Map<String, Long> knownTasksByTimeChanged) {
        return tC -> !knownTasksByTimeChanged.containsKey(tC.getTaskUid()) ||
                knownTasksByTimeChanged.get(tC.getTaskUid()) < tC.getLastTaskChange().toEpochMilli();
    }

    private Collector<TaskTimeChangedDTO, ?, Map<String, Instant>> taskTimeChangedCollector() {
        return Collectors.toMap(TaskTimeChangedDTO::getTaskUid, TaskTimeChangedDTO::getLastTaskChange);
    }

    private String getUserResponse(Task task, User user) {
        switch (task.getTaskType()) {
            case MEETING:
                EventLog mtgRsvp = findMostRecentResponseLog(user, (Event) task);
                return mtgRsvp != null ? mtgRsvp.getResponse().name() : null;
            case VOTE:
                EventLog voteResponse = findMostRecentResponseLog(user, (Event) task);
                return voteResponse != null  ? voteResponse.getTag() : null;
            case TODO:
                // hack, but going to change in to-do refactor anyway
                return ((Todo) task).hasUserResponded(user) ? "COMPLETE" : null;
            default:
                return null;
        }
    }

    private EventLog findMostRecentResponseLog(User user, Event event) {
        List<EventLog> responseLogs = eventLogRepository.findAll(Specifications.where(forEvent(event))
                        .and(forUser(user)).and(isResponseToAnEvent()),
        new Sort(Sort.Direction.DESC, "createdDateTime"));
        return responseLogs != null && !responseLogs.isEmpty() ? responseLogs.get(0) : null;
    }

    private Set<TaskDTO> resolveEventTaskDtos(List<Event> events, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Event event : events) {
            EventLog userResponseLog = eventLogRepository.findOne(where(forEvent(event))
                    .and(forUser(user)).and(isResponseToAnEvent()));
            if (changedSince == null || isEventAddedOrUpdatedSince(event, userResponseLog, changedSince)) {
                TaskDTO taskDTO = new TaskDTO(event, user, userResponseLog);
                if (event instanceof Vote) {
                    taskDTO.setVoteCount(voteBroker.fetchVoteResults(user.getUid(), event.getUid(), true));
                }
                taskDtos.add(taskDTO);
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
