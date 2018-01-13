package za.org.grassroot.services.task;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.task.enums.TaskSortType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/04/26.
 */
public interface TaskBroker {

    // note: this is a highly inefficient method that will scan the repos to find the entity with the corresponding UID
    // use it only when gains are significant and use relatively low. If the type is readily available, use the subsequent method.
    TaskDTO load(String userUid, String taskUid);

    TaskDTO load(String userUid, String taskUid, TaskType type);

    List<TaskDTO> fetchUpcomingIncompleteGroupTasks(String userUid, String groupUid);

    List<TaskDTO> fetchGroupTasksInPeriod(String userUid, String groupUid, Instant start, Instant end);

    ChangedSinceData<TaskDTO> fetchGroupTasks(String userUid, String groupUid, Instant changedSince);

    List<TaskDTO> fetchUpcomingUserTasks(String userUid);

    /**
     * TaskDTO is deprecated in favor of TaskFullDTO, so this is a replacement method for List<TaskDTO> fetchUpcomingUserTasks
     * might be better to return List<Task> and transform to appropriate DTO in controller
     *
     * @param userUid
     * @return
     */
    List<TaskFullDTO> fetchUpcomingUserTasksFull(String userUid);

    ChangedSinceData<TaskDTO> fetchUpcomingTasksAndCancelled(String userUid, Instant changedSince);

    List<TaskDTO> searchForTasks(String userUid, String searchTerm);

    /*
    Some new methods for new REST API
     */

    /**
     * Fetches minimal info on all user tasks, except those known to the caller to have changed since a certain time
     * @param userUid The UID of the user
     * @param knownTasksByTimeChanged Map of tasks that the client already knows about, with the time they were changed on the server
     * @return List of minimal info on tasks (all needed to call for more data)
     */
    List<TaskMinimalDTO> findNewlyChangedTasks(String userUid, Map<String, Long> knownTasksByTimeChanged);

    List<TaskMinimalDTO> fetchNewlyChangedTasksForGroup(String userUid, String groupUid,
                                                        Map<String, Long> knownTasksByTimeChanged);

    /**
     * Fetches all the tasks that the user has, with a specified sort--heavy call, both in processing and return size, use with care
     * @param userUid UID of the user
     * @param sortType The sort type (can't use JPA Sort because of differences in entities)
     * @return List of relatively full set of info on tasks
     */
    List<TaskFullDTO> fetchAllUserTasksSorted(String userUid, TaskSortType sortType);

    /**
     * Fetches full information on a set of tasks that have known UIDs and types
     * @param userUid UID of the user
     * @param taskUidsAndTypes The uids, with matching types, of the tasks to be fetched
     * @param taskSortType Optional: whether to sort in a specified manner, sorts by date created if null
     * @return Full information on the tasks
     */
    List<TaskFullDTO> fetchSpecifiedTasks(String userUid, Map<String, TaskType> taskUidsAndTypes, TaskSortType taskSortType);

    List<Task> fetchTasksRequiringUserResponse(String userUid, String userResponse);

    @Transactional(readOnly = true)
    List<TaskFullDTO> fetchUpcomingGroupTasks(String userUid, String groupUid);
}