package za.org.grassroot.services.util;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.UserManagementService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by aakilomar on 11/2/15.
 */
@Service
public class CacheUtilManager implements CacheUtilService {

    private Logger log = LoggerFactory.getLogger(CacheUtilManager.class);

    @Autowired
    CacheManager cacheManager;

    @Override
    public void clearRsvpCacheForUser(User user, EventType eventType) {
        try {
            String cacheKey = eventType.toString() + "|" + user.getId();
            Cache cache = cacheManager.getCache("userRSVP");
            cache.remove(cacheKey);
        } catch (Exception e) {
            log.error("FAILED to clear userRSVP..." + user.getId() + " error: " + e.toString());
        }

    }

    /*
    Removing until needed, as allows removal of userManagementService, keeping this strictly interacting with cache
     */
    /* @Override
    @Transactional(readOnly = true)
    public void clearCacheForAllUsersInGroup(EventDTO event) {
        log.info("clearCacheForAllUsersInGroup...starting");
        try {
            Cache cache = cacheManager.getCache("userRSVP");
            Set<User> userList;
            if (event.isIncludeSubGroups()) {
                Group group = (Group) event.getParent();
                userList = new HashSet<>(userManagementService.fetchByGroup(group.getUid(), true));
            } else {
                // todo: switch this
                userList = ((Group) event.getParent()).getMembers();
            }
            for (User user : userList) {
                log.info("clearCacheForAllUsersInGroup...user..." + user.getPhoneNumber());
                String cacheKey = event.getEventType().toString() + "|" + user.getId();
                log.info("clearCacheForAllUsersInGroup...removing..." + cacheKey);
                try {
                    cache.remove(cacheKey);
                } catch (Exception e2) {

                }
            }
        } catch (Exception e) {
        }
        log.info("clearCacheForAllUsersInGroup...ending");

    }*/

    @Override
    public List<Event> getOutstandingResponseForUser(User user, EventType eventType) {
        List<Event> outstandingRSVPs = null;

        Cache cache = cacheManager.getCache("userRSVP");
        String cacheKey = eventType.toString() + "|" + user.getId();
        log.info("getOutstandingResponseForUser...cacheKey..." + cacheKey);
        try {
            outstandingRSVPs = (List<Event>) cache.get(cacheKey).getObjectValue();

        } catch (Exception e) {
            log.debug("Could not retrieve outstanding RSVP/Vote from cache  userRSVP for user " + user.getPhoneNumber() + " error: " + e.toString() + " eventType: " + eventType.toString());
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
            log.error(e.toString());
        }

    }

    @Override
    public void putUssdMenuForUser(String phoneNumber, String urlToCache) {
        log.info("Putting USSD menu into cache ..." + urlToCache);
        try {
            Cache cache = cacheManager.getCache("userUSSDMenu");
            cache.put(new Element(phoneNumber, urlToCache));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Override
    public void clearUssdMenuForUser(String phoneNumber) {
        log.info("Clearing out the stored USSD menu for the user ...");
        try {
            Cache cache = cacheManager.getCache("userUSSDMenu");
            cache.remove(phoneNumber);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Override
    public String fetchUssdMenuForUser(String phoneNumber) {
        String menuToReturn;
        Cache cache = cacheManager.getCache("userUSSDMenu");
        log.info("fetchUssdMenuForUser ...cacheKey... " + phoneNumber);
        try {
            menuToReturn = (String) cache.get(phoneNumber).getObjectValue();
        } catch (Exception e) {
            log.debug("Could not find a stored USSD menu for phone number ..." + phoneNumber);
            menuToReturn = null;
        }
        return menuToReturn;
    }

    @Override
    public User fetchUser(String phoneNumber) {
        User user;
        Cache cache = cacheManager.getCache("user");
        try{
            user = (User)cache.get(phoneNumber).getObjectValue();
        }catch (Exception e){
            user = null;
        }
        return user;
    }

    @Override
    public void cacheUser(User user) {
        try {
            Cache cache = cacheManager.getCache("user");
            cache.put(new Element(user.getPhoneNumber(), user));
        } catch (Exception e) {
            log.error(e.toString());
        }

    }

    @Override
    public void putUserLanguage(String inputNumber, String language) {
        try {
            Cache cache = cacheManager.getCache("user_language");
            cache.put(new Element(inputNumber, language));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Override
    public String getUserLanguage(String inputNumber) {
        String language = null;
        try {
            Cache cache = cacheManager.getCache("user_language");
            language = String.valueOf(cache.get(inputNumber).getObjectValue());
        } catch (Exception e) {
            log.error(e.toString());
        }
        return language;

    }

    @Override
    public void clearUserLanguage(String phoneNumber) {
        try {
            Cache cache = cacheManager.getCache("user_language");
            cache.remove(phoneNumber);
        } catch (Exception e) {
            log.error(e.toString());
        }

    }

    @Override
    public boolean checkSessionStatus(String userUid, UserInterfaceType interfaceType) {
        try {
            Cache cache = cacheManager.getCache("user_session");
            return (boolean) cache.get(formSessionKey(userUid, interfaceType)).getObjectValue();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setSessionOpen(String userUid, UserInterfaceType interfaceType) {
        try {
            Cache cache = cacheManager.getCache("user_session");
            cache.put(new Element(formSessionKey(userUid, interfaceType), true));
        } catch (Exception e) {
            log.error("Error, could not put cache element!! ... " + e.toString());
        }
    }

    private String formSessionKey(String userUid, UserInterfaceType interfaceType) {
        return String.join(userUid, "+", interfaceType.toString());
    }

}
