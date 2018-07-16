package za.org.grassroot.integration.graph;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.enums.TaskType;

import java.util.List;
import java.util.Set;
import java.util.Map;

@Async
public interface GraphBroker {

    void addUserToGraph(String userUid);

    void addGroupToGraph(String groupUid, String creatingUserUid, Set<String> memberUids);

    void addMovementToGraph(String movementUid, String creatingUserUid);

    void addAccountToGraph(String accountUid, List<String> adminUids);

    void addMembershipToGraph(Set<String> memberUids, String groupUid);

    void addTaskToGraph(String taskUid, TaskType taskType, List<String> assignedUserUids);

    void annotateUser(String userUid, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations);

    void annotateGroup(String groupUid, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations);

    void annotateMembership(String userUid, String groupUid, Set<String> tags, boolean setAllAnnotations);

    void annotateTask(String taskUid, TaskType taskType, Map<String, String> properties, Set<String> tags, boolean setAllAnnotations);

    void removeUserFromGraph(String userUid);

    void removeGroupFromGraph(String groupUid);

    void removeAccountFromGraph(String accountUid);

    void removeMembershipFromGraph(String userUid, String groupUid);

    void removeTaskFromGraph(String taskUid, TaskType taskType);

    void removeAnnotationsFromUser(String userUid, Set<String> keysToRemove, Set<String> tagsToRemove);

    void removeAnnotationsFromGroup(String groupUid, Set<String> keysToRemove, Set<String> tagsToRemove);

    void removeAnnotationsFromMembership(String userUid, String groupUid, Set<String> tagsToRemove);

    void removeAnnotationsFromTask(String taskUid, TaskType taskType, Set<String> keysToRemove, Set<String> tagsToRemove);

}
