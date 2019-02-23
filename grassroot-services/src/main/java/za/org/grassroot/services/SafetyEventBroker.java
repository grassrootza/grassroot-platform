package za.org.grassroot.services;

import za.org.grassroot.core.domain.SafetyEvent;

import java.util.List;

/**
 * Created by paballo on 2016/07/18.
 */
public interface SafetyEventBroker {

    SafetyEvent create(String userUid, String groupUid);

    SafetyEvent load(String safetyEventUid);

    void recordResponse(String userUid, String safetyEventUid, boolean isValid);

    List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid);

    boolean isUserBarred(String uid);

    void sendReminders(String uid);

    void setSafetyGroup(String userUid, String groupUid);

    /**
     *
     * @param userUid
     * @param deactivateGroup
     */
    void resetSafetyGroup(String userUid, boolean deactivateGroup);

}
