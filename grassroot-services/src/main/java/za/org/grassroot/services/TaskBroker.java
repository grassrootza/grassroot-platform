package za.org.grassroot.services;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.enums.LogBookStatus;

import java.util.List;

/**
 * Created by luke on 2016/04/26.
 */
public interface TaskBroker {

    TaskDTO load(String userUid, String taskUid, TaskType type);

    List<TaskDTO> fetchGroupTasks(String userUid, String groupUid, boolean futureOnly, LogBookStatus logBookStatus);

    List<TaskDTO> fetchUserTasks(String userUid, boolean futureOnly);

}