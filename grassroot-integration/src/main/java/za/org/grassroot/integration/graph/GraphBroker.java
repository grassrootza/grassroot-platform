package za.org.grassroot.integration.graph;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.task.Task;

import java.util.List;
import java.util.Set;

@Async
public interface GraphBroker {

    void addUserToGraph(String userUid);

    void addUserAnnotation(User user);

    void removeUserFromGraph(String userUid);

    void removeUserAnnotation(String userUid, Set<String> keysToRemove);

    void addGroupToGraph(String groupUid, String creatingUserUid, Set<String> memberUids);

    void addGroupAnnotation(Group group, double latitude, double longitude);

    void removeGroupFromGraph(String groupUid);

    void removeGroupAnnotation(String groupUid, Set<String> keysToRemove, List<String> tagsToRemove);

    void addAccountToGraph(String accountUid, List<String> adminUids);

    void removeAccountFromGraph(String accountUid);

    void addMembershipToGraph(Set<String> memberUids, String groupUid);

    void addMembershipAnnotation(Membership membership);

    void removeMembershipFromGraph(String userUid, String groupUid);

    void removeMembershipAnnotation(Membership membership, List<String> tagsToRemove);

    void addTaskToGraph(Task task, List<String> assignedUserUids);

    void addTaskAnnotation(Task task, String[] tags, String description);

    void removeTaskFromGraph(Task task);

    void removeTaskAnnotation(Task task, List<String> tagsToRemove);

}
