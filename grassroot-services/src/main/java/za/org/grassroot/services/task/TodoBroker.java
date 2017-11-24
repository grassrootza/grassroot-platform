package za.org.grassroot.services.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface TodoBroker {

    Todo load(String todoUid);

    List<TaskTimeChangedDTO> fetchTodosWithTimeChanged(Set<String> todoUids);

    // methods for creating and modifying to-dos
    String create(TodoHelper todoHelper);

    void cancel(String userUid, String todoUid, String reason);

    void extend(String userUid, String todoUid, Instant newDueDateTime);

    void updateSubject(String userUid, String todoUid, String newDescription);

    void recordValidation(String userUid, String todoUid, String notes, Set<String> taskImageUids);

    Todo checkForTodoNeedingResponse(String userUid);

    boolean canUserRespond(String userUid, String todoUid);

    boolean canUserViewResponses(String userUid, String todoUid);

    boolean canUserModify(String userUid, String todoUid);

    void recordResponse(String userUid, String todoUid, String response, boolean confirmRecorded);

    void updateTodoCompleted(String userUid, String todoUid, boolean completed);

    void addAssignments(String addingUserUid, String todoUid, Set<String> addedMemberUids);

    void addValidators(String addingUserUid, String todoUid, Set<String> validatingMemberUids);

    void removeUsers(String removingUserUid, String todoUid, Set<String> memberUidsToRemove);

    // methods for retrieving to-dos
    List<Todo> fetchTodosForUser(String userUid, boolean forceIncludeCreated, boolean limitToNeedingResponse, Instant intervalStart, Instant intervalEnd, Sort sort);

    Page<Todo> fetchPageOfTodosForUser(String userUid, boolean createdOnly, boolean openOnly, Pageable page);

    List<Todo> fetchTodosForGroup(String userUid, String groupUid, boolean limitToNeedingResponse, boolean limitToIncomplete,
                                  Instant start, Instant end, Sort sort);

    List<Todo> searchUserTodos(String userUid, String searchString);

    List<TaskTimeChangedDTO> fetchUserTodosWithTimeChanged(String userUid);

    List<TaskTimeChangedDTO> fetchGroupTodosWithTimeChanged(String groupUid);

    TodoAssignment fetchUserTodoDetails(String userUid, String todoUid);

    List<TodoAssignment> fetchAssignedUserResponses(String userUid, String todoUid, boolean respondedOnly,
                                                    boolean assignedOnly, boolean witnessOnly);

    // Handling reminders
    void sendScheduledReminder(String todoUid);

}
