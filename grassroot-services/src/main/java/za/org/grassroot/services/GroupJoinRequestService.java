package za.org.grassroot.services;

import za.org.grassroot.core.domain.GroupJoinRequest;

import java.util.List;

public interface GroupJoinRequestService {

    String open(String requestorUid, String groupUid);

    void approve(String userUid, String requestUid);

    void decline(String userUid, String requestUid);

    GroupJoinRequest loadRequest(String requestUid);

    List<GroupJoinRequest> getOpenRequestsForGroup(String groupUid);

    List<GroupJoinRequest> getOpenRequestsForUser(String userUid);
    
}
