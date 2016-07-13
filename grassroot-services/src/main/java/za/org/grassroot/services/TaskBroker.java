package za.org.grassroot.services;

import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2016/04/26.
 */
public interface TaskBroker {

    TaskDTO load(String userUid, String taskUid, TaskType type);

    List<TaskDTO> fetchUpcomingIncompleteGroupTasks(String userUid, String groupUid);

    ChangedSinceData<TaskDTO> fetchGroupTasks(String userUid, String groupUid, Instant changedSince);

    List<TaskDTO> fetchUpcomingUserTasks(String userUid);

    List<TaskDTO> searchForTasks(String userUid, String searchTerm);

}