package za.org.grassroot.services.task;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.enums.TaskSortType;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final EventLogBroker eventLogBroker;
    private final TodoBroker todoBroker;

    private final UidIdentifiableRepository genericRepository;
    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final TodoLogRepository todoLogRepository;
    private final TodoAssignmentRepository todoAssignmentRepository;

    private final MembershipRepository membershipRepository;

    private final VoteBroker voteBroker;
    private final PermissionBroker permissionBroker;

    @Autowired
    public TaskBrokerImpl(UserRepository userRepository, GroupBroker groupBroker, EventBroker eventBroker, EventLogBroker eventLogBroker, UidIdentifiableRepository genericRepository, EventRepository eventRepository, EventLogRepository eventLogRepository, TodoLogRepository todoLogRepository, TodoBroker todoBroker, TodoAssignmentRepository todoAssignmentRepository, MembershipRepository membershipRepository, PermissionBroker permissionBroker, VoteBroker voteBroker) {
        this.userRepository = userRepository;
        this.groupBroker = groupBroker;
        this.eventBroker = eventBroker;
        this.eventLogBroker = eventLogBroker;
        this.genericRepository = genericRepository;
        this.todoBroker = todoBroker;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.todoLogRepository = todoLogRepository;
        this.todoAssignmentRepository = todoAssignmentRepository;
        this.membershipRepository = membershipRepository;
        this.permissionBroker = permissionBroker;
        this.voteBroker = voteBroker;
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
                break;
        }

        log.debug("Task created by user: {}", taskToReturn.isCreatedByUser());
        return taskToReturn;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public <T extends Task> T loadEntity(String userUid, String taskUid, TaskType type, Class<T> returnType) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Task task = TaskType.TODO.equals(type) ? todoBroker.load(taskUid) : eventBroker.load(taskUid);

        if (user.getMembership(task.getAncestorGroup()) == null) {
            throw new AccessDeniedException("Error! Only users within ancestor group can see task");
        }

        try {
            return (T) task;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Error! Return type does not match task type");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> loadResponses(String userUid, String taskUid, TaskType type) {
        if (TaskType.VOTE.equals(type)) {
            throw new IllegalArgumentException("Cannot call individual votes");
        }

        Map<String, String> responses = new LinkedHashMap<>();

        if (TaskType.MEETING.equals(type)) {
            Meeting meeting = loadEntity(userUid, taskUid, TaskType.MEETING, Meeting.class);
            eventBroker.getRSVPResponses(meeting).forEach((user, response) ->
                    responses.put(user.getName(), response.name()));
        } else if (TaskType.TODO.equals(type)) {
            List<TodoAssignment> todoAssignmentList = todoBroker.fetchAssignedUserResponses(userUid, taskUid, false, true, false);
            todoAssignmentList.forEach(assignment -> responses.put(assignment.getUser().getName(), assignment.getResponseText()));
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public String fetchUserResponse(String userUid, Task task) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        return getUserResponse(task, user);
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

        List<Event> events = eventRepository.findAll(EventSpecifications.upcomingEventsForUser(user));

        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, null);

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoBroker.fetchTodosForUser(userUid, true, true, todoStart, todoEnd, null);

        Set<TaskDTO> todoTaskDtos = resolveTodoTaskDtos(todos, user, null);
        taskDtos.addAll(todoTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);

        log.info("Retrieved all the user's upcoming tasks, found {}, took {} msecs", tasks.size(),
                System.currentTimeMillis() - now.toEpochMilli());
        return tasks;
    }

    @Override
    public List<TaskFullDTO> fetchUpcomingUserTasksFull(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Instant now = Instant.now();

        List<Event> events = eventRepository.findAll(EventSpecifications.upcomingEventsForUser(user));

        Set<TaskFullDTO> taskDtos = new HashSet<>();
        events.forEach(e -> taskDtos.add(new TaskFullDTO(e, user, e.getCreatedDateTime(), getUserResponse(e, user))));

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();

        List<Todo> todos = todoBroker.fetchTodosForUser(userUid, true, false, todoStart, todoEnd, null);
        log.info("number of todos fetched for user: {}", todos.size());

        todos.forEach(todo -> taskDtos.add(new TaskFullDTO(todo, user, todo.getCreatedDateTime(), getUserResponse(todo, user))));

        List<TaskFullDTO> tasks = new ArrayList<>(taskDtos);
        tasks.sort((o1, o2) -> ComparisonChain.start()
                .compare(o1.getDeadlineMillis(), o2.getDeadlineMillis())
                .compareFalseFirst(o1.isHasResponded(), o2.isHasResponded())
                .result());
        log.info("Retrieved all the user's upcoming tasks, found {}, took {} msecs", tasks.size(), System.currentTimeMillis() - now.toEpochMilli());
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
	public List<TaskFullDTO> searchForTasks(String userUid, String searchTerm) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(searchTerm);

		if (searchTerm.trim().isEmpty()) {
			throw new IllegalArgumentException("Error! Cannot use this method to search for blank search term");
		}

		Long startTime = System.currentTimeMillis();
		User user = userRepository.findOneByUid(userUid);

        try {
            String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, false);
            List<Event> events = eventRepository.findByParentGroupMembershipsUserAndNameSearchTerm(user.getId(), tsQuery);

            Set<TaskFullDTO> taskFullDtos = new HashSet<>();
            events.forEach(event -> taskFullDtos.add(new TaskFullDTO(event, user, event.getCreatedDateTime(), getUserResponse(event, user))));

            List<Todo> todos = todoBroker.searchUserTodos(userUid, searchTerm);
            todos.forEach(todo -> taskFullDtos.add(new TaskFullDTO(todo, user, todo.getDeadlineTime(), getUserResponse(todo, user))));

            List<TaskFullDTO> tasks = new ArrayList<>(taskFullDtos);
            tasks.sort((o1, o2) -> ComparisonChain.start()
                    .compare(o2.getDeadlineMillis(), o1.getDeadlineMillis()) // for reverse order
                    .compareFalseFirst(o1.isHasResponded(), o2.isHasResponded())
                    .result());
            log.info("Searched for the term {} among all of user's tasks, took {} msecs", searchTerm, System.currentTimeMillis() - startTime);

            return tasks;
        } catch (InvalidDataAccessResourceUsageException e) {
            log.error("Syntax error: {}", e.getLocalizedMessage());
            return new ArrayList<>();
        }
	}

    @Override
    @Transactional(readOnly = true)
    public TaskMinimalDTO fetchDescription(String userUid, String taskUid, TaskType type) {
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(type);

        TaskMinimalDTO taskToReturn;

        switch (type) {
            case MEETING:
            case VOTE:
                Event event = eventBroker.load(taskUid);
                taskToReturn = new TaskMinimalDTO(event, event.getCreatedDateTime());
                break;
            case TODO:
                Todo todo = todoBroker.load(taskUid);
                taskToReturn = new TaskMinimalDTO(todo, todo.getCreatedDateTime());
                break;
            default:
                taskToReturn = null;
        }

        return taskToReturn;
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

        Map<String, Instant> uidInstantMap = Stream.concat(userEvents.stream().distinct(), userTodos.stream().distinct())
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
                .findAll(Specification.where(
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


        Stream<TaskTimeChangedDTO> taskStream;
        if (todoUids.isEmpty()) {
            taskStream = eventRepository.fetchEventsWithTimeChanged(eventUids).stream().distinct();
        } else if (eventUids.isEmpty()) {
            taskStream = todoBroker.fetchTodosWithTimeChanged(todoUids).stream().distinct();
        } else {
            taskStream = Stream.concat(eventRepository.fetchEventsWithTimeChanged(eventUids).stream().distinct(),
                    todoBroker.fetchTodosWithTimeChanged(todoUids).stream().distinct());
        }

        Map<String, Instant> uidTimeMap = taskStream.collect(taskTimeChangedCollector());

        return Stream.concat(events.stream().map(e -> (Task) e), todos.stream().map(t -> (Task) t))
                .distinct()
                .map(transformToDTO(user, uidTimeMap))
                .sorted(compareByType(taskSortType))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> fetchMembersAssignedToTask(String userUid, String taskUid, TaskType taskType, boolean onlyPositiveResponders) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        List<Membership> members = new ArrayList<>();

        if (TaskType.MEETING.equals(taskType) || TaskType.VOTE.equals(taskType)) {
            Event event = eventBroker.load(taskUid);
            Group ancestorGroup = event.getAncestorGroup();
            validateUserPartOfGroupOrSystemAdmin(ancestorGroup, user);
            Set<User> assignedUsers = onlyPositiveResponders ?
                    new HashSet<>(userRepository.findUsersThatRSVPForEvent(event)) : event.getMembers();
            // this could be very big, so don't user group.getMemberships which may induce graph problems
            members.addAll(membershipRepository.findByGroupAndUserIn(ancestorGroup, assignedUsers));
            log.info("added members from meeting, number: {}", members.size());
        } else {
            Todo todo = todoBroker.load(taskUid);
            Group ancestorGroup = todo.getAncestorGroup();
            validateUserPartOfGroupOrSystemAdmin(ancestorGroup, user);
            Set<User> users = todo.getAssignedMembers();
            members.addAll(membershipRepository.findByGroupAndUserIn(ancestorGroup, users));
        }

        return members;
    }

    private void validateUserPartOfGroupOrSystemAdmin(Group group, User user) {
        if (!user.isMemberOf(group) && !permissionBroker.isSystemAdmin(user)) {
            throw new AccessDeniedException("Error! Must be part of group");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFullDTO> fetchUpcomingGroupTasks(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        Set<TaskFullDTO> taskDtos = new HashSet<>();

        eventBroker.retrieveGroupEvents(group, user, Instant.now(), null).stream()
                .filter(event -> event.getEventType().equals(EventType.MEETING) || partOfGroupBeforeVoteCalled(event, user))
                .forEach(e -> taskDtos.add(new TaskFullDTO(e, user, e.getCreatedDateTime(), getUserResponse(e, user))));

        Instant todoStart = Instant.now().minus(DAYS_PAST_FOR_TODO_CHECKING, ChronoUnit.DAYS);
        Instant todoEnd = DateTimeUtil.getVeryLongAwayInstant();
        List<Todo> todos = todoBroker.fetchTodosForGroup(userUid, groupUid, false, true, todoStart, todoEnd, null);

        log.info("number of todos fetched for group = {}", todos.size());

        for (Todo todo : todos) {
            taskDtos.add(new TaskFullDTO(todo, user, todo.getCreatedDateTime(), getUserResponse(todo, user)));
        }

        List<TaskFullDTO> tasks = new ArrayList<>(taskDtos);
        tasks.sort((o1, o2) -> ComparisonChain.start()
                .compare(o1.getDeadlineMillis(), o2.getDeadlineMillis())
                .compareFalseFirst(o1.isHasResponded(), o2.isHasResponded())
                .result());

        return tasks;
    }

    @Override
    @Transactional
    public void cancelTask(String userUid, String taskUid, TaskType taskType, boolean notifyMembers, String attachedReason) {
        switch (taskType) {
            case MEETING:
            case VOTE:
                eventBroker.cancel(userUid, taskUid, true);
                break;
            case TODO:
                todoBroker.cancel(userUid, taskUid, notifyMembers, attachedReason);
                break;
            default:
                log.error("Error! Unknown task type");
        }
    }

    @Override
    @Transactional
    public TaskFullDTO changeTaskDate(String userUid, String taskUid, TaskType taskType, Instant newDateTime) {
        // todo : fix the whole LDT / instant mess and just use instant
        LocalDateTime ldt = DateTimeUtil.convertToUserTimeZone(newDateTime, DateTimeUtil.getSAST()).toLocalDateTime();
        switch (taskType) {
            case MEETING:
                eventBroker.updateMeeting(userUid, taskUid, null, ldt, null);
                break;
            case VOTE:
                voteBroker.updateVote(userUid, taskUid, ldt, null);
                break;
            case TODO:
                todoBroker.extend(userUid, taskUid, newDateTime);
                break;
            default:
                log.error("Error! Unknown task type");
        }
        return fetchSpecifiedTasks(userUid, ImmutableMap.of(taskUid, taskType), null).get(0);
    }

    @Override
    public void respondToTask(String userUid, String taskUid, TaskType taskType, String response) {
        log.info("responding to task, userUid: {}, task type: {}", userUid, taskType);
        switch (taskType) {
            case MEETING:
                eventLogBroker.rsvpForEvent(taskUid, userUid, EventRSVPResponse.fromString(response));
                break;
            case VOTE:
                voteBroker.recordUserVote(userUid, taskUid, response);
                break;
            case TODO:
                todoBroker.recordResponse(userUid, taskUid, response, false);
                break;
            default:
                throw new IllegalArgumentException("Error! Unsupported task type");
        }
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
                TodoAssignment assignment = todoAssignmentRepository.findByTodoAndUser((Todo) task, user);
                return assignment == null ? null : assignment.getResponseText();
            default:
                return null;
        }
    }

    private EventLog findMostRecentResponseLog(User user, Event event) {
        List<EventLog> responseLogs = eventLogRepository.findAll(Specification.where(forEvent(event))
                        .and(forUser(user)).and(isResponseToAnEvent()),
        new Sort(Sort.Direction.DESC, "createdDateTime"));
        return responseLogs != null && !responseLogs.isEmpty() ? responseLogs.get(0) : null;
    }

    private Set<TaskDTO> resolveEventTaskDtos(List<Event> events, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (Event event : events) {
            EventLog userResponseLog = eventLogRepository.findOne(Specification.where(forEvent(event))
                    .and(forUser(user)).and(isResponseToAnEvent())).orElse(null);
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
        Membership membership = user.getMembership(event.getAncestorGroup());
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
            TodoLog lastChangeLog = todoLogRepository.findFirstByTodoAndTypeOrderByCreationTimeDesc(todo, TodoLogType.CHANGED);
            return (lastChangeLog != null && lastChangeLog.getCreationTime().isAfter(changedSince));
        }
    }

}
