package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/12/05.
 */
@Component
public class USSDEventUtil extends USSDUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDEventUtil.class);

    @Autowired
    EventManagementService eventManager;

    private static final String eventIdParameter = "eventId";
    private static final String eventIdUrlEnding = "?" + eventIdParameter + "=";

    private static final String subjectMenu = "subject", placeMenu = "place", timeMenu = "time",
            timeOnly = "time_only", dateOnly = "date_only";

    // public USSDEventUtil(MessageSource messageSource) { super(messageSource); }

    /*
    note: the next method will bring up events in groups that the user has unsubscribed from, since it doesn't go via the
    group menus. but the alternate, to do a group check on each event, is going to cause speed issues, and real
    world cases of user unsubscribing between calling an event and it happening are marginal. so, leaving it this way.
     */

    // todo: generalize to askForEvent so we can use this for votes
    // todo: paginate
    public USSDMenu askForMeeting(User sessionUser, String existingUrl, String newUrl) {

        String mtgKey = USSDSection.MEETINGS.toKey();
        String meetingMenus = USSDSection.MEETINGS.toPath();

        USSDMenu askMenu = new USSDMenu(getMessage(mtgKey, newUrl, promptKey + ".new-old", sessionUser));
        String newMeetingOption = getMessage(mtgKey, newUrl, optionsKey + "new", sessionUser);

        Integer enumLength = "X. ".length();
        Integer lastOptionBuffer = enumLength + newMeetingOption.length();

        List<Event> upcomingEvents = eventManager.getPaginatedEventsCreatedByUser(sessionUser, 0, 3);

        for (Event event : upcomingEvents) {
            Map<String, String> eventDescription = eventManager.getEventDescription(event);
            if (eventDescription.get("minimumData").equals("true")) {
                String menuLine = eventDescription.get("groupName") + ": " + eventDescription.get("dateTimeString");
                if (askMenu.getMenuCharLength() + enumLength + menuLine.length() + lastOptionBuffer < 160) {
                    askMenu.addMenuOption(meetingMenus + existingUrl + eventIdUrlEnding + event.getId(), menuLine);
                }
            }
        }

        askMenu.addMenuOption(meetingMenus + newUrl + "?newMtg=true", newMeetingOption);

        return askMenu;
    }

    public Event updateEvent(Long eventId, String lastMenuKey, String passedValue) {

        Event eventToReturn;

        switch(lastMenuKey) {
            case subjectMenu:
                eventToReturn = eventManager.setSubject(eventId, passedValue);
                break;
            case placeMenu:
                eventToReturn = eventManager.setLocation(eventId, passedValue);
                break;
            case timeMenu:
                eventToReturn = eventManager.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(passedValue)));
                break;
            case timeOnly:
                String formattedTime = DateTimeUtil.reformatTimeInput(passedValue);
                log.info("This is what we got back ... " + formattedTime);
                eventToReturn = eventManager.changeMeetingTime(eventId, formattedTime);
                break;
            case dateOnly:
                String formattedDate = DateTimeUtil.reformatDateInput(passedValue);
                log.info("This is what we got back ... " + formattedDate);
                eventToReturn = eventManager.changeMeetingDate(eventId, formattedDate);
                break;
            default:
                eventToReturn = eventManager.loadEvent(eventId);
        }
        return eventToReturn;
    }

    // helper method to set a send block before updating (todo: consolidate/minimize DB calls)
    public Event updateEventAndBlockSend(Long eventId, String lastMenuKey, String passedValue) {
        eventManager.setSendBlock(eventId);
        return updateEvent(eventId, lastMenuKey, passedValue);
    }


}
