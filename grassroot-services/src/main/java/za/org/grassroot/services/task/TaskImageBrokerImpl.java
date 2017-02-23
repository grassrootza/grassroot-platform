package za.org.grassroot.services.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.storage.StorageBroker;

import java.util.Objects;

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
