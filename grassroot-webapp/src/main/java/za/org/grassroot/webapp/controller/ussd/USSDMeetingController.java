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
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDMeetingController extends USSDController {

    /**
     * Meeting organizer menus
     * todo: Various forms of validation and checking throughout
     * todo: Think of a way to pull together the common method set up stuff (try to load user, get next key)
     */

    @Autowired
    private USSDEventUtil eventUtil;

    private Logger log = LoggerFactory.getLogger(getClass());
    private static final String path = homePath + meetingMenus;
    private static final USSDSection thisSection = USSDSection.MEETINGS;

    private static final String
            newGroupMenu = "newgroup",
            groupHandlingMenu = "group",
            subjectMenu ="subject",
            timeMenu = "time",
            placeMenu = "place",
            confirmMenu = "confirm",
            timeOnly = "time_only",
            dateOnly = "date_only",
            send = "send";

    private static final String
            manageMeetingMenu = "manage",
            viewMeetingDetails = "details",
            changeDateAndTime = "changeDateTime",
            changeMeetingLocation = "changeLocation",
            cancelMeeting = "cancel",
            modifyConfirm = "modify";

    private static final List<String> menuSequence =
            Arrays.asList(startMenu, subjectMenu, placeMenu, timeMenu, confirmMenu, send);

    private String nextMenu(String currentMenu) {
        return menuSequence.get(menuSequence.indexOf(currentMenu) + 1);
    }

    // for stubbing with Mockito ...
    public void setEventUtil(USSDEventUtil eventUtil) { this.eventUtil = eventUtil; }

    /*
    Opening menu. Check if the user has created meetings which are upcoming, and, if so, give options to view and
    manage those. If not, initialize an event and ask them to pick a group or create a new one.
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                              @RequestParam(value="newMtg", required=false) boolean newMeeting) throws URISyntaxException {

        Long startTime = System.currentTimeMillis();
        log.info("Timing a core menu ... meeting start menu entering ...");
        User user = userManager.findByInputNumber(inputNumber);
        Long endTime = System.currentTimeMillis();
        log.info("Timing a core menu ... meeting start menu to retrieve user... " + (endTime - startTime) + " msecs");

        startTime = System.currentTimeMillis();
        USSDMenu returnMenu;

        // todo: replace with call to countFutureEvents plus permission filter
        if (newMeeting || eventManager.getUpcomingEventsUserCreated(user).size() == 0) {
            returnMenu = ussdGroupUtil.askForGroupAllowCreateNew(user, USSDSection.MEETINGS, nextMenu(startMenu), newGroupMenu, null);
        } else {
            returnMenu = eventUtil.askForMeeting(user, startMenu, manageMeetingMenu, startMenu + "?newMtg=1");
        }
        endTime = System.currentTimeMillis();

        log.info(String.format("Timing meeting start menu, construcing menu took ... took %d msecs", endTime - startTime));
        return menuBuilder(returnMenu);
    }

    /**
     *  SECTION: Methods and menus for creating a new meeting follow after this

     First, the group handling menus, if the user chose to create a new group, or had no groups, they will go
     through the first two menus, which handle number entry and group creation; if they chose an existing group, they
     go to the subject input menu. We create the event entity once the group has been completed/selected.
     */

    @RequestMapping(value = path + newGroupMenu)
    @ResponseBody
    public Request newGroup(@RequestParam(value= phoneNumber, required = true) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, meetingMenus + newGroupMenu);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(user, thisSection, groupHandlingMenu));
    }

    /*
     The most complex handler in this class, as we have four cases, depending on two independent variables:
     a) whether the user has signalled to continue adding numbers or to stop (userResponse.equals('0'))
     b) whether we have a valid group yet or not (groupId is not null)
     the complexity for continuing to add numbers is in the Util class; for stopping, we have to handle here as it
     is specific to this section of menus. Needing to deal with interruptions adds a final layers of complexity.
     Note: if user input is '0' with a valid group, then we mimic the set subject menu and create an event
    */

    @RequestMapping(value = path + groupHandlingMenu)
    @ResponseBody
    public Request createGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=false) Long groupId,
                               @RequestParam(value= eventIdParam, required=false) Long eventId,
                               @RequestParam(value= userInputParam, required=false) String userResponse,
                               @RequestParam(value= interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value= interruptedInput, required=false) String priorInput) throws URISyntaxException {

        USSDMenu thisMenu;

        // if priorInput exists, we have been interrupted, so use that as userInput, else use what is passed as 'request'
        String userInput = (priorInput != null) ? USSDUrlUtil.decodeParameter(priorInput) : userResponse;
        String includeGroup = (groupId != null) ? groupIdUrlSuffix + groupId : ""; // there is no case where eventId is not null but groupId is
        String includeEvent = (eventId != null) ? ("&" + eventIdParam + "=" + eventId) : "";

        String urlToSave = USSDUrlUtil.saveMenuUrlWithInput(thisSection, groupHandlingMenu, includeGroup + includeEvent, userInput);
        log.info("In group handling menu, have saved this URL: " + urlToSave);

        User user = userManager.findByInputNumber(inputNumber, urlToSave);

        if (!userInput.trim().equals("0")) {
            thisMenu = new USSDMenu(true);
            if (groupId == null) {
                Long newGroupId = ussdGroupUtil.addNumbersToNewGroup(user, USSDSection.MEETINGS, thisMenu, userInput, groupHandlingMenu);
                userManager.setLastUssdMenu(user, USSDUrlUtil.
                        saveMenuUrlWithInput(thisSection, groupHandlingMenu, groupIdUrlSuffix + newGroupId, userInput));
            } else {
                thisMenu = ussdGroupUtil.addNumbersToExistingGroup(user, groupId, USSDSection.MEETINGS, userInput, groupHandlingMenu);
            }
        } else {
            thisMenu = new USSDMenu(true);
            if (groupId == null) {
                thisMenu.setPromptMessage(getMessage(thisSection, groupHandlingMenu, promptKey + ".no-group", user));
                thisMenu.setNextURI(meetingMenus + groupHandlingMenu);
            } else {
                Long eventIdToPass = eventId;
                if (eventId == null) {
                    eventIdToPass = eventManager.createMeeting(user, groupId).getId();
                    userManager.setLastUssdMenu(user, USSDUrlUtil.saveMenuUrlWithInput(thisSection, groupHandlingMenu,
                                                                                       includeGroup + "&eventId=" + eventIdToPass, userInput));
                }
                thisMenu.setPromptMessage(getMessage(thisSection, nextMenu(startMenu), promptKey, user));
                thisMenu.setNextURI(meetingMenus + nextMenu(nextMenu(startMenu)) + eventIdUrlSuffix + eventIdToPass
                                            + "&" + previousMenu + "=" + nextMenu(startMenu));
            }
        }

        return menuBuilder(thisMenu);

    }

    /*
    The subsequent menus are more straightforward, each filling in a part of the event data structure
     */

    // NOTE: Whatever menu comes after the group selection _has_ to use "groupId" (groupIdParam) instead of "request" (userInputParam),
    // because the menu response will overwrite the 'request' parameter in the returned URL, and we will get a fail on the group
    // note: this is also where we create the event entity, as after this we need to keep and store

    @RequestMapping(value = path + subjectMenu)
    @ResponseBody
    public Request getSubject(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                              @RequestParam(value= eventIdParam, required=false) Long eventId,
                              @RequestParam(value= groupIdParam, required=false) Long groupId,
                              @RequestParam(value= interruptedFlag, required=false) boolean interrupted,
                              @RequestParam(value="revising", required=false) boolean revising) throws URISyntaxException {

        if (!interrupted && !revising) eventId = eventManager.createMeeting(inputNumber, groupId).getId();
        User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(subjectMenu, eventId, revising));
        String promptMessage = getMessage(thisSection, subjectMenu, promptKey, sessionUser);
        String nextUrl = (!revising) ? nextUrl(subjectMenu, eventId) : confirmUrl(subjectMenu, eventId);
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));
    }

    @RequestMapping(value = path + placeMenu)
    @ResponseBody
    public Request getPlace(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                            @RequestParam(value= eventIdParam, required=true) Long eventId,
                            @RequestParam(value= previousMenu, required=false) String passedValueKey,
                            @RequestParam(value= userInputParam, required=true) String passedValue,
                            @RequestParam(value="interrupted", required=false) boolean interrupted,
                            @RequestParam(value="revising", required=false) boolean revising) throws URISyntaxException {

        // todo: add error and exception handling

        User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(placeMenu, eventId, revising));
        if (!interrupted) eventUtil.updateEvent(eventId, passedValueKey, passedValue);
        String promptMessage = getMessage(thisSection, placeMenu, promptKey, sessionUser);
        String nextUrl = (!revising) ? nextUrl(placeMenu, eventId) : confirmUrl(placeMenu, eventId);
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));
    }

    @RequestMapping(value = path + timeMenu)
    @ResponseBody
    public Request getTime(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                           @RequestParam(value= eventIdParam, required=true) Long eventId,
                           @RequestParam(value= previousMenu, required=false) String passedValueKey,
                           @RequestParam(value= userInputParam, required=true) String passedValue,
                           @RequestParam(value="interrupted", required=false) boolean interrupted) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeMenu, eventId, false));
        if (!interrupted) eventUtil.updateEvent(eventId, passedValueKey, passedValue);
        String promptMessage = getMessage(thisSection, timeMenu, promptKey, sessionUser);
        String nextUrl = meetingMenus + nextMenu(timeMenu) + eventIdUrlSuffix + eventId + "&" + previousMenu + "=" + timeMenu;
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));

    }

    @RequestMapping(value = path + timeOnly)
    @ResponseBody
    public Request changeTimeOnly(@RequestParam(value= phoneNumber, required = true) String inputNumber,
                                  @RequestParam(value= eventIdParam, required = true) Long eventId,
                                  @RequestParam(value= previousMenu, required= false) String priorMenu,
                                  @RequestParam(value="next_menu", required = false) String nextMenu,
                                  @RequestParam(value="load_string", required = false) boolean loadString) throws URISyntaxException {

        if (nextMenu == null) nextMenu = priorMenu;
        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeOnly, eventId, false) + "?next_menu=" + nextMenu);

        String currentlySetTime = (!loadString) ?
                eventManager.loadEvent(eventId).getEventStartDateTime().toLocalDateTime().format(DateTimeUtil.preferredTimeFormat) :
                eventManager.getDateTimeFromString(eventId).format(DateTimeUtil.preferredTimeFormat);

        String passingField = "&" + ((nextMenu.equals(confirmMenu)) ? previousMenu : "action") + "=" + timeOnly;
        String loadFlag = (loadString) ? "&load_string=1" : "";

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "time." + promptKey, currentlySetTime, user));
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(nextMenu, eventId) + passingField + loadFlag);

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + dateOnly)
    @ResponseBody
    public Request changeDateOnly(@RequestParam(value= phoneNumber, required = true) String inputNumber,
                                  @RequestParam(value= eventIdParam, required = true) Long eventId,
                                  @RequestParam(value= previousMenu, required = false) String priorMenu,
                                  @RequestParam(value="next_menu", required = false) String nextMenu,
                                  @RequestParam(value="load_string", required = false) boolean loadString) throws URISyntaxException {

        if (nextMenu == null) nextMenu = priorMenu;
        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(dateOnly, eventId, false) + "?next_menu=" + nextMenu);

        String currentDate = (!loadString) ?
                eventManager.loadEvent(eventId).getEventStartDateTime().toLocalDateTime().format(DateTimeUtil.preferredDateFormat) :
                eventManager.getDateTimeFromString(eventId).format(DateTimeUtil.preferredDateFormat);

        // modify & confirm screens name params differently & during modification we have to pass along that we are using the string
        String passingField = "&" + ((nextMenu.equals(confirmMenu)) ? previousMenu : "action") + "=" + dateOnly;
        String loadFlag = (loadString) ? "&load_string=1" : "";

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "date." + promptKey, currentDate, user));
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(nextMenu, eventId) + passingField + loadFlag);

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + confirmMenu)
    @ResponseBody
    public Request confirmDetails(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                  @RequestParam(value= eventIdParam, required=true) Long eventId,
                                  @RequestParam(value= previousMenu, required=true) String priorMenuName,
                                  @RequestParam(value= userInputParam, required=true) String passedValue,
                                  @RequestParam(value="interrupted", required=false) boolean interrupted) throws URISyntaxException {

        // todo: refactor and optimize this so it doesn't use so many getters, etc. Could be quite slow if many users.

        User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(confirmMenu, eventId, false));

        // make sure we don't send out the invites before the user has confirmed
        // todo: handle error if date parser finds nothing (e.g., user made a mistake and entered "J" or similar)
        // todo optimize db calls in this, think may be doing an unnecessary call

        USSDMenu thisMenu = new USSDMenu();
        Event meeting = (!interrupted) ? eventUtil.updateEventAndBlockSend(eventId, priorMenuName, passedValue) :
                eventManager.loadEvent(eventId);

        // todo: decide whether to keep this here with getter or to move into services logic
        String dateString = meeting.getEventStartDateTime().toLocalDateTime().format(dateTimeFormat);
        String[] confirmFields = new String[]{ dateString, meeting.getName(), meeting.getEventLocation() };
        String confirmPrompt = getMessage(thisSection, confirmMenu, promptKey, confirmFields, sessionUser);

        // based on user feedback, give options to return to any prior screen, then back here.

        thisMenu.setPromptMessage(confirmPrompt);
        thisMenu.addMenuOption(meetingMenus + send + eventIdUrlSuffix + eventId,
                               getMessage(thisSection + "." + confirmMenu + "." + optionsKey + "yes", sessionUser));
        thisMenu.addMenuOption(composeBackUri(eventId, timeOnly), composeBackMessage(sessionUser, "time"));
        thisMenu.addMenuOption(composeBackUri(eventId, dateOnly), composeBackMessage(sessionUser, "date"));
        thisMenu.addMenuOption(composeBackUri(eventId, placeMenu), composeBackMessage(sessionUser, placeMenu));
        thisMenu.addMenuOption(composeBackUri(eventId, subjectMenu) + "&groupId=" + meeting.getAppliesToGroup().getId(),
                               composeBackMessage(sessionUser, subjectMenu));

        return menuBuilder(thisMenu);

    }

    @RequestMapping(value = path + send)
    @ResponseBody
    public Request sendMessage(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= eventIdParam, required=true) Long eventId) throws URISyntaxException {

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber, null); } // so, 'menu to come back to' returns null
        catch (Exception e) { return noUserError; }

        // todo: use responses (from integration or from elsewhere, to display errors if numbers wrong

        eventManager.removeSendBlock(eventId);
        return menuBuilder(new USSDMenu(getMessage(thisSection, send, promptKey, sessionUser), optionsHomeExit(sessionUser)));
    }

    /*
    SECTION: Meeting management menus follow after this
   The event management menu -- can't have too much complexity, just giving an RSVP total, and allowing cancel
   and change the date & time or location (other changes have too high a clunky UI vs number of use case trade off)
    */
    @RequestMapping(value= path + manageMeetingMenu)
    @ResponseBody
    public Request meetingManage(@RequestParam(value= phoneNumber) String inputNumber,
                                 @RequestParam(value= eventIdParam) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Event meeting = eventManager.loadEvent(eventId);

        log.info("Inside the management menu, for event: " + meeting);

        // todo: check user's permissions on this group/event, once permissions structure in place and being used
        String eventSuffix = eventIdUrlSuffix + eventId + "&next_menu=" + changeDateAndTime; // next_menu param only used by date/time
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, manageMeetingMenu, promptKey, sessionUser));

        promptMenu.addMenuOption(meetingMenus + viewMeetingDetails + eventSuffix, getMessage(thisSection, viewMeetingDetails, "option", sessionUser));
        promptMenu.addMenuOption(meetingMenus + timeOnly + eventSuffix, getMessage(thisSection, "change_" + timeOnly, "option", sessionUser));
        promptMenu.addMenuOption(meetingMenus + dateOnly + eventSuffix, getMessage(thisSection, "change_" + dateOnly, "option", sessionUser));
        promptMenu.addMenuOption(meetingMenus + changeMeetingLocation + eventSuffix, getMessage(thisSection, changeMeetingLocation, "option", sessionUser));
        promptMenu.addMenuOption(meetingMenus + cancelMeeting + eventSuffix, getMessage(thisSection, cancelMeeting, "option", sessionUser));

        return menuBuilder(promptMenu);
    }

    @RequestMapping(value= path + viewMeetingDetails)
    @ResponseBody
    public Request meetingDetails(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= eventIdParam) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Event meeting = eventManager.loadEvent(eventId);

        String mtgDescription;
        Map<String, String> meetingDetails = eventManager.getEventDescription(meeting);

        if (meeting.isRsvpRequired()) {
            // todo: use enums instead of literal strings for the map
            Map<String, Integer> rsvpResponses = eventManager.getMeetingRsvpTotals(meeting);
            int answeredYes = rsvpResponses.get("yes");
            int answeredNo = rsvpResponses.get("no");
            int noAnswer = rsvpResponses.get("no_answer");
            String[] messageFields = new String[]{meetingDetails.get("groupName"), meetingDetails.get("location"), meetingDetails.get("dateTimeString"),
                    "" + eventManager.getNumberInvitees(meeting), "" + answeredYes, "" + answeredNo, "" + noAnswer};
            mtgDescription = getMessage(thisSection, viewMeetingDetails, promptKey + ".rsvp", messageFields, sessionUser);
        } else {
            String[] messageFields = new String[]{meetingDetails.get("groupName"), meetingDetails.get("location"), meetingDetails.get("dateTimeString"),
                    "" + eventManager.getNumberInvitees(meeting)};
            mtgDescription = getMessage(thisSection, viewMeetingDetails, promptKey, messageFields, sessionUser);
        }

        log.info("Meeting description: " + mtgDescription);
        USSDMenu promptMenu = new USSDMenu(mtgDescription);
        promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + eventIdUrlSuffix + eventId,
                                 getMessage(thisSection, viewMeetingDetails, optionsKey + "back", sessionUser)); // go back to 'manage meeting'
        promptMenu.addMenuOption(startMenu, getMessage(startMenu, sessionUser)); // go to GR home menu
        promptMenu.addMenuOption("exit", getMessage("exit.option", sessionUser)); // exit system
        return menuBuilder(promptMenu);
    }

    /*
    todo: add logging, etc.
     */

    @RequestMapping(value= path + changeMeetingLocation)
    public Request changeLocation(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value = eventIdParam) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, meetingMenus + changeMeetingLocation + eventIdUrlSuffix + eventId);

        String prompt = getMessage(thisSection, changeMeetingLocation, promptKey, eventManager.loadEvent(eventId).getEventLocation(), sessionUser);

        USSDMenu promptMenu = new USSDMenu(prompt);
        promptMenu.setFreeText(true);
        promptMenu.setNextURI(meetingMenus + modifyConfirm + eventIdUrlSuffix + eventId + "&action=" + changeMeetingLocation);

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value= path + cancelMeeting)
    public Request meetingCancel(@RequestParam(value= phoneNumber) String inputNumber,
                                 @RequestParam(value = eventIdParam) Long eventId) throws URISyntaxException {

        // todo: make clear how many people will be notified, how many have said yes, etc etc

        User sessionUser = userManager.findByInputNumber(inputNumber); // not saving URL on this one

        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, cancelMeeting, promptKey, sessionUser));
        promptMenu.addMenuOption(meetingMenus + modifyConfirm + doSuffix + eventIdUrlSuffix + eventId + "&action=" + cancelMeeting,
                                 getMessage(optionsKey + "yes", sessionUser));
        promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + eventIdUrlSuffix + eventId,
                                 getMessage(optionsKey + "no", sessionUser));

        return menuBuilder(promptMenu);

    }

    /*
    This menu allows to change date and time one by one but not to have to send two reminders if changing both
    It needs a separate case in the updateEvent switch statement, or else we use the parser for no reason
     */
    @RequestMapping(value = path + changeDateAndTime)
    @ResponseBody
    public Request confirmDateTime(@RequestParam(value= phoneNumber, required = true) String inputNumber,
                                   @RequestParam(value= eventIdParam, required = true) Long eventId,
                                   @RequestParam(value= userInputParam, required = true) String userInput,
                                   @RequestParam(value="action", required = true) String action,
                                   @RequestParam(value= interruptedFlag, required = false) boolean interrupted,
                                   @RequestParam(value="load_string", required = false) boolean load_string) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveMtgMenuWithAction(changeDateAndTime, eventId, action));

        // todo: probably want to straighten out / harmonize use of Timestamp / ValueOf
        Timestamp modifyingTimestamp = (!load_string) ? eventManager.loadEvent(eventId).getEventStartDateTime() :
                Timestamp.valueOf(DateTimeUtil.parsePreformattedString(eventManager.loadEvent(eventId).getDateTimeString()));
        LocalDateTime newDateTime;
        String otherMenu;

        if (action.equals(timeOnly)) {
            String formattedTime = DateTimeUtil.reformatTimeInput(userInput);
            log.info("Modifying time ... Here is what we got back ... " + formattedTime);
            newDateTime = DateTimeUtil.changeTimestampTimes(modifyingTimestamp, formattedTime).toLocalDateTime();
            log.info("Modifying time ... The minutes on the modified stamp are ... " + newDateTime.getMinute());
            otherMenu = dateOnly;
        } else {
            String formattedDate = DateTimeUtil.reformatDateInput(userInput);
            newDateTime = DateTimeUtil.changeTimestampDates(modifyingTimestamp, formattedDate).toLocalDateTime();
            otherMenu = timeOnly;
        }

        String formattedDate = newDateTime.format(dateFormat);
        String formattedTime = newDateTime.format(timeFormat);
        String newDateTimeString = newDateTime.format(DateTimeUtil.preferredDateTimeFormat);

        log.info("Modifying time or date ... formatted string is ... " + newDateTimeString);

        if (!interrupted) eventManager.storeDateTimeString(eventId, newDateTimeString);

        String[] promptFields = (action.equals(timeOnly)) ? new String[] { formattedTime, formattedDate } :
                new String[] { formattedDate, formattedTime };

        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeDateAndTime, promptKey + "." + action, promptFields, user));

        String sendUrl = assembleModifySendUrl(eventId, changeDateAndTime, newDateTimeString);
        String otherUrl = mtgMenu(otherMenu, eventId) + "&next_menu=" + changeDateAndTime + "&load_string=1";
        String backUrl = mtgMenu(action, eventId) + "&next_menu=" + changeDateAndTime + ((load_string) ? "&load_string=1" : "");

        menu.addMenuOption(sendUrl, getMessage(thisSection, changeDateAndTime, optionsKey + "confirm", user));
        menu.addMenuOption(otherUrl, getMessage(thisSection, changeDateAndTime, optionsKey + otherMenu, user));
        menu.addMenuOption(backUrl, getMessage(thisSection, changeDateAndTime, optionsKey + "back", user));

        return menuBuilder(menu);

    }

    // note: changing date and time do not come through here, but through a separate menu
    @RequestMapping(value= path + modifyConfirm)
    public Request modifyMeeting(@RequestParam(value= phoneNumber) String inputNumber,
                                 @RequestParam(value= eventIdParam) Long eventId,
                                 @RequestParam("action") String action,
                                 @RequestParam(userInputParam) String userInput,
                                 @RequestParam(value = "prior_input", required=false) String priorInput) throws URISyntaxException {

        userInput = (priorInput == null) ? userInput : priorInput;
        User sessionUser = userManager.findByInputNumber(inputNumber, meetingMenus + modifyConfirm + eventIdUrlSuffix + eventId
                + "&action=" + action + "&prior_input=" + USSDUrlUtil.encodeParameter(userInput));

        USSDMenu menu;
        String sendUrl;
        String backUrl = meetingMenus + action + eventIdUrlSuffix + eventId + "&next_menu=" + modifyConfirm;

        log.info("Updating a meeting via USSD ... action parameter is " + action + " and user input is: " + userInput);

        switch (action) {
            case cancelMeeting:
                menu = new USSDMenu(getMessage(thisSection, cancelMeeting, "prompt", sessionUser));
                sendUrl = assembleModifySendUrl(eventId, action, "");
                break;
            case changeMeetingLocation:
                menu = new USSDMenu(getMessage(thisSection, modifyConfirm, changeMeetingLocation + "." + promptKey, userInput, sessionUser));
                sendUrl = assembleModifySendUrl(eventId, changeMeetingLocation, userInput);
                break;
            default:
                menu = new USSDMenu(getMessage(thisSection, modifyConfirm, "error", sessionUser));
                sendUrl = meetingMenus + manageMeetingMenu + eventIdUrlSuffix + eventId;
                break;
        }

        menu.addMenuOption(sendUrl, getMessage(optionsKey + "yes", sessionUser)); // todo: i18n
        menu.addMenuOption(backUrl, getMessage(optionsKey + "no", sessionUser));
        return menuBuilder(menu);

    }

    private String assembleModifySendUrl(Long eventId, String action, String value) {
        return mtgMenuWithAction(modifyConfirm + doSuffix, eventId, action) + "&value=" + encodeParameter(value);
    }

    @RequestMapping(value=path + modifyConfirm + doSuffix)
    public Request modifyMeetingSend(@RequestParam(value= phoneNumber) String inputNumber,
                                     @RequestParam(value= eventIdParam) Long eventId,
                                     @RequestParam(value= "action") String action,
                                     @RequestParam(value= "value", required = false) String confirmedValue) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        String menuPrompt = getMessage(thisSection, modifyConfirm, action + ".done", sessionUser);

        switch (action) {
            case changeDateAndTime:
                eventUtil.updateEvent(eventId, changeDateAndTime, confirmedValue);
                break;
            case changeMeetingLocation:
                eventUtil.updateEvent(eventId, placeMenu, confirmedValue);
                break;
            case cancelMeeting:
                eventManager.cancelEvent(eventId);
                break;
            default:
                eventManager.removeSendBlock(eventId);
                break;
        }

        return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(sessionUser)));
    }


    /*
    A couple of helper methods that are quite specific to flow & structure of this controller
    todo: consider abstracting & moving to USSDUrlUtils
     */

    private String composeBackUri(Long eventId, String backMenu) {
        return meetingMenus + backMenu + eventIdUrlSuffix + eventId + "&" + previousMenu + "=" + backMenu + "&revising=1"
                + "&next_menu=" + confirmMenu;
    }

    /* Another helper function to compose next URL */
    private String nextUrl(String currentMenu, Long eventId) {
        return meetingMenus + nextMenu(currentMenu) + eventIdUrlSuffix + eventId + "&" + previousMenu + "=" + currentMenu;
    }

    /* Helper method to compose URL for going to confirmation screen after revisions */
    private String confirmUrl(String currentMenu, Long eventId) {
        return meetingMenus + confirmMenu + eventIdUrlSuffix + eventId + "&" + previousMenu + "=" + currentMenu +
                "&revising=1";
    }

    private String composeBackMessage(User user, String backMenu) {
        return getMessage(thisSection + "." + confirmMenu + "." + optionsKey + backMenu, user);
    }

}
