package za.org.grassroot.webapp.util;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.AsyncUserLogger;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.EventRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static za.org.grassroot.core.enums.UserInterfaceType.USSD;
import static za.org.grassroot.core.util.DateTimeUtil.*;

/**
 * Created by luke on 2015/12/05.
 */
@Component
public class USSDEventUtil extends USSDUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDEventUtil.class);

    @Autowired
    private EventManagementService eventManager;

    @Autowired
    private EventRequestBroker eventRequestBroker;

    @Autowired
    private AsyncUserLogger userLogger;

    private static final String eventIdParameter = "eventId";
    private static final String entityUidParameter =  "entityUid";
    private static final String eventIdFirstParam = "?" + eventIdParameter + "=";
    private static final String eventIdLaterParam = "&" + eventIdParameter + "=";
    private static final String entityUidFirstParam = "?" + entityUidParameter + "=";

    private static final String subjectMenu = "subject",
            placeMenu = "place",
            dateTimeMenu = "time",
            timeOnly = "time_only",
            dateOnly = "date_only",
            newTime = "new_time",
            newDate = "new_date",
            changeMeetingLocation = "changeLocation";
    private static final int pageSize = 3;

    private static final Map<USSDSection, EventType> mapSectionType =
            ImmutableMap.of(USSDSection.MEETINGS, EventType.MEETING, USSDSection.VOTES, EventType.VOTE);

    private static final DateTimeFormatter mtgFormat = DateTimeFormatter.ofPattern("d MMM H:mm");

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
        final String newMeetingOption = getMessage(mtgSection, callingMenu, optionsKey + "new", sessionUser);

        final Integer enumLength = "X. ".length();
        final Integer lastOptionBuffer = enumLength + newMeetingOption.length();

        List<Event> upcomingEvents = eventManager.getPaginatedEventsCreatedByUser(sessionUser, 0, 3);
        log.info("Returned " + upcomingEvents.size() + " events as upcoming ...");

        for (Event event : upcomingEvents) {
            String menuLine = event.resolveGroup().getName("") + ": "
                    + mtgFormat.format(event.getEventStartDateTime().toLocalDateTime());
            log.info("Here is the description ..." + menuLine);
            if (askMenu.getMenuCharLength() + enumLength + menuLine.length() + lastOptionBuffer < 160)
                askMenu.addMenuOption(meetingMenus + existingUrl + entityUidFirstParam + event.getUid(), menuLine);
        }
        askMenu.addMenuOption(meetingMenus + newUrl, newMeetingOption);
        return askMenu;
    }


    public USSDMenu listUpcomingEvents(User user, USSDSection section, String prompt, String nextMenu) {
        // todo: page back and forward
        EventType eventType = mapSectionType.get(section);
        USSDMenu menu = new USSDMenu(prompt);
        Page<Event> events = eventManager.getEventsUserCanView(user, eventType, 1, 0, pageSize);
        return addListOfEventsToMenu(menu, section.toPath() + nextMenu, events.getContent(), false);
    }

    public USSDMenu listPriorEvents(User user, USSDSection section, String prompt, String nextUrl, boolean withGroup) {
        return listPaginatedEvents(user, section, prompt, nextUrl, withGroup, -1, 0);
    }

    public USSDMenu listPaginatedEvents(User user, USSDSection section, String prompt, String nextUrl,
                                        boolean includeGroupName, int pastPresentBoth, int pageNumber) {
        Page<Event> events = eventManager.getEventsUserCanView(user, mapSectionType.get(section), pastPresentBoth, pageNumber, pageSize);
        USSDMenu menu = new USSDMenu(prompt);
        menu = addListOfEventsToMenu(menu, nextUrl, events.getContent(), includeGroupName);
        if (events.hasNext())
            menu.addMenuOption(USSDUrlUtil.paginatedEventUrl(prompt, section, nextUrl, pastPresentBoth, includeGroupName, pageNumber + 1), "More");
        if (events.hasPrevious())
            menu.addMenuOption(USSDUrlUtil.paginatedEventUrl(prompt, section, nextUrl, pastPresentBoth, includeGroupName, pageNumber - 1), "Back");
        return menu;
    }

    private USSDMenu addListOfEventsToMenu(USSDMenu menu, String nextMenuUrl, List<Event> events, boolean includeGroupName) {
        final String formedUrl = nextMenuUrl + ((nextMenuUrl.contains("?")) ? eventIdLaterParam : eventIdFirstParam);
        for (Event event : events) {
            String descriptor = (includeGroupName ? event.resolveGroup().getName("") + ": " : "Subject: ") + event.getName();
            menu.addMenuOption(formedUrl + event.getId(), checkAndTruncateMenuOption(descriptor));
        }
        return menu;
    }

    public void updateEventRequest(String userUid, String eventUid, String priorMenu, String userInput) {
        switch(priorMenu) {
            case subjectMenu:
                eventRequestBroker.updateName(userUid, eventUid, userInput);
                break;
            case placeMenu:
                eventRequestBroker.updateMeetingLocation(userUid, eventUid, userInput);
                break;
            case dateTimeMenu:
                // todo: handle errors in processing better (i.e., call parseDateTime in menus, pass here already-processed)
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation:", USSD);
                eventRequestBroker.updateStartTimestamp(userUid, eventUid, Timestamp.valueOf(parseDateTime(userInput)));
                break;
            case timeOnly:
                EventRequest eventReq = eventRequestBroker.load(eventUid);
                String reformattedTime = DateTimeUtil.reformatTimeInput(userInput);
                Timestamp newTimestamp = changeTimestampTimes(eventReq.getEventStartDateTime(), reformattedTime);
                log.info("This is what we got back ... " + preferredDateTimeFormat.format(newTimestamp.toLocalDateTime()));
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation-time-only", USSD);
                eventRequestBroker.updateStartTimestamp(userUid, eventUid, newTimestamp);
                break;
            case dateOnly:
                eventReq = eventRequestBroker.load(eventUid);
                String reformattedDate = DateTimeUtil.reformatDateInput(userInput);
                newTimestamp = changeTimestampDates(eventReq.getEventStartDateTime(), reformattedDate);
                log.info("This is what we got back ... " + preferredDateTimeFormat.format(newTimestamp.toLocalDateTime()));
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation-date-only", USSD);
                eventRequestBroker.updateStartTimestamp(userUid, eventUid, newTimestamp);
            default:
                break;
        }
    }

    public void updateExistingEvent(String userUid, String requestUid, String fieldChanged, String newValue) {
        log.info(String.format("Updating the changeRequest on a meeting ... changing %s to %s", fieldChanged, newValue));
        Timestamp oldTimestamp, newTimestamp;
        switch (fieldChanged) {
            /*case subjectMenu:
                eventRequestBroker.updateName(userUid, requestUid, newValue);
                break;*/
            case changeMeetingLocation:
                eventRequestBroker.updateMeetingLocation(userUid, requestUid, newValue);
                break;
            case newTime:
                oldTimestamp = eventRequestBroker.load(requestUid).getEventStartDateTime();
                String reformattedTime = DateTimeUtil.reformatTimeInput(newValue);
                newTimestamp = changeTimestampTimes(oldTimestamp, reformattedTime);
                userLogger.recordUserInputtedDateTime(userUid, newValue, "meeting-edit-time-only", USSD);
                eventRequestBroker.updateStartTimestamp(userUid, requestUid, newTimestamp);
                break;
            case newDate:
                oldTimestamp = eventRequestBroker.load(requestUid).getEventStartDateTime();
                String reformattedDate = DateTimeUtil.reformatDateInput(newValue);
                newTimestamp = changeTimestampDates(oldTimestamp, reformattedDate);
                userLogger.recordUserInputtedDateTime(userUid, newValue, "meeting-edit-date-only", USSD);
                eventRequestBroker.updateStartTimestamp(userUid, requestUid, newTimestamp);
                break;
        }
    }

}
