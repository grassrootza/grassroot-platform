package za.org.grassroot.integration.graph;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.domain.task.Task;

import java.util.List;
import java.util.Set;

@Async
public interface GraphBroker {

    void addUserToGraph(String userUid);

    void addGroupToGraph(String groupUid, String creatingUserUid, Set<String> memberUids);

    void addAccountToGraph(String accountUid, List<String> adminUids);

    void addMembershipToGraph(Set<String> memberUids, String groupUid);

    void removeMembershipFromGraph(String userUid, String groupUid);

    void addTaskToGraph(Task task, List<String> assignedUserUids);

    void addActorAnnotation(String Uid, String description, String[] tags, String language, String location);

    void addEventAnnotation(String Uid, String description, String[] tags, String location);

}
