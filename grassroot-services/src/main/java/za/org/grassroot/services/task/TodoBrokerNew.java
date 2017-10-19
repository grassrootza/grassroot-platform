package za.org.grassroot.services.task;

import java.time.Instant;
import java.util.Set;

public interface TodoBrokerNew {

    String create(TodoHelper todoHelper);

    void cancel(String userUid, String todoUid, String cancel);

    void extend(String userUid, String todoUid, Instant newDueDateTime);

    void updateDescription(String userUid, String todoUid, String newDescription);

    void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids);

}
