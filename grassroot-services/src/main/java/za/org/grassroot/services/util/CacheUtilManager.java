package za.org.grassroot.services.util;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    public List<SafetyEvent> getOutstandingSafetyEventResponseForUser(User user) {
        List<SafetyEvent> outstandingSafetyEvents = null;
        Cache cache = cacheManager.getCache("userSafetyEvents");
        String cacheKey = user.getUid();
        try{
            if(cache.isKeyInCache(cacheKey)) {
                outstandingSafetyEvents = (List<SafetyEvent>) cache.get(cacheKey).getObjectValue();
            }
        }
        catch (Exception e){
           log.info("Could not retrieve outstanding events for user {}", user.getPhoneNumber());
        }

        return outstandingSafetyEvents;
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
        catch (Exception e){
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
    public void putUserLanguage(String inputNumber, String language) {
        try {
            Cache cache = cacheManager.getCache("user_language");
            cache.put(new Element(inputNumber, language));
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
