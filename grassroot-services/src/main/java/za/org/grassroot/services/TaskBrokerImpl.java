package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.LogBookLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.*;

import java.time.Instant;
import java.util.*;

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
    private LogBookRepository logBookRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private LogBookLogRepository logBookLogRepository;

    @Autowired
    private TodoBroker todoBroker;

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
                LogBook logBook = todoBroker.load(taskUid);
                taskToReturn = new TaskDTO(logBook, user);
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
        for (Event event : groupBroker.retrieveGroupEvents(group, null, start, null)) {
            taskDtos.add(new TaskDTO(event, user, eventLogRepository));
        }

        // todo : hmm, actually, we may want this to find all incomplete actions, but to consider / adjust in future
        List<LogBook> logBooks = logBookRepository.findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThan(group, LogBook.COMPLETION_PERCENTAGE_BOUNDARY, start);
        for (LogBook logBook : logBooks) {
            taskDtos.add(new TaskDTO(logBook, user));
        }

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchGroupTasks(String userUid, String groupUid, Instant changedSince) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        List<Event> events = eventRepository.findByParentGroupAndCanceledFalse(group);
        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, changedSince);

        List<LogBook> logBooks = logBookRepository.findByParentGroup(group);
        Set<TaskDTO> logBookTaskDtos = resolveLogBookTaskDtos(logBooks, user, changedSince);
        taskDtos.addAll(logBookTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);

        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUpcomingUserTasks(String userUid, Instant changedSince) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Instant now = Instant.now();

        List<Event> events = eventRepository.findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(user, now);
        Set<TaskDTO> taskDtos = resolveEventTaskDtos(events, user, changedSince);

        List<LogBook> logBooks = logBookRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThan(user, now);
        Set<TaskDTO> logBookTaskDtos = resolveLogBookTaskDtos(logBooks, user, changedSince);
        taskDtos.addAll(logBookTaskDtos);

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        log.info("Retrieved all the user's upcoming tasks, took {} msecs", System.currentTimeMillis() - now.toEpochMilli());
        return tasks;
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
		List<Event> events = eventRepository.findByParentGroupMembershipsUserAndNameContainingIgnoreCase(user, searchTerm);
		Set<TaskDTO> taskDTOs = resolveEventTaskDtos(events, user, null);

		List<LogBook> toDos = logBookRepository.findByParentGroupMembershipsUserAndMessageContainingIgnoreCase(user, searchTerm);
		Set<TaskDTO> todoTaskDTOs = resolveLogBookTaskDtos(toDos, user, null);
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
            if (changedSince == null || isEventChangedSince(event, userResponseLog, changedSince)) {
                taskDtos.add(new TaskDTO(event, user, userResponseLog));
            }
        }
        return taskDtos;
    }

    private Set<TaskDTO> resolveLogBookTaskDtos(List<LogBook> logBooks, User user, Instant changedSince) {
        Set<TaskDTO> taskDtos = new HashSet<>();
        for (LogBook logBook : logBooks) {
            if (changedSince == null || isLogBookChangedSince(logBook, changedSince)) {
                taskDtos.add(new TaskDTO(logBook, user));
            }
        }
        return taskDtos;
    }

    private boolean isEventChangedSince(Event event, EventLog userResponseLog, Instant changedSince) {
        if (event.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        EventLog lastChangeLog = eventLogRepository.findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(event, EventLogType.CHANGE);
        if (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        if (userResponseLog != null && userResponseLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        return false;
    }

    private boolean isLogBookChangedSince(LogBook logBook, Instant changedSince) {
        if (logBook.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        LogBookLog lastChangeLog = logBookLogRepository.findFirstByLogBookAndTypeOrderByCreatedDateTimeDesc(logBook, LogBookLogType.CHANGED);
        if (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        return false;
    }
}
