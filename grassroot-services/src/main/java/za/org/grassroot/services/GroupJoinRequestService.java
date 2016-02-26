package za.org.grassroot.services;

public interface GroupJoinRequestService {
    String open(String requestorUid, String groupUid);

    void approve(String userUid, String requestUid);

    void decline(String userUid, String requestUid);
}
