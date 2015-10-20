package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDMeetingController extends USSDController {

    @Autowired
    MessageSendingService messageService;

    @Autowired
    SmsSendingService smsService;

    /**
     * Meeting organizer menus
     * todo: Various forms of validation and checking throughout
     * todo: Think of a way to pull together the common method set up stuff (try to load user, get next key)
     * todo: Make the prompts also follow the sequence somehow (via a map of some sort, likely)
     * todo: Replace "meeting" as the event "name" with a meeting subject
     */

    private Logger log = LoggerFactory.getLogger(getClass());

    private static final String basePath = USSD_BASE + MTG_MENUS;

    private static final String newGroupMenu = "newgroup", groupHandlingMenu = "group", subjectMenu ="subject",
            timeMenu = "time", placeMenu = "place", confirmMenu = "confirm", send = "send";

    private static final String manageMeetingMenu = "manage", viewMeetingDetails = "details", changeMeetingDate = "changeDate",
            changeMeetingLocation = "changeLocation", cancelMeeting = "cancel", modifyMeeting = "modify";

    private static final List<String> menuSequence = Arrays.asList(START_KEY, subjectMenu, placeMenu, timeMenu, confirmMenu, send);

    private String nextMenu(String currentMenu) {
        return menuSequence.get(menuSequence.indexOf(currentMenu) + 1);
    }

    /*
    Opening menu. Check if the user has created meetings which are upcoming, and, if so, give options to view and
    manage those. If not, initialize an event and ask them to pick a group or create a new one.
     */
    @RequestMapping(value = basePath + START_KEY)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value="newMtg", required=false) boolean newMeeting) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.loadOrSaveUser(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu returnMenu;

        if (newMeeting || eventManager.getUpcomingEventsUserCreated(sessionUser).size() == 0) {
            Event meetingToCreate = eventManager.createEvent("", sessionUser); // initialize event to be filled out in subsequent menus
            meetingToCreate = eventManager.setEventNoReminder(meetingToCreate.getId()); // until we are confident with reminders
            returnMenu = askForGroup(sessionUser, meetingToCreate.getId(), nextMenu(START_KEY), groupHandlingMenu); // helper method
        } else {
            returnMenu = askForMeeting(sessionUser); // helper method; if user picks 'new meeting', comes back here, with newMeeting param true
        }

        return menuBuilder(returnMenu);
    }

    private USSDMenu askForGroup(User sessionUser, Long eventId, String keyNext, String keyCreate) throws URISyntaxException {

        USSDMenu groupMenu;

        if (sessionUser.getGroupsPartOf().isEmpty()) { // user is not part of any group, so ask them to start entering numbers

            groupMenu = firstGroupPrompt(keyCreate, eventId, sessionUser);

        } else {

            String prompt = getMessage(MTG_KEY, START_KEY, PROMPT + ".has-group", sessionUser);

            String existingGroupUri = MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + groupHandlingMenu;
            String newGroupUri = MTG_MENUS + newGroupMenu + EVENTID_URL + eventId;

            groupMenu = userGroupMenu(sessionUser, prompt, existingGroupUri, newGroupUri, GROUP_PARAM);

        }
        return groupMenu;
    }


    /*
    note: the next method will bring up events in groups that the user has unsubscribed from, since it doesn't go via the
    group menus. but the alternate, to do a group check on each event, is going to cause speed issues, and real
    world cases of user unsubscribing between calling an event and it happening are marginal. so, leaving it this way.
     */
    private USSDMenu askForMeeting(User sessionUser) {

        USSDMenu askMenu = new USSDMenu(getMessage(MTG_KEY, START_KEY, PROMPT + ".new-old", sessionUser));
        String newMeetingOption = getMessage(MTG_KEY, START_KEY, OPTION + "new", sessionUser);

        Integer enumLength = "X. ".length();
        Integer lastOptionBuffer = enumLength + newMeetingOption.length();

        List<Event> upcomingEvents = eventManager.getPaginatedEventsCreatedByUser(sessionUser, 0, 3);

        for (Event event : upcomingEvents) {
            Map<String, String> eventDescription = eventManager.getEventDescription(event);
            if (eventDescription.get("minimumData").equals("true")) {
                String menuLine = eventDescription.get("groupName") + ": " + eventDescription.get("dateTimeString");
                if (askMenu.getMenuCharLength() + enumLength + menuLine.length() + lastOptionBuffer < 160) {
                    askMenu.addMenuOption(MTG_MENUS + manageMeetingMenu + EVENTID_URL + event.getId(), menuLine);
                }
            }
        }

        askMenu.addMenuOption(MTG_MENUS + START_KEY + "?newMtg=true", newMeetingOption);

        return askMenu;
    }

    /*
    The event management menu -- can't have too much complexity, just giving an RSVP total, and allowing cancel
    and change the date & time or location (other changes have too high a clunky UI vs number of use case trade off)
     */
    @RequestMapping(value= basePath + manageMeetingMenu)
    @ResponseBody
    public Request meetingManage(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        Event meeting = eventManager.loadEvent(eventId);

        log.info("Inside the management menu, for event: " + meeting);

        // todo: check user's permissions on this group/event, once permissions structure in place and being used

        String eventSuffix = EVENTID_URL + meeting.getId();
        USSDMenu promptMenu = new USSDMenu(getMessage(MTG_KEY, manageMeetingMenu, PROMPT, sessionUser));

        promptMenu.addMenuOption(MTG_MENUS + viewMeetingDetails + eventSuffix, getMessage(MTG_KEY, viewMeetingDetails, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + changeMeetingDate + eventSuffix, getMessage(MTG_KEY, changeMeetingDate, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + changeMeetingLocation + eventSuffix, getMessage(MTG_KEY, changeMeetingLocation, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + cancelMeeting + eventSuffix, getMessage(MTG_KEY, cancelMeeting, "option", sessionUser));

        return menuBuilder(promptMenu);
    }

    @RequestMapping(value= basePath + viewMeetingDetails)
    @ResponseBody
    public Request meetingDetails(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        Event meeting = eventManager.loadEvent(eventId);

        String mtgDescription;

        Map<String, String> meetingDetails = eventManager.getEventDescription(meeting);

        if (meeting.isRsvpRequired()) {

            int answeredYes = eventManager.getListOfUsersThatRSVPYesForEvent(meeting).size(), answeredNo = eventManager.getListOfUsersThatRSVPNoForEvent(meeting).size();
            int noAnswer = eventManager.getNumberInvitees(meeting) - (answeredYes + answeredNo);

            String[] messageFields = new String[]{meetingDetails.get("groupName"), meetingDetails.get("location"), meetingDetails.get("dateTimeString"),
                    "" + eventManager.getNumberInvitees(meeting), "" + answeredYes, "" + answeredNo, "" + noAnswer};

            mtgDescription = getMessage(MTG_KEY, viewMeetingDetails, PROMPT + ".rsvp", messageFields, sessionUser);

        } else {

            String[] messageFields = new String[]{meetingDetails.get("groupName"), meetingDetails.get("location"), meetingDetails.get("dateTimeString"),
                                                    "" + eventManager.getNumberInvitees(meeting)};

            mtgDescription = getMessage(MTG_KEY, viewMeetingDetails, PROMPT, messageFields, sessionUser);

        }

        log.info("Meeting description: " + mtgDescription);

        USSDMenu promptMenu = new USSDMenu(mtgDescription);

        promptMenu.addMenuOption(MTG_MENUS + manageMeetingMenu + EVENTID_URL + eventId,
                                 getMessage(MTG_KEY, viewMeetingDetails, OPTION + "back", sessionUser)); // go back to 'manage meeting'
        promptMenu.addMenuOption(START_KEY, getMessage(START_KEY, sessionUser)); // go to GR home menu
        promptMenu.addMenuOption("exit", getMessage("exit.option", sessionUser)); // exit system

        return menuBuilder(promptMenu);

    }

    /*
    Major todo for these: add a 'confirm' screen
    todo: add logging, etc.
     */

    @RequestMapping(value= basePath + changeMeetingDate)
    public Request changeDate(@RequestParam(value=PHONE_PARAM) String inputNumber,
                              @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        // todo: check user permissions.

        User sessionUser = userManager.loadOrSaveUser(inputNumber, MTG_MENUS + changeMeetingDate + EVENTID_URL + eventId);

        String prompt = getMessage(MTG_KEY, changeMeetingDate, PROMPT, eventManager.getEventDescription(eventId).get("dateTimeString"), sessionUser);

        USSDMenu promptMenu = new USSDMenu(prompt);
        promptMenu.setFreeText(true);
        promptMenu.setNextURI(MTG_MENUS + modifyMeeting + EVENTID_URL + eventId + "&action=" + changeMeetingDate);

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value= basePath + changeMeetingLocation)
    public Request changeLocation(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, MTG_MENUS + changeMeetingLocation + EVENTID_URL + eventId);

        String prompt = getMessage(MTG_KEY, changeMeetingLocation, PROMPT, eventManager.getEventDescription(eventId).get("location"), sessionUser);

        USSDMenu promptMenu = new USSDMenu(prompt);
        promptMenu.setFreeText(true);
        promptMenu.setNextURI(MTG_MENUS + modifyMeeting + EVENTID_URL + eventId + "&action=" + changeMeetingLocation);

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value= basePath + cancelMeeting)
    public Request meetingCancel(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        USSDMenu promptMenu = new USSDMenu(getMessage(MTG_KEY, cancelMeeting, PROMPT, sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + modifyMeeting + EVENTID_URL + eventId + "&action=" + cancelMeeting, getMessage(OPTION + "yes", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + manageMeetingMenu + EVENTID_URL + eventId, getMessage(OPTION + "no", sessionUser));

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value= basePath + modifyMeeting)
    public Request modifyMeeting(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId,
                                 @RequestParam("action") String action, @RequestParam(TEXT_PARAM) String userInput) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, null); // reset interrupted slag

        String menuPrompt;
        log.info("Updating a meeting via USSD ... action parameter is " + action + " and user input is: " + userInput);

        switch (action) {

            case changeMeetingDate:
                updateEvent(eventId, timeMenu, userInput);
                menuPrompt = getMessage(MTG_KEY, changeMeetingDate, "done", sessionUser);
                break;

            case changeMeetingLocation:
                updateEvent(eventId, placeMenu, userInput);
                menuPrompt = getMessage(MTG_KEY, changeMeetingLocation, "done", sessionUser);
                break;

            case cancelMeeting:
                eventManager.cancelEvent(eventId);
                menuPrompt = getMessage(MTG_KEY, cancelMeeting, "done", sessionUser);
                break;

            default:
                menuPrompt = getMessage(MTG_KEY, modifyMeeting, "error", sessionUser);
                break;
        }

        return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(sessionUser)));

    }

    /**
     * Methods and menus for creating a new meeting follow after this
     */

    /*
    First, the group handling menus, the most complex of them. There are four cases for the user having arrived here:

        (1) the user had no groups before, and was asked to enter a set of numbers to create a group
        (2) the user had other groups, but selected "create new group" on the previous menu
        (3) the user has entered some numbers, and is being asked for more
        (4) the user was interrupted/timed out in the middle of entering numbers and is returning from start menu

    Cases (1) and (2) land the user at the first two methods. Then all cases lead to createGroup, which is split into
    different helper methods to deal with each of the cases

     */

    @RequestMapping(value = basePath + newGroupMenu)
    @ResponseBody
    public Request newGroup(@RequestParam(value=PHONE_PARAM, required = true) String inputNumber,
                            @RequestParam(value=EVENT_PARAM, required = true) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, MTG_MENUS + newGroupMenu + EVENTID_URL + eventId);
        return menuBuilder(firstGroupPrompt(groupHandlingMenu, eventId, sessionUser));

    }

    /* Start group creation by asking the user to enter a phone number  (case 1 or 2) */
    private USSDMenu firstGroupPrompt(String keyNext, Long eventId, User sessionUser) {

        USSDMenu groupMenu = new USSDMenu("");

        groupMenu.setFreeText(true);
        groupMenu.setPromptMessage(getMessage(MTG_KEY, START_KEY, PROMPT + ".new-group", sessionUser));
        groupMenu.setNextURI(MTG_MENUS + keyNext + EVENTID_URL + eventId);

        return groupMenu;

    }

    @RequestMapping(value = basePath + groupHandlingMenu)
    @ResponseBody
    public Request createGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=false) String userResponse,
                               @RequestParam(value="prior_input", required=false) String priorInput,
                               HttpServletRequest request) throws URISyntaxException {

        USSDMenu thisMenu;
        String thisUriBase = MTG_MENUS + groupHandlingMenu + EVENTID_URL + eventId; // so we can get back here if the user is interrupted

        // if priorInput exists, we have been interrupted, so use that as userInput, else use what is passed as 'request'
        String userInput = (priorInput != null) ? priorInput : userResponse;

        if (userInput.trim().equals("0")) { // user has signalled to stop entering numbers, and wants to continue, so check if we have a valid group, and act accordingly
            if (groupId != null) { // stop asking for numbers, set the event's group, and prompt for and pass whatever is next in the sequence
                thisMenu = groupCreationFinished(inputNumber, thisUriBase, groupId, eventId, priorInput != null);
            } else { // there were errors, so no group has been created, but user wants to stop ... need to insist on a number
                thisMenu = groupNoValidNumbersError(inputNumber, thisUriBase, eventId);
            }
        } else {
            /* user still in the middle of entering numbers, so process response,and create a new group or add to the one we're building */
            thisMenu = numberEntryHandling(inputNumber, userInput, eventId, groupId, thisUriBase);
        }
        log.info("In the guts of the meeting/group creation menu ... User return URL is: " + userManager.findByInputNumber(inputNumber).getLastUssdMenu());
        return menuBuilder(thisMenu);

    }

    /*
    The subsequent menus are more straightforward, each filling in a part of the event data structure
     */

    // NOTE: Whatever menu comes after the group selection _has_ to use "groupId" (GROUP_PARAM) instead of "request" (TEXT_PARAM),
    // because the menu response will overwrite the 'request' parameter in the returned URL, and we will get a fail on the group

    @RequestMapping(value = basePath + subjectMenu)
    @ResponseBody
    public Request getSubject(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                              @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                              @RequestParam(value=GROUP_PARAM, required=true) String passedValue,
                              @RequestParam(value="prior_input", required=false) String priorInput,
                              @RequestParam(value="revising", required=false) boolean revising) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, subjectMenu, passedValueKey, passedValue));
        updateEvent(eventId, passedValueKey, passedValue, priorInput != null);

        String promptMessage = getMessage(MTG_KEY, subjectMenu, PROMPT, sessionUser);
        String nextUrl = (!revising) ? nextUrl(subjectMenu, eventId) : confirmUrl(subjectMenu, priorInput, eventId);

        return menuBuilder(new USSDMenu(promptMessage, nextUrl));

    }

    /* Another helper function to compose next URL */
    private String nextUrl(String currentMenu, Long eventId) {
        return MTG_MENUS + nextMenu(currentMenu) + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + currentMenu;
    }

    private String confirmUrl(String currentMenu, String dateTimeString, Long eventId) {
        String dateString = encodeParamater(dateTimeString);
        return MTG_MENUS + confirmMenu + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + currentMenu + "&prior_input=" + dateString + "&revising=1";
    }

    @RequestMapping(value = basePath + placeMenu)
    @ResponseBody
    public Request getPlace(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                            @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                            @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                            @RequestParam(value=TEXT_PARAM, required=true) String passedValue,
                            @RequestParam(value="prior_input", required=false) String priorInput,
                            @RequestParam(value="revising", required=false) boolean revising) throws URISyntaxException {

        // todo: add error and exception handling

        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, placeMenu, passedValueKey, passedValue));
        updateEvent(eventId, passedValueKey, passedValue, priorInput != null);

        String promptMessage = getMessage(MTG_KEY, placeMenu, PROMPT, sessionUser);
        String nextUrl = (!revising) ? nextUrl(placeMenu, eventId) : confirmUrl(placeMenu, priorInput, eventId);

        return menuBuilder(new USSDMenu(promptMessage, nextUrl));
    }

    // don't need the revision flag as this is always the menu in front of confirm screen
    @RequestMapping(value = basePath + timeMenu)
    @ResponseBody
    public Request getTime(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                           @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                           @RequestParam(value=PASSED_FIELD, required=false) String passedValueKey,
                           @RequestParam(value=TEXT_PARAM, required=true) String passedValue,
                           @RequestParam(value="prior_input", required=false) String priorInput) throws URISyntaxException {

        String keyNext = nextMenu(timeMenu);
        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, timeMenu, passedValueKey, passedValue));
        Event meetingToCreate = updateEvent(eventId, passedValueKey, passedValue, priorInput != null);
        String promptMessage = getMessage(MTG_KEY, timeMenu, PROMPT, sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + timeMenu));

    }

    @RequestMapping(value = basePath + confirmMenu)
    @ResponseBody
    public Request confirmDetails(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                                  @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                                  @RequestParam(value=TEXT_PARAM, required=true) String passedValue,
                                  @RequestParam(value="prior_input", required=false) String priorInput,
                                  @RequestParam(value="revising", required=false) boolean revising) throws URISyntaxException {

        // show user the assembled confirmation string, _but_ truncate so we don't throw an error.
        // also note that we should not do any update to the event here, or it will trigger the send -- just assemble string
        // todo: refactor and optimize this so it doesn't use so many getters, etc. Could be quite slow if many users.

        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, confirmMenu, passedValueKey, passedValue));
        Event meeting = (revising) ? updateEvent(eventId, passedValueKey, passedValue) : eventManager.loadEvent(eventId);

        USSDMenu thisMenu = new USSDMenu();

        String userInput = (priorInput == null) ? passedValue : priorInput;
        LocalDateTime parsedDate = DateTimeUtil.parseDateTime(userInput);

        String dateString = parsedDate.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"));
        String dateTimeParam = encodeParamater(dateString);

        String[] confirmFields = new String[]{ dateString, meeting.getName(), meeting.getEventLocation() };
        String confirmPrompt = getMessage(MTG_KEY, confirmMenu, PROMPT, confirmFields, sessionUser);

        // based on user feedback, give options to return to any prior screen, then back here.

        thisMenu.setPromptMessage(confirmPrompt);
        thisMenu.addMenuOption(MTG_MENUS + send + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + timeMenu + "&confirmed_time=" + dateTimeParam,
                               getMessage(MTG_KEY + "." + confirmMenu + "." + OPTION + "yes", sessionUser));
        thisMenu.addMenuOption(composeBackUri(eventId, timeMenu, dateTimeParam), composeBackMessage(sessionUser, timeMenu));
        thisMenu.addMenuOption(composeBackUri(eventId, placeMenu, dateTimeParam), composeBackMessage(sessionUser, placeMenu));
        thisMenu.addMenuOption(composeBackUri(eventId, subjectMenu, dateTimeParam) + "&groupId=" + meeting.getAppliesToGroup().getId(),
                               composeBackMessage(sessionUser, subjectMenu));

        if (!checkMenuLength(thisMenu, false)) {
            Integer charsToTrim = thisMenu.getMenuCharLength() - 159; // adding a character, for safety
            String revisedPrompt = confirmPrompt.substring(0, confirmPrompt.length() - charsToTrim);
            thisMenu.setPromptMessage(revisedPrompt);
        }

        return menuBuilder(thisMenu);

    }

    /*
    Helper method for composing "back" urls
     */

    private String composeBackUri(Long eventId, String backMenu, String dateTimeString) {
        return MTG_MENUS + backMenu + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + backMenu + "&revising=1" + "&prior_input=" + dateTimeString;
    }

    private String composeBackMessage(User user, String backMenu) {
        return getMessage(MTG_KEY + "." + confirmMenu + "." + OPTION + backMenu, user);
    }

    /*
    Finally, do the last update, assemble a text message and send it out -- most of this needs to move to the messaging layer
     */

    @RequestMapping(value = basePath + send)
    @ResponseBody
    public Request sendMessage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value="confirmed_time", required=true) String confirmedTime) throws URISyntaxException {

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful

        User sessionUser;
        try { sessionUser = userManager.loadOrSaveUser(inputNumber, null); } // so, 'menu to come back to' returns null
        catch (Exception e) { return noUserError; }

        // todo: use responses (from integration or from elsewhere, to display errors if numbers wrong

        Event meetingToSend = updateEvent(eventId, timeMenu, confirmedTime);

        return menuBuilder(new USSDMenu(getMessage(MTG_KEY, send, PROMPT, sessionUser), optionsHomeExit(sessionUser)));
    }

    /*
     * Auxiliary functions to help with passing parameters around, to allow for flexibility in menu order
     * Possibly move to the higher level controller class
    */

    private Event updateEvent(Long eventId, String lastMenuKey, String passedValue) {

        Event eventToReturn;
        switch(lastMenuKey) {
            case subjectMenu:
                eventToReturn = eventManager.setSubject(eventId, passedValue);
                break;
            case timeMenu:
                // todo: make sure we confirm date/time that's parsed, and/or set it null, so the message still sends
                // eventToReturn = eventManager.setDateTimeString(eventId, passedValue);
                eventToReturn = eventManager.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(passedValue)));
                break;
            case placeMenu:
                eventToReturn = eventManager.setLocation(eventId, passedValue);
                break;
            case groupHandlingMenu:
                eventToReturn = eventManager.setGroup(eventId, Long.parseLong(passedValue));
                break;
            default:
                eventToReturn = eventManager.loadEvent(eventId);
        }
        return eventToReturn;
    }

    private Event updateEvent(Long eventId, String lastMenuKey, String passedValue, boolean wasInterrupted) {

        // before doing anything, check if we have been passed the menu option from the 'you were interrupted' start prompt
        // and, if so, don't do anything, just return the event as it stands

        if (wasInterrupted) {
            return eventManager.loadEvent(eventId);
        } else {
            return updateEvent(eventId, lastMenuKey, passedValue);
        }

    }

    /*
    Helper methods for group creation, most of them self-explanatory (relatively)
     */

    private USSDMenu numberEntryHandling(String inputNumber, String userInput, Long eventId, Long groupId, String thisUriBase) {

        USSDMenu returnMenu;

        Map<String, List<String>> splitPhoneNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        String priorInputEncoded = encodeParamater(userInput);

        if (groupId == null) {// this is the first handling of numbers, so create a new group with the valid ones (if any), and return
            returnMenu = numberHandlingForNewGroup(inputNumber, splitPhoneNumbers.get(VALID), splitPhoneNumbers.get(ERROR), eventId,
                                                   thisUriBase, priorInputEncoded);
        } else { // adding more numbers to an existing group, so add the valid ones, and prompt for more or done
            returnMenu = numberHandlingForExistingGroup(inputNumber, splitPhoneNumbers.get(VALID), splitPhoneNumbers.get(ERROR), eventId,
                                                        groupId, thisUriBase, priorInputEncoded);
        }

        return returnMenu;
    }

    private USSDMenu numberHandlingForNewGroup(String inputNumber, List<String> validNumbers, List<String> errorNumbers, Long eventId,
                                               String thisUriBase, String priorInputEncoded) {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&prior_input=" + priorInputEncoded);

        String returnUri;

        if (!validNumbers.isEmpty()) { // create a new group and put its Id into the parameters
            Group createdGroup = groupManager.createNewGroup(sessionUser, validNumbers);
            returnUri = MTG_MENUS + groupHandlingMenu + EVENTID_URL + eventId + "&groupId=" + createdGroup.getId();
        } else { // avoid creating detritus groups if no valid numbers & user hangs up
            returnUri = MTG_MENUS + groupHandlingMenu + EVENTID_URL + eventId;
        }

        // notify user if any numbers need re-entering, and ask for more or finished
        return numberEntryPrompt(returnUri, MTG_KEY, sessionUser, true, errorNumbers);

    }

    private USSDMenu numberHandlingForExistingGroup(String inputNumber, List<String> validNumbers, List<String> errorNumbers, Long eventId,
                                                    Long groupId, String thisUriBase, String priorInputEncoded) {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + GROUP_PARAM + "=" + groupId + "&" +
                "prior_input=" + priorInputEncoded);
        groupManager.addNumbersToGroup(groupId, validNumbers);
        String returnUri = MTG_MENUS + groupHandlingMenu + EVENTID_URL + eventId + "&groupId=" + groupId;
        return numberEntryPrompt(returnUri, MTG_KEY, sessionUser, false, errorNumbers);

    }

    private USSDMenu groupCreationFinished(String inputNumber, String thisUriBase, Long groupId, Long eventId, boolean interrupted) {

        USSDMenu returnMenu = new USSDMenu(true);
        String keyNext = nextMenu(START_KEY); // so we know which menu follows once done with the group creation

        User sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + GROUP_PARAM + "=" + groupId + "&prior_input=0");
        if (!interrupted) { updateEvent(eventId, groupHandlingMenu, "" + groupId); }

        returnMenu.setPromptMessage(getMessage(MTG_KEY, keyNext, PROMPT, sessionUser));
        returnMenu.setNextURI(MTG_MENUS + nextMenu(keyNext) + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyNext);

        return returnMenu;
    }

    private USSDMenu groupNoValidNumbersError(String inputNumber, String thisUriBase, Long eventId) {

        // user wanted to finish but hasn't given us any valid numbers, so need to flag that
        // alternate approach may be to provide option of returning to the group picking menu

        USSDMenu returnMenu = new USSDMenu(true);
        User sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + TEXT_PARAM + "=0");
        returnMenu.setPromptMessage(getMessage(MTG_KEY, groupHandlingMenu, PROMPT + ".no-group", sessionUser));
        returnMenu.setNextURI(MTG_MENUS + groupHandlingMenu + EVENTID_URL + eventId);

        return  returnMenu;

    }


    public USSDMenu numberEntryPrompt(String returnUri, String sectionKey, User sessionUser, boolean newGroup,
                                      List<String> errorNumbers) {

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        String promptKey = (newGroup) ? "created" : "added";

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(sectionKey, groupHandlingMenu, PROMPT + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(sectionKey, groupHandlingMenu, PROMPT_ERROR, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(returnUri);
        return thisMenu;

    }

    /*
    Helper method to assemble the URI to save against the user, in case they get interrupted
     */

    private String assembleThisUri(Long eventId, String thisKey, String passedValueKey, String passedValue) {
        return (MTG_MENUS + thisKey + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + passedValueKey);
    }

}
