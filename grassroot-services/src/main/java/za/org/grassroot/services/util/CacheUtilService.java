package za.org.grassroot.services.util;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.List;

/**
 * Created by aakilomar on 11/2/15.
 */
public interface CacheUtilService {

    void clearRsvpCacheForUser(User user, EventType eventType);

    void clearCacheForAllUsersInGroup(EventDTO event);

    List<Event> getOutstandingResponseForUser(User user, EventType eventType);

    void putOutstandingResponseForUser(User user, EventType eventType, List<Event> outstandingRSVPs);

    void putUssdMenuForUser(String phoneNumber, String urlToCache);

    void clearUssdMenuForUser(String phoneNumber);

    String fetchUssdMenuForUser(String phoneNumber);

    User fetchUser(String phoneNumber);

    void cacheUser(User user);

    void putUserLanguage(String phoneNumber, String language);

    String getUserLanguage(String phoneNumber);

    void clearUserLanguage(String phoneNumber);

    /**
     * Returns whether an entry is stored in the cache indicating the user has accessed the given interface type in the last hour
     * @param userUid The uid of the user
     * @param interfaceType The interface type checked
     * @return true if the user has accessed the interface in the last period (as defined by cache length).
     */
    boolean checkSessionStatus(String userUid, UserInterfaceType interfaceType);

    void setSessionOpen(String userUid, UserInterfaceType interfaceType);

}
