package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.association.GroupJoinRequest;

import java.util.List;

public interface GroupJoinRequestService {

    String open(String requestorUid, String groupUid, String description);

    void approve(String userUid, String requestUid);

    void decline(String userUid, String requestUid);

    void cancel(String requestorUid, String groupUid);

    void remind(String requestorUid, String groupUid);

    GroupJoinRequest loadRequest(String requestUid);

    // this retrieves requests for which the user is approver
    List<GroupJoinRequest> getPendingRequestsForUser(String userUid);

    // this retrieves requests on the group
    List<GroupJoinRequest> getPendingRequestsForGroup(String userUid, String groupUid);

    // this retrieves requests which the user has sent
    List<GroupJoinRequest> getPendingRequestsFromUser(String userUid);

    List<GroupJoinRequest> getOpenUserRequestsForGroupList(String requestorUid, List<Group> possibleGroups);
    
}