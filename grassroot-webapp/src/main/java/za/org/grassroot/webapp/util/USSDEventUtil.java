package za.org.grassroot.webapp.util;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
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
    private static final int pageSize = 3;

    private static final Map<USSDSection, EventType> mapSectionType =
            ImmutableMap.of(USSDSection.MEETINGS, EventType.Meeting, USSDSection.VOTES, EventType.Vote);

    /*
    note: the next method will bring up events in groups that the user has unsubscribed from, since it doesn't go via the
    group menus. but the alternate, to do a group check on each event, is going to cause speed issues, and real
    world cases of user unsubscribing between calling an event and it happening are marginal. so, leaving it this way.
     */

    // todo: generalize to askForEvent so we can use this for votes, and paginate
    public USSDMenu askForMeeting(User sessionUser, String callingMenu, String existingUrl, String newUrl) {

        final USSDSection mtgSection = USSDSection.MEETINGS;
        final String meetingMenus = mtgSection.toPath();

        USSDMenu askMenu = new USSDMenu(getMessage(mtgSection, callingMenu, promptKey + ".new-old", sessionUser));
        String newMeetingOption = getMessage(mtgSection, callingMenu, optionsKey + "new", sessionUser);

        Integer enumLength = "X. ".length();
        Integer lastOptionBuffer = enumLength + newMeetingOption.length();

        List<Event> upcomingEvents = eventManager.getPaginatedEventsCreatedByUser(sessionUser, 0, 3);

        for (Event event : upcomingEvents) {
            // todo: need to reduce the number of DB calls here, a lot, including possibly superfluous calls to minimumDataAvailable
            Map<String, String> eventDescription = eventManager.getEventDescription(event);
            if (eventDescription.get("minimumData").equals("true")) {
                String menuLine = eventDescription.get("groupName") + ": " + eventDescription.get("dateTimeString");
                if (askMenu.getMenuCharLength() + enumLength + menuLine.length() + lastOptionBuffer < 160) {
                    askMenu.addMenuOption(meetingMenus + existingUrl + eventIdUrlEnding + event.getId(), menuLine);
                }
            }
        }
        askMenu.addMenuOption(meetingMenus + newUrl, newMeetingOption);
        return askMenu;
    }

    public USSDMenu listUpcomingEvents(User user, USSDSection section, String prompt, String nextUrl) {
        // todo: page back and forward
        // todo: a means to alter how event subject is described (e.g., pass in a key)
        EventType eventType = mapSectionType.get(section);
        USSDMenu menu = new USSDMenu(prompt);
        Page<Event> events = eventManager.getEventsUserCanView(user, eventType, 1, 0, pageSize);
        for (Event event: events) {
            menu.addMenuOption(nextUrl + eventIdUrlEnding + event.getId(), event.getName());
        }
        return menu;
    }

    public USSDMenu listPriorEvents(User user, USSDSection section, String prompt, String nextUrl) {
        EventType eventType = mapSectionType.get(section);
        USSDMenu menu = new USSDMenu(prompt);
        Page<Event> events = eventManager.getEventsUserCanView(user, eventType, -1, 0, pageSize);
        for (Event event : events)
            menu.addMenuOption(nextUrl + eventIdUrlEnding + event.getId(), event.getName());
        return menu;
    }

    public boolean userHasEventsToView(User user, USSDSection section, boolean upcomingOnly) {
        EventType eventType = mapSectionType.get(section);
        return eventManager.userHasEventsToView(user, eventType, upcomingOnly);
    }

    public Page<Event> askForEventsUserCanManage(User user, USSDSection section, boolean upcomingOnly) {
        EventType eventType = mapSectionType.get(section);
        return null;
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
