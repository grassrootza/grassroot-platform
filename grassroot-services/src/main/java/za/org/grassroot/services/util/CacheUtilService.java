package za.org.grassroot.services.util;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;

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






}
