package za.org.grassroot.services.util;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.GroupManagementService;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 11/2/15.
 */
@Component
public class CacheUtilManager implements CacheUtilService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    CacheManager cacheManager;

    @Autowired
    GroupManagementService groupManagementService;

    @Override
    public void clearRsvpCacheForUser(User user, EventType eventType) {
        try {
            String cacheKey = eventType.toString() + "|" + user.getId();
            Cache cache = cacheManager.getCache("userRSVP");
            cache.remove(cacheKey);
        } catch (Exception e) {
            log.severe("FAILED to clear userRSVP..." + user.getId() + " error: " + e.toString());
        }

    }

    @Override
    public void clearCacheForAllUsersInGroup(EventDTO event) {
        log.info("clearCacheForAllUsersInGroup...starting");
        try {
            Cache cache = cacheManager.getCache("userRSVP");
            List<User> userList;
            if (event.isIncludeSubGroups()) {
                userList = groupManagementService.getAllUsersInGroupAndSubGroups(event.getAppliesToGroup());
            } else {
                userList = event.getAppliesToGroup().getGroupMembers();
            }
            for (User user : userList) {
                log.info("clearCacheForAllUsersInGroup...user..." + user.getPhoneNumber());
                // exclude createdbyuser as she has already been cleared
                if (user.getId() != event.getCreatedByUser().getId()) {
                    String cacheKey = event.getEventType().toString() + "|" + user.getId();
                    log.info("clearCacheForAllUsersInGroup...removing..." + cacheKey);
                    try {
                        cache.remove(cacheKey);
                    } catch (Exception e2) {

                    }
                }
            }
        } catch (Exception e) {
        }
        log.info("clearCacheForAllUsersInGroup...ending");

    }

    @Override
    public List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
        List<Event> outstandingRSVPs = null;

        Cache cache = cacheManager.getCache("userRSVP");
        String cacheKey = eventType.toString() + "|" + user.getId();
        log.info("getOutstandingResponseForUser...cacheKey..." + cacheKey);
        try {
            outstandingRSVPs = (List<Event>) cache.get(cacheKey).getObjectValue();

        } catch (Exception e) {
            log.fine("Could not retrieve outstanding RSVP/Vote from cache  userRSVP for user " + user.getPhoneNumber() + " error: " + e.toString() + " eventType: " + eventType.toString());
        }
        return outstandingRSVPs;
    }

    @Override
    public void putOutstandingResponseForUser(User user, EventType eventType, List<Event> outstandingRSVPs) {
        try {
            Cache cache = cacheManager.getCache("userRSVP");
            String cacheKey = eventType.toString() + "|" + user.getId();
            cache.put(new Element(cacheKey,outstandingRSVPs));
        } catch (Exception e) {
            log.severe(e.toString());
        }

    }



}
