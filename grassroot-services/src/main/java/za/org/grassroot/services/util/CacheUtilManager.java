package za.org.grassroot.services.util;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by aakilomar on 11/2/15.
 */
@Service @Slf4j
public class CacheUtilManager implements CacheUtilService {

    private final CacheManager cacheManager;

    @Autowired
    public CacheUtilManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void clearRsvpCacheForUser(String userUid) {
        try {
            Cache cache = cacheManager.getCache("userRSVP");
            cache.remove(userUid);
        } catch (Exception e) {
            log.error("FAILED to clear userRSVP..." + userUid + " error: " + e.toString());
        }

    }

    @Override
    public UserMinimalProjection checkCacheForUserMinimalInfo(String msisdn) {
        Cache cache = cacheManager.getCache("user_msisdn_minimal");
        if (cache != null && cache.isKeyInCache(msisdn)) {
            return (UserMinimalProjection) cache.get(msisdn).getObjectValue();
        } else {
            return null;
        }
    }

    @Override
    public void stashUserForMsisdn(String msisdn, UserMinimalProjection user) {
        Cache cache = cacheManager.getCache("user_msisdn_minimal");
        log.debug("Putting in cache, user: {}", user);
        cache.put(new Element(msisdn, user));
    }

    @Override
    public List<Event> getOutstandingResponseForUser(String userUid) {
        Cache cache = cacheManager.getCache("userRSVP");
        log.info("getOutstandingResponseForUser... anything in cache : {}", cache.isKeyInCache(userUid));
        try {
            return cache.isKeyInCache(userUid) ? (List<Event>) cache.get(userUid).getObjectValue() : null;
        } catch (NullPointerException|ClassCastException e) {
            return null;
        }
    }

    @Override
    public List<SafetyEvent> getOutstandingSafetyEventResponseForUser(User user) {
        Cache cache = cacheManager.getCache("userSafetyEvents");
        String cacheKey = user.getUid();
        try {
            return cache.isKeyInCache(cacheKey) ? (List<SafetyEvent>) cache.get(cacheKey).getObjectValue() : null;
        }
        catch (NullPointerException|ClassCastException e){
           log.info("Could not retrieve outstanding events for user {}", user.getPhoneNumber());
           return null;
        }
    }

    @Override
    public void putSafetyEventResponseForUser(User user, SafetyEvent safetyEvent){
        //Since we cant use some kind of log for safety events, We put the events here and hope
        // each gets at least one positive response within an hour of activation before cache is cleared,
        List<SafetyEvent> safetyEventsUserToRespondTo = null;
        Cache cache = cacheManager.getCache("userSafetyEvents");
        String cacheKey = user.getUid();
        try{
            safetyEventsUserToRespondTo =(List<SafetyEvent>) cache.get(cacheKey).getObjectValue();
        }
        catch (NullPointerException|ClassCastException e){
            log.info("No list of outstanding safety events to respond to, creating list for user {}", user.getPhoneNumber());
            safetyEventsUserToRespondTo = new ArrayList<>();
        }
        safetyEventsUserToRespondTo.add(safetyEvent);
        cache.put(new Element(cacheKey,safetyEventsUserToRespondTo));

    }

    @Override
    public void clearSafetyEventResponseForUser(User user, SafetyEvent safetyEvent){
        Cache cache = cacheManager.getCache("userSafetyEvents");
        String cacheKey = user.getUid();
        if (cache.get(cacheKey) != null) {
            //remove particular safety response and if empty thereafter remove key
            List<SafetyEvent> safetyEventsUserToRespondTo = (List<SafetyEvent>) cache.get(cacheKey).getObjectValue();
            log.info("safety event to respond to size {}", safetyEventsUserToRespondTo.size());
            safetyEventsUserToRespondTo.remove(safetyEvent);
            Iterator iter = safetyEventsUserToRespondTo.iterator();
            while (iter.hasNext()) {
                SafetyEvent event = (SafetyEvent) iter.next();
                if (safetyEvent.getUid().equals(event.getUid())) {
                    iter.remove();
                }
            }
            log.info("safety event to respond to size after clearing {}", safetyEventsUserToRespondTo.size());
            cache.put(new Element(cacheKey, safetyEventsUserToRespondTo));
            if (safetyEventsUserToRespondTo.isEmpty()) {
                cache.remove(cacheKey);
            }
        }
    }

    @Override
    public void putOutstandingResponseForUser(String userUid, List<Event> outstandingRSVPs) {
        try {
            Cache cache = cacheManager.getCache("userRSVP");
            cache.put(new Element(userUid,outstandingRSVPs));
        } catch (CacheException|NullPointerException e) {
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
        } catch (NullPointerException e) {
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
        } catch (NullPointerException e) {
            log.debug("Could not find a stored USSD menu for phone number ..." + phoneNumber);
            menuToReturn = null;
        }
        return menuToReturn;
    }

    @Override
    public void putUserLanguage(String inputNumber, String language) {
        Cache cache = cacheManager.getCache("user_language");
        cache.put(new Element(inputNumber, language));
    }

    @Override
    public void putJoinAttempt(String userUid, int attempt){
        Cache cache = cacheManager.getCache("user_join_group");
        cache.put(new Element(userUid,attempt));
    }

    @Override
    public int fetchJoinAttempts(String userUid){
        Cache cache = cacheManager.getCache("user_join_group");
        Element cacheElement = cache.get(userUid);
        if (cacheElement != null) {
            log.debug("found user in cache, returning what's stored");
            return (int) cache.get(userUid).getObjectValue();
        } else {
            log.debug("nothing in cache, returning 0");
            return 0;
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

    @Override
    public List<PublicActivityLog> getCachedPublicActivity(PublicActivityType activityType) {
        try {
            Cache cache = cacheManager.getCache("public_activity_logs");
            Element cachedElement = cache.get(activityType);
            return cachedElement == null ? null : (List<PublicActivityLog>) cache.get(activityType).getObjectValue();
        } catch (NullPointerException|ClassCastException e) {
            log.error("Error retrieving from cache: {}", e.getMessage());
            return null;
        }

    }

    @Override
    public void putCachedPublicActivity(PublicActivityType activityType, List<PublicActivityLog> activityLogs) {
        try {
            Cache cache = cacheManager.getCache("public_activity_logs");
            cache.put(new Element(activityType, activityLogs));
        } catch (CacheException e) {
            log.error("Error putting element in cache");
        }
    }

    private String formSessionKey(String userUid, UserInterfaceType interfaceType) {
        return String.join(userUid, "+", interfaceType.toString());
    }

}
