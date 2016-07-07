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
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.enums.LogBookStatus;

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
    private EventLogRepository eventLogRepository;

    @Autowired
    private LogBookBroker logBookBroker;

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
                LogBook logBook = logBookBroker.load(taskUid);
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
    public List<TaskDTO> fetchGroupTasks(String userUid, String groupUid, boolean futureOnly, LogBookStatus logBookStatus) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        Set<TaskDTO> taskSet = new HashSet<>();

        Instant start = futureOnly ? Instant.now() : null;
        for (Event event : groupBroker.retrieveGroupEvents(group, null, start, null)) {
            if (event.getEventStartDateTime() != null) {
                taskSet.add(new TaskDTO(event, user, eventLogRepository));
            }
        }

        for (LogBook logBook : logBookBroker.loadGroupLogBooks(group.getUid(), futureOnly, logBookStatus)) {
            if (logBook.getCreatedByUser().equals(user)) {
                taskSet.add(new TaskDTO(logBook, user));
            } else {
                taskSet.add(new TaskDTO(logBook, user));
            }
        }

        List<TaskDTO> tasks = new ArrayList<>(taskSet);
        Collections.sort(tasks);
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUserTasks(String userUid, boolean futureOnly) {
        Objects.requireNonNull(userUid);

        Long startTime = System.currentTimeMillis();

        User user = userRepository.findOneByUid(userUid);
        Set<TaskDTO> tasksDtos = new HashSet<>();

        List<Event> events = eventBroker.loadUserEvents(user.getUid(), null, false, futureOnly);
        for (Event event : events) {
            tasksDtos.add(new TaskDTO(event, user, eventLogRepository));
        }

        List<LogBook> logBooks = logBookBroker.loadUserLogBooks(user.getUid(), false, futureOnly, LogBookStatus.BOTH);
        for (LogBook logBook : logBooks) {
            tasksDtos.add(new TaskDTO(logBook, user));
        }

        List<TaskDTO> tasks = new ArrayList<>(tasksDtos);
        Collections.sort(tasks);
        log.info("Retrieved all the user's upcoming tasks, took {} msecs", System.currentTimeMillis() - startTime);
        return tasks;
    }
}
