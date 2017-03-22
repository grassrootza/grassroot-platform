package za.org.grassroot.services.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.storage.ImageType;
import za.org.grassroot.integration.storage.StorageBroker;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.enums.ActionLogType.TODO_LOG;
import static za.org.grassroot.core.specifications.EventLogSpecifications.forEvent;
import static za.org.grassroot.core.specifications.EventLogSpecifications.ofType;
import static za.org.grassroot.core.specifications.ImageRecordSpecifications.actionLogType;
import static za.org.grassroot.core.specifications.ImageRecordSpecifications.actionLogUid;
import static za.org.grassroot.core.specifications.TodoLogSpecifications.forTodo;
import static za.org.grassroot.core.specifications.TodoLogSpecifications.ofType;

/**
 * Created by luke on 2017/02/21.
 */
@Service
public class TaskImageBrokerImpl implements TaskImageBroker {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final TodoRepository todoRepository;
    private final TodoLogRepository todoLogRepository;
    private final ImageRecordRepository imageRecordRepository;

    private final StorageBroker storageBroker;

    @Autowired
    public TaskImageBrokerImpl(UserRepository userRepository, EventRepository eventRepository, EventLogRepository eventLogRepository,
                               TodoRepository todoRepository, TodoLogRepository todoLogRepository, ImageRecordRepository imageRecordRepository,
                               StorageBroker storageBroker) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.todoRepository = todoRepository;
        this.todoLogRepository = todoLogRepository;
        this.imageRecordRepository = imageRecordRepository;
        this.storageBroker = storageBroker;
    }

    @Override
    @Transactional
    public String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file, Double latitude, Double longitude) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(file);
        DebugUtil.transactionRequired("");

        User user = userRepository.findOneByUid(userUid);
        GeoLocation location = latitude == null ? null : new GeoLocation(latitude, longitude);

        switch (taskType) {
            case MEETING:
                return storeImageForMeeting(user, taskUid, location, file);
            case TODO:
                return storeImageForTodo(user, taskUid, location, file);
            default: // throw an exception
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

        ActionLogType logType = isTodo ? TODO_LOG : ActionLogType.EVENT_LOG;
        List<String> imageLogUids = isTodo ?
                todoLogRepository.findAll(where(forTodo((Todo) task)).and(ofType(TodoLogType.IMAGE_RECORDED)))
                        .stream().map(TodoLog::getUid).collect(Collectors.toList()) :
                eventLogRepository.findAll((where(forEvent((Event) task))).and(ofType(EventLogType.IMAGE_RECORDED)))
                        .stream().map(EventLog::getUid).collect(Collectors.toList());

        return imageLogUids
                .stream()
                .map(uid -> fetchLogImageDetails(uid, logType)) // in future we may have a log without a record, if image deleted
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ImageRecord::getCreationTime))
                .collect(Collectors.toList());
    }

    @Override
    public ImageRecord fetchImageRecord(String logUid, TaskType taskType) {
        return fetchLogImageDetails(logUid, TaskType.TODO.equals(taskType) ? TODO_LOG : ActionLogType.EVENT_LOG);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskLog fetchLogForImage(String logUid, TaskType taskType) {
        return TaskType.TODO.equals(taskType) ?
                todoLogRepository.findOneByUid(logUid):
                eventLogRepository.findOneByUid(logUid);
    }

    @Override
    @Transactional
    public void updateImageFaceCount(String userUid, String logUid, TaskType taskType, int faceCount) {
        User user = userRepository.findOneByUid(userUid);
        TaskLog log = fetchLogForImage(logUid, taskType);
        if (!user.equals(log.getUser())) {
            throw new AccessDeniedException("Only the user that took the photo can update its count");
        }
        ImageRecord record = fetchImageRecord(logUid, taskType);
        record.setCountModified(true);
        record.setRevisedFaces(faceCount);

        if (TaskType.MEETING.equals(taskType)) {
            EventLog eventLog = new EventLog(user, (Event) log.getTask(), EventLogType.IMAGE_COUNT_CHANGED);
            eventLogRepository.save(eventLog);
        }
    }

    @Override
    public long countImagesForTask(String userUid, String taskUid, TaskType taskType) {
        boolean isTodo = TaskType.TODO.equals(taskType);
        Task task = isTodo ? todoRepository.findOneByUid(taskUid) : eventRepository.findOneByUid(taskUid);
        return isTodo ?
                todoLogRepository.count(where(forTodo((Todo) task)).and(ofType(TodoLogType.IMAGE_RECORDED))) :
                eventLogRepository.count(where(forEvent((Event) task)).and(ofType(EventLogType.IMAGE_RECORDED)));
    }

    @Override
    public byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid, boolean checkAnalyzed) {
        // consider adding a membership check in future, hence keeping this method as intermediary (but also need this to be fast ...)
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        ImageType imageType = checkAnalyzed && storageBroker.doesImageExist(logUid, ImageType.ANALYZED) ?
                ImageType.ANALYZED : ImageType.FULL_SIZE;
        return storageBroker.fetchImage(logUid, imageType);
    }

    @Override
    public byte[] fetchMicroThumbnailForTask(String userUid, TaskType taskType, String logUid) {
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        // as above, add a membership check in the future
        return storageBroker.fetchImage(logUid, ImageType.MICRO);
    }

    @Override
    @Transactional
    public String removeTaskImageRecord(String userUid, TaskType taskType, String logUid, boolean removeFromStorage) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        User user = userRepository.findOneByUid(userUid);
        ImageRecord record = fetchImageRecord(logUid, taskType);
        TaskLog taskLog = fetchLogForImage(logUid, taskType);

        if (!taskLog.getUser().equals(user)) {
            throw new AccessDeniedException("Only the user that added an image can delete it");
        }

        String removedLogUid;
        if (taskType.equals(TaskType.TODO)) {
            TodoLog todoLog = new TodoLog(TodoLogType.IMAGE_REMOVED, user, (Todo) taskLog.getTask(), "image removed");
            todoLogRepository.save(todoLog);
            removedLogUid = todoLog.getUid();
        } else {
            EventLog eventLog = new EventLog(user, (Event) taskLog.getTask(), EventLogType.IMAGE_REMOVED);
            eventLogRepository.save(eventLog);
            removedLogUid = eventLog.getUid();
        }

        imageRecordRepository.delete(record);

        if (removeFromStorage) {
            storageBroker.deleteImage(logUid);
        }

        return removedLogUid;
    }

    private ImageRecord fetchLogImageDetails(String actionLogUid, ActionLogType actionLogType) {
        return imageRecordRepository.findOne(where(actionLogUid(actionLogUid)).and(actionLogType(actionLogType)));
    }

    private String storeImageForMeeting(User user, String meetingUid, GeoLocation location, MultipartFile file) {
        DebugUtil.transactionRequired("");

        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);
        // at present only someone invited to meeting can upload a photo of it (may want to relax this in the future)
        if (!meeting.getAllMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only invited members can add a picture of a meeting");
        }

        EventLog eventLog = new EventLog(user, meeting, EventLogType.IMAGE_RECORDED);

        if (location != null) {
            eventLog.setLocation(location);
        }

        boolean uploadCompleted = storageBroker.storeImage(ActionLogType.EVENT_LOG, eventLog.getUid(), file);
        if (uploadCompleted) {
            eventLogRepository.save(eventLog);
            return eventLog.getUid();
        } else {
            // todo : try again or throw an exception
            return null;
        }
    }

    private String storeImageForTodo(User user, String todoUid, GeoLocation location, MultipartFile file) {
        DebugUtil.transactionRequired("");

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getAncestorGroup().getMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only group members can add a photo for a todo");
        }

        TodoLog todoLog = new TodoLog(TodoLogType.IMAGE_RECORDED, user, todo, "Photo recorded");
        if (location != null) {
            todoLog.setLocation(location);
        }

        boolean uploadCompleted = storageBroker.storeImage(TODO_LOG, todoLog.getUid(), file);
        if (uploadCompleted) {
            todoLogRepository.save(todoLog);
            return todoLog.getUid();
        } else { // as above
            return null;
        }
    }

}
