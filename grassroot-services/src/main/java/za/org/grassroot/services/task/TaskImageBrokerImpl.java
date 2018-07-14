package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.media.ImageRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.specifications.TodoLogSpecifications;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.UrlShortener;
import za.org.grassroot.integration.storage.ImageType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specification.where;
import static za.org.grassroot.core.enums.ActionLogType.EVENT_LOG;
import static za.org.grassroot.core.enums.ActionLogType.TODO_LOG;
import static za.org.grassroot.core.enums.EventLogType.IMAGE_AT_CREATION;
import static za.org.grassroot.core.enums.EventLogType.IMAGE_RECORDED;
import static za.org.grassroot.core.specifications.EventLogSpecifications.forEvent;
import static za.org.grassroot.core.specifications.EventLogSpecifications.isImageLog;
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

    private static final Logger logger = LoggerFactory.getLogger(TaskImageBrokerImpl.class);

    @Value("${grassroot.task.images.bucket:null}")
    private String taskImagesBucket;

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final TodoRepository todoRepository;
    private final TodoLogRepository todoLogRepository;
    private final ImageRecordRepository imageRecordRepository;

    private final StorageBroker storageBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final UrlShortener urlShortener;

    @Autowired
    public TaskImageBrokerImpl(UserRepository userRepository, EventRepository eventRepository, EventLogRepository eventLogRepository,
                               TodoRepository todoRepository, TodoLogRepository todoLogRepository, ImageRecordRepository imageRecordRepository,
                               StorageBroker storageBroker, GeoLocationBroker geoLocationBroker, UrlShortener urlShortener) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.todoRepository = todoRepository;
        this.todoLogRepository = todoLogRepository;
        this.imageRecordRepository = imageRecordRepository;
        this.storageBroker = storageBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.urlShortener = urlShortener;
    }

    @Override
    public String storeImagePreTask(TaskType taskType, MultipartFile file) {
        String imageKey = UIDGenerator.generateId();
        storageBroker.storeImage(TaskType.TODO.equals(taskType) ?
                ActionLogType.TODO_LOG : ActionLogType.EVENT_LOG, imageKey, file);
        return imageKey;
    }

    @Override
    @Transactional
    public String storeImageForTask(String userUid, String taskUid, TaskType taskType, MultipartFile file, String caption, Double latitude, Double longitude) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(file);
        DebugUtil.transactionRequired("");

        User user = userRepository.findOneByUid(userUid);
        GeoLocation location = latitude == null ? null : new GeoLocation(latitude, longitude);

        if (location != null) {
            geoLocationBroker.logUserLocation(userUid, latitude, longitude, Instant.now(), UserInterfaceType.ANDROID);
        }

        switch (taskType) {
            case MEETING:
                return storeImageForMeeting(user, taskUid, location, file, caption);
            case TODO:
                return storeImageForTodo(user, taskUid, location, file, caption);
            default: // throw an exception
                return null;
        }
    }

    @Override
    @Transactional
    public void recordImageForTask(String userUid, String taskUid, TaskType taskType, Collection<String> imageKeys, EventLogType eventLogType, TodoLogType todoLogType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);

        Objects.requireNonNull(imageKeys);

        DebugUtil.transactionRequired("Image log storing needs transaction");

        User user = userRepository.findOneByUid(userUid);
        if (TaskType.MEETING.equals(taskType) || TaskType.VOTE.equals(taskType)) {
            Event event = eventRepository.findOneByUid(taskUid);
            eventLogRepository.saveAll(generateEventLogs(imageKeys, user, event, eventLogType));
        } else {
            Todo todo = todoRepository.findOneByUid(taskUid);
            logger.info("recording todo logs with image keys: {}", imageKeys);
            todoLogRepository.saveAll(generateTodoLogs(imageKeys, user, todo, todoLogType));

        }
    }

    private List<EventLog> generateEventLogs(Collection<String> imageKeys, User user, Event event, EventLogType eventLogType) {
        return imageKeys.stream().map(key -> {
            EventLog imageLog = new EventLog(user, event, eventLogType == null ? IMAGE_RECORDED : eventLogType);
            imageLog.setTag(key); // slight abuse of usage, but no other possible tag here
            logger.info("recording event log with image: {}", imageLog);
            return imageLog;
        }).collect(Collectors.toList());
    }

    private List<TodoLog> generateTodoLogs(Collection<String> imageKeys, User user, Todo todo, TodoLogType logType) {
        return imageKeys.stream()
                .map(key -> new TodoLog(logType == null ? TodoLogType.IMAGE_RECORDED : logType, user, todo, key))
                .collect(Collectors.toList());
    }

    @Override
    public String getShortUrl(String imageKey) {
        try {
            return urlShortener.shortenImageUrl(MediaFunction.TASK_IMAGE, imageKey);
        } catch (Exception e) {
            // not great to have generic catch here but need robustness or have risk of notices not going out
            logger.error("Error shortening URL! : {}", e);
            return null;
        }
    }

    @Override
    public String fetchImageKeyForCreationImage(String userUid, String taskUid, TaskType taskType) {
        Task task = validateFieldsAndFetch(userUid, taskUid, taskType);
        String imageRecordKey;
        if (taskType.equals(TaskType.TODO)) {
            Specification<TodoLog> todoLogSpecs = Specification.where(TodoLogSpecifications.forTodo((Todo) task))
                    .and(TodoLogSpecifications.ofType(TodoLogType.IMAGE_AT_CREATION));
            List<TodoLog> todoLogs = todoLogRepository.findAll(todoLogSpecs);
            imageRecordKey = !todoLogs.isEmpty() ? todoLogs.get(0).getTag() : null;
        } else {
            Specification<EventLog> eventLogSpecs = Specification.where(
                    EventLogSpecifications.forEvent((Event) task)).and(EventLogSpecifications.ofType(IMAGE_AT_CREATION));
            List<EventLog> logs = eventLogRepository.findAll(eventLogSpecs);
            imageRecordKey = !logs.isEmpty() ? logs.get(0).getTag() : null;
        }

        return imageRecordKey;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageRecord> fetchImagesForTask(String userUid, String taskUid, TaskType taskType) {
        Task task = validateFieldsAndFetch(userUid, taskUid, taskType);
        boolean isTodo = TaskType.TODO.equals(task.getTaskType());
        ActionLogType logType = isTodo ? TODO_LOG : ActionLogType.EVENT_LOG;
        List<String> imageLogUids = isTodo ?
                todoLogRepository.findAll(where(forTodo((Todo) task)).and(ofType(TodoLogType.IMAGE_RECORDED)))
                        .stream().map(l -> extractImageKey(l, logType)).collect(Collectors.toList()) :
                eventLogRepository.findAll((where(forEvent((Event) task))).and(isImageLog()))
                        .stream().map(l -> extractImageKey(l, logType)).collect(Collectors.toList());

        logger.info("found this many UIDs: {}", imageLogUids.size());
        return imageLogUids
                .stream()
                .map(uid -> fetchLogImageDetails(uid, logType)) // in future we may have a log without a record, if image deleted
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ImageRecord::getCreationTime))
                .collect(Collectors.toList());
    }

    // todo: massively clean & refactor this so it's not terrible
    @Override
    @Transactional(readOnly = true)
    public Map<TaskLog, ImageRecord> fetchTaskPosts(String userUid, String taskUid, TaskType taskType) {
        Task task = validateFieldsAndFetch(userUid, taskUid, taskType);
        List<? extends TaskLog> taskLogs = fetchTaskLogs(task);

        ActionLogType logType = TaskType.TODO.equals(task.getTaskType()) ? TODO_LOG : ActionLogType.EVENT_LOG;

        return taskLogs.stream()
                .collect(Collectors.toMap(tl -> (TaskLog) tl, tl -> fetchLogImageDetails(tl.getUid(), logType)));

    }

    private Task validateFieldsAndFetch(String userUid, String taskUid, TaskType taskType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(taskType);

        boolean isTodo = TaskType.TODO.equals(taskType);
        User user = userRepository.findOneByUid(userUid);
        Task task = isTodo ? todoRepository.findOneByUid(taskUid) : eventRepository.findOneByUid(taskUid);
        validateUserInTask(user, task);

        return task;
    }

    private List<? extends TaskLog> fetchTaskLogs(Task task) {
        boolean isTodo = TaskType.TODO.equals(task.getTaskType());
        return isTodo ?
                todoLogRepository.findAll(where(forTodo((Todo) task)).and(ofType(TodoLogType.IMAGE_RECORDED))) :
                eventLogRepository.findAll((where(forEvent((Event) task))).and(isImageLog()));
    }

    private void validateUserInTask(User user, Task task) {
        if (!task.getMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only a member of a task can fetch its images");
        }
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
                eventLogRepository.findOne(EventLogSpecifications.isImageLogWithKey(logUid)).orElse(null);
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
    @Transactional(readOnly = true)
    public long countImagesForTask(String userUid, String taskUid, TaskType taskType) {
        boolean isTodo = TaskType.TODO.equals(taskType);
        Task task = isTodo ? todoRepository.findOneByUid(taskUid) : eventRepository.findOneByUid(taskUid);
        return isTodo ? countTodoImages((Todo) task) : countEventImages((Event) task);
    }

    // note: the alternative is to get the list of UIDs and count for image records with loguids in the set, but this
    // works as long as we have confidence in the remove method, and is a lot lot simpler, so using it for now
    private long countTodoImages(Todo todo) {
        return todoLogRepository.count(where(forTodo(todo)).and(ofType(TodoLogType.IMAGE_RECORDED))) -
                todoLogRepository.count(where(forTodo(todo)).and(ofType(TodoLogType.IMAGE_REMOVED)));
    }

    private long countEventImages(Event event) {
        return eventLogRepository.count(where(forEvent(event)).and(isImageLog()))
                - eventLogRepository.count(where(forEvent(event)).and(ofType(EventLogType.IMAGE_REMOVED)));
    }

    @Override
    public byte[] fetchImageForTask(String userUid, TaskType taskType, String logUid, boolean checkAnalyzed) {
        // consider adding a membership check in future, hence keeping this method as intermediary (but also need this to be fast ...)
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        ImageType imageType = checkAnalyzed && storageBroker.doesImageExist(logUid, ImageType.ANALYZED) ?
                ImageType.ANALYZED : ImageType.FULL_SIZE;
        return storageBroker.fetchTaskImage(logUid, imageType);
    }

    @Override
    public byte[] fetchMicroThumbnailForTask(String userUid, TaskType taskType, String logUid) {
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(logUid);

        // as above, add a membership check in the future
        return storageBroker.fetchTaskImage(logUid, ImageType.MICRO);
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

    private String extractImageKey(ActionLog actionLog, ActionLogType actionLogType) {
        String uidToSearch;
        if (!EVENT_LOG.equals(actionLogType)) {
            uidToSearch = actionLog.getUid();
        } else {
            EventLog eventLog = (EventLog) actionLog;
            if (EventLogType.IMAGE_AT_CREATION.equals(eventLog.getEventLogType())) {
                uidToSearch = eventLog.getTag();
            } else {
                uidToSearch = actionLog.getUid();
            }
        }
        return uidToSearch;
    }

    private ImageRecord fetchLogImageDetails(String actionLogUid, ActionLogType actionLogType) {
        return imageRecordRepository.findOne(where(actionLogUid(actionLogUid))
                .and(actionLogType(actionLogType))).orElse(null);
    }

    private String storeImageForMeeting(User user, String meetingUid, GeoLocation location, MultipartFile file, String caption) {
        DebugUtil.transactionRequired("");

        Meeting meeting = (Meeting) eventRepository.findOneByUid(meetingUid);
        // at present only someone invited to meeting can upload a photo of it (may want to relax this in the future)
        if (!meeting.getAllMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only invited members can add a picture of a meeting");
        }

        EventLog eventLog = new EventLog(user, meeting, EventLogType.IMAGE_RECORDED);
        if (location != null) {
            eventLog.setLocation(location);
            geoLocationBroker.calculateMeetingLocationInstant(meeting.getUid(), location, UserInterfaceType.WEB);
        }

        if (!StringUtils.isEmpty(caption)) {
            eventLog.setTag(caption);
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

    private String storeImageForTodo(User user, String todoUid, GeoLocation location, MultipartFile file, String caption) {
        DebugUtil.transactionRequired("");

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getAncestorGroup().getMembers().contains(user)) {
            throw new AccessDeniedException("Error! Only group members can add a photo for a todo");
        }

        TodoLog todoLog = new TodoLog(TodoLogType.IMAGE_RECORDED, user, todo, "Photo recorded");
        if (location != null) {
            todoLog.setLocationWithSource(location, LocationSource.convertFromInterface(UserInterfaceType.ANDROID));
            geoLocationBroker.calculateTodoLocationInstant(todo.getUid(), location, UserInterfaceType.ANDROID);
        }

        if (!StringUtils.isEmpty(caption)) {
            todoLog.setMessage(caption);
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