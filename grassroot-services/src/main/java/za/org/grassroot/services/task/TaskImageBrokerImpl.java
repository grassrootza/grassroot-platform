package za.org.grassroot.services.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.storage.StorageBroker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.EventLogSpecifications.forEvent;
import static za.org.grassroot.core.specifications.EventLogSpecifications.ofType;
import static za.org.grassroot.core.specifications.TodoLogSpecifications.forTodo;
import static za.org.grassroot.core.specifications.TodoLogSpecifications.ofType;

/**
 * Created by luke on 2017/02/21.
 */
@Service
public class TaskImageBrokerImpl implements TaskImageBroker {

    private UserRepository userRepository;
    private EventRepository eventRepository;
    private EventLogRepository eventLogRepository;
    private TodoRepository todoRepository;
    private TodoLogRepository todoLogRepository;

    private StorageBroker storageBroker;

    @Autowired
    public TaskImageBrokerImpl(UserRepository userRepository, EventRepository eventRepository, EventLogRepository eventLogRepository,
                               TodoRepository todoRepository, TodoLogRepository todoLogRepository, StorageBroker storageBroker) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.todoRepository = todoRepository;
        this.todoLogRepository = todoLogRepository;
        this.storageBroker = storageBroker;
    }

    @Override
    @Transactional
    public String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(file);
        DebugUtil.transactionRequired("");

        User user = userRepository.findOneByUid(userUid);

        switch (taskType) {
            case MEETING:
                return storeImageForMeeting(user, taskUid, file);
            case TODO:
            //    break;
            default:
                return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageRecord> fetchImagesForTask(String userUid, String taskUid, TaskType taskType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(taskType);

        boolean isTodo = TaskType.TODO.equals(taskType);
        User user = userRepository.findOneByUid(userUid);
        Task task = isTodo ? todoRepository.findOneByUid(taskUid) : eventRepository.findOneByUid(taskUid);

        if (!task.getMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only a member of a task can fetch its images");
        }

        List<ImageRecord> imageRecords = new ArrayList<>();
        ActionLogType logType = isTodo ? ActionLogType.TODO_LOG : ActionLogType.EVENT_LOG;
        List<String> imageLogUids = isTodo ?
                todoLogRepository.findAll(where(forTodo((Todo) task)).and(ofType(TodoLogType.IMAGE_RECORDED)))
                        .stream().map(TodoLog::getUid).collect(Collectors.toList()) :
                eventLogRepository.findAll((where(forEvent((Event) task))).and(ofType(EventLogType.IMAGE_RECORDED)))
                        .stream().map(EventLog::getUid).collect(Collectors.toList());

        return imageLogUids
                .stream()
                .map(uid -> storageBroker.fetchLogImageDetails(uid, logType)) // in future we may have a log without a record, if image deleted
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ImageRecord::getCreationTime))
                .collect(Collectors.toList());
    }

    @Override
    public byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid) {
        // consider adding a membership check in future, hence keeping this method as intermediary (but also need this to be fast ...)
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        return storageBroker.fetchImage(logUid, TaskType.TODO.equals(taskType) ? ActionLogType.TODO_LOG : ActionLogType.EVENT_LOG);
    }

    private String storeImageForMeeting(User user, String meetingUid, MultipartFile file) {
        DebugUtil.transactionRequired("");

        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);
        // at present only someone invited to meeting can upload a photo of it (may want to relax this in the future)
        if (!meeting.getAllMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only invited members can add a picture of a meeting");
        }

        EventLog eventLog = new EventLog(user, meeting, EventLogType.IMAGE_RECORDED);

        boolean uploadCompleted = storageBroker.storeImage(ActionLogType.EVENT_LOG, eventLog.getUid(), file);
        if (uploadCompleted) {
            eventLogRepository.save(eventLog);
            return eventLog.getUid();
        } else {
            // todo : try again?
            return null;
        }
    }

}
