package za.org.grassroot.services;

import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.services.enums.LogBookStatus;

import java.util.List;

/**
 * Created by luke on 2016/04/26.
 */
public interface TaskBroker {

    List<TaskDTO> fetchGroupTasks(String userUid, String groupUid, boolean futureOnly, LogBookStatus logBookStatus);

    List<TaskDTO> fetchUserTasks(String userUid, boolean futureOnly);

}