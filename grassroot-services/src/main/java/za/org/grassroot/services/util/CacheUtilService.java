package za.org.grassroot.services.util;

import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.List;

/**
 * Created by aakilomar on 11/2/15.
 */
public interface CacheUtilService {

    void clearRsvpCacheForUser(String userUid);

    // void clearCacheForAllUsersInGroup(EventDTO event);

    List<Event> getOutstandingResponseForUser(String userUid);

    List<SafetyEvent> getOutstandingSafetyEventResponseForUser(User user);

    void putSafetyEventResponseForUser(User user, SafetyEvent safetyEvent);

    void clearSafetyEventResponseForUser(User user, SafetyEvent safetyEvent);

    void putOutstandingResponseForUser(String userUid, List<Event> outstandingRSVPs);

    void putUssdMenuForUser(String phoneNumber, String urlToCache);

    void clearUssdMenuForUser(String phoneNumber);

    String fetchUssdMenuForUser(String phoneNumber);

    void putUserLanguage(String phoneNumber, String language);

    void putJoinAttempt(String userUid,int attempt);

    int fetchJoinAttempts(String userUid);

    /**
     * Returns whether an entry is stored in the cache indicating the user has accessed the given interface type in the last hour
     * @param userUid The uid of the user
     * @param interfaceType The interface type checked
     * @return true if the user has accessed the interface in the last period (as defined by cache length).
     */
    boolean checkSessionStatus(String userUid, UserInterfaceType interfaceType);

    void setSessionOpen(String userUid, UserInterfaceType interfaceType);

}
