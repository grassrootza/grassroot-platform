package za.org.grassroot.services.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface TodoBrokerNew {

    Todo load(String todoUid);

    String create(TodoHelper todoHelper);

    void cancel(String userUid, String todoUid, String cancel);

    void extend(String userUid, String todoUid, Instant newDueDateTime);

    void updateSubject(String userUid, String todoUid, String newDescription);

    void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids);

    Todo checkForTodoNeedingResponse(String userUid);

    boolean canUserRespond(String userUid, String todoUid);

    void recordResponse(String userUid, String todoUid, String response, boolean confirmRecorded);

    void updateTodoCompleted(String userUid, String todoUid, boolean completed);

    /*
    Fetching and displaying todos
     */

    List<Todo> fetchTodosForUser(String userUid, boolean createdOnly, boolean openOnly, Sort sort);

    Page<Todo> fetchPageOfTodosForUser(String userUid, boolean createdOnly, boolean openOnly, Pageable page);

    List<TodoAssignment> fetchAssignedUserResponses(String userUid, String todoUid, boolean respondedOnly,
                                                    boolean assignedOnly, boolean witnessOnly);

    // pass null to email address to send to user's one on file
    void emailTodoResponses(String userUid, String todoUid, String emailAddress);

}
