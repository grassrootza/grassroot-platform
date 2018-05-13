package za.org.grassroot.integration.graph;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.enums.TaskType;

@Async
public interface GraphBroker {

    void addUserToGraph(String userUid);

    void addGroupToGraph(String groupUid);

    void addAccountToGraph(String accountUid);

    void addMembershipToGraph(String userUid, String groupUid);

    void removeMembershipFromGraph(String userUid, String groupUid);

    void addTaskToGraph(String taskUid, TaskType taskType);

}
