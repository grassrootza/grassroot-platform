package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.Todo;

import java.time.Instant;
import java.util.Set;

public interface TodoBrokerNew {

    Todo load(String todoUid);

    String create(TodoHelper todoHelper);

    void cancel(String userUid, String todoUid, String cancel);

    void extend(String userUid, String todoUid, Instant newDueDateTime);

    void updateDescription(String userUid, String todoUid, String newDescription);

    void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids);

    Todo checkForTodoNeedingResponse(String userUid);

    void hasInformationRequested(String userUid, String responseString);

    void recordResponse(String userUid, String todoUid, String response);

}
