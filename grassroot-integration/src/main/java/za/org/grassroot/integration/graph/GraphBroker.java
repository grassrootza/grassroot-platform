package za.org.grassroot.integration.graph;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;
import java.util.List;

@Async
public interface GraphBroker {

    void addUserToGraph(String userUid);

    void addGroupToGraph(String groupUid, String creatingUserUid);

    void addAccountToGraph(String accountUid, List<String> adminUids);

    void addMembershipToGraph(String userUid, String groupUid);

    void removeMembershipFromGraph(String userUid, String groupUid);

    void addTaskToGraph(Task task);

}
