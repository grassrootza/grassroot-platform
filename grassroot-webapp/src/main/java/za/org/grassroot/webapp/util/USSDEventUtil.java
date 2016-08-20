package za.org.grassroot.webapp.util;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.services.LearningManager;
import za.org.grassroot.integration.services.LearningService;
import za.org.grassroot.language.DateTimeParseFailure;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.EventRequestBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static za.org.grassroot.core.enums.UserInterfaceType.USSD;
import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.enums.USSDSection.MEETINGS;
import static za.org.grassroot.webapp.enums.USSDSection.VOTES;
import static za.org.grassroot.webapp.util.USSDUrlUtil.paginatedEventUrl;

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

    @Autowired
    private LearningService learningService;

    private static final String entityUidParameter =  "entityUid";
    private static final String entityUidFirstParam = "?" + entityUidParameter + "=";
    private static final String entityUidLaterParam = "&" + entityUidParameter + "=";

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
            ImmutableMap.of(MEETINGS, EventType.MEETING, VOTES, EventType.VOTE);

    private static final DateTimeFormatter mtgFormat = DateTimeFormatter.ofPattern("d MMM H:mm");

    public USSDMenu listUpcomingEvents(User user, USSDSection section, String prompt, String nextMenu,
                                       boolean includeNewOption, String newMenu, String newOption) {
        return listPaginatedEvents(user, section, prompt, nextMenu, includeNewOption, newMenu, newOption, true, 1, 0);
    }

    public USSDMenu listPriorEvents(User user, USSDSection section, String prompt, String nextUrl, boolean withGroup) {
        return listPaginatedEvents(user, section, prompt, nextUrl, false, null, null, withGroup, -1, 0);
    }

    public USSDMenu listPaginatedEvents(User user, USSDSection section, String prompt, String menuForExisting,
                                        boolean includeNewOption, String menuForNew, String optionTextForNew,
                                        boolean includeGroupName, int pastPresentBoth, int pageNumber) {

        Page<Event> events = eventManager.getEventsUserCanView(user, mapSectionType.get(section), pastPresentBoth, pageNumber, pageSize);
        USSDMenu menu = new USSDMenu(prompt);
        menu = addListOfEventsToMenu(menu, section, menuForExisting, events.getContent(), includeGroupName);
        if (events.hasNext())
            menu.addMenuOption(paginatedEventUrl(prompt, section, menuForExisting, menuForNew, optionTextForNew, pastPresentBoth,
                                                 includeGroupName, pageNumber + 1), "More");
        if (events.hasPrevious())
            menu.addMenuOption(paginatedEventUrl(prompt, section, menuForExisting, menuForNew, optionTextForNew, pastPresentBoth,
                                                 includeGroupName, pageNumber - 1), "Back");
        if (includeNewOption)
            menu.addMenuOption(section.toPath() + menuForNew, optionTextForNew);
         menu.addMenuOption("start", getMessage(section,"start","options.back",user));
        return menu;
    }

    private USSDMenu addListOfEventsToMenu(USSDMenu menu, USSDSection section, String nextMenuUrl, List<Event> events, boolean includeGroupName) {
        final String formedUrl = section.toPath() + nextMenuUrl + ((nextMenuUrl.contains("?")) ? entityUidLaterParam : entityUidFirstParam);
        for (Event event : events) {
            String suffix = (section.equals(MEETINGS)) ? mtgFormat.format(event.getEventDateTimeAtSAST()) : event.getName();
            String descriptor = (includeGroupName ? event.getAncestorGroup().getName("") + ": " : "Subject: ") + suffix;
            menu.addMenuOption(formedUrl + event.getUid(), checkAndTruncateMenuOption(descriptor));
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
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation:", USSD);
                eventRequestBroker.updateEventDateTime(userUid, eventUid, parseDateTime(userInput));
                break;

            case timeOnly:
                EventRequest eventReq = eventRequestBroker.load(eventUid);
                String reformattedTime = DateTimeUtil.reformatTimeInput(userInput);
                LocalDateTime newTimestamp = changeTimestampTimes(eventReq.getEventDateTimeAtSAST(), reformattedTime);
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation-time-only", USSD);
                eventRequestBroker.updateEventDateTime(userUid, eventUid, newTimestamp);
                break;

            case dateOnly:
                eventReq = eventRequestBroker.load(eventUid);
                String reformattedDate = DateTimeUtil.reformatDateInput(userInput);
                newTimestamp = changeTimestampDates(eventReq.getEventDateTimeAtSAST(), reformattedDate);
                log.info("This is what we got back ... " + getPreferredDateTimeFormat().format(newTimestamp));
                userLogger.recordUserInputtedDateTime(userUid, userInput, "meeting-creation-date-only", USSD);
                eventRequestBroker.updateEventDateTime(userUid, eventUid, newTimestamp);
            default:
                break;
        }
    }

    public static boolean validateTime(LocalDateTime timestamp) {
        // do after we update, in case user is changing one at a time
        return timestamp.isAfter(LocalDateTime.now());
    }

    public void updateExistingEvent(String userUid, String requestUid, String fieldChanged, String newValue) {
        log.info(String.format("Updating the changeRequest on a meeting ... changing %s to %s", fieldChanged, newValue));
        LocalDateTime oldTimestamp, newTimestamp;
        switch (fieldChanged) {
            case changeMeetingLocation:
                eventRequestBroker.updateMeetingLocation(userUid, requestUid, newValue);
                break;
            case newTime:
                oldTimestamp = eventRequestBroker.load(requestUid).getEventDateTimeAtSAST();
                String reformattedTime = DateTimeUtil.reformatTimeInput(newValue);
                newTimestamp = changeTimestampTimes(oldTimestamp, reformattedTime);
                userLogger.recordUserInputtedDateTime(userUid, newValue, "meeting-edit-time-only", USSD);
                eventRequestBroker.updateEventDateTime(userUid, requestUid, newTimestamp);
                break;
            case newDate:
                oldTimestamp = eventRequestBroker.load(requestUid).getEventDateTimeAtSAST();
                String reformattedDate = DateTimeUtil.reformatDateInput(newValue);
                newTimestamp = changeTimestampDates(oldTimestamp, reformattedDate);
                userLogger.recordUserInputtedDateTime(userUid, newValue, "meeting-edit-date-only", USSD);
                eventRequestBroker.updateEventDateTime(userUid, requestUid, newTimestamp);
                break;
        }
    }

    /**
     * Method that invokes the date time parser directly, with no attempt to map to a predefined format or regex. Should
     * likely move to a single class in language, once updated that module to Java 8 and Antlr 4
     * @param passedValue
     * @return LocalDateTime of most likely match; if no match, returns the current date time rounded up to next hour
     */
    public LocalDateTime parseDateTime(String passedValue) throws DateTimeParseFailure {

        LocalDateTime parsedDateTime;

        try {
            parsedDateTime = learningService.parse(passedValue);
            log.info("Date time processed: " + parsedDateTime.toString());
        } catch (Exception e) {
            throw new DateTimeParseFailure();
        }

        return parsedDateTime;
    }

    /**
     * Method that will take a string representing a date and update a timestamp, leaving the time unchanged. It will
     * first try to process the string in the preferred format; if it fails, it invokes the language processor
     * @param originalDateTime The original date and time
     * @param revisedDateString The string representing the new date
     * @return The revised timestamp, with the new date, but original time
     */
    private LocalDateTime changeTimestampDates(LocalDateTime originalDateTime, String revisedDateString) {
        Objects.requireNonNull(originalDateTime);
        Objects.requireNonNull(revisedDateString);

        LocalDate revisedDate;
        try {
            revisedDate = LocalDate.parse(revisedDateString, DateTimeUtil.getPreferredDateFormat());
        } catch (DateTimeParseException e) {
            revisedDate = LocalDate.from(parseDateTime(revisedDateString));
        }
        LocalDateTime newDateTime = LocalDateTime.of(revisedDate, originalDateTime.toLocalTime());
        return newDateTime;
    }

    /**
     * Method that will take a string representing a time and update an instant, leaving the date unchanged. It will
     * first try to process the string in the preferred time format ("HH:mm"); if it fails, it invokes the language processor
     * @param originalDateTime The original local date time
     * @param revisedTimeString The string representing the new time
     * @return The revised timestamp, with the new date, but original time
     */
    private LocalDateTime changeTimestampTimes(LocalDateTime originalDateTime, String revisedTimeString) {
        Objects.requireNonNull(originalDateTime);
        Objects.requireNonNull(revisedTimeString);

        LocalTime revisedTime;
        try {
            revisedTime = LocalTime.parse(revisedTimeString, DateTimeUtil.getPreferredTimeFormat());
        } catch (DateTimeParseException e) {
            revisedTime = LocalTime.from(parseDateTime(revisedTimeString));
        }
        LocalDateTime newDateTime = LocalDateTime.of(originalDateTime.toLocalDate(), revisedTime);
        return newDateTime;
    }

}
