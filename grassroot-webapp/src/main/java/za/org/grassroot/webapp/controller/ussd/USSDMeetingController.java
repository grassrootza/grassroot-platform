package za.org.grassroot.webapp.controller.ussd;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
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
import za.org.grassroot.integration.domain.MessageProtocol;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final String keyNewGroup = "newgroup", keyGroup = "group", keySubject="subject",
            keyTime = "time", keyPlace = "place", keySend = "send";
    private static final String keyManage = "manage", keyMtgDetails = "details", keyChangeDate = "changeDate",
            keyChangeLocation = "changeLocation", keyCancel = "cancel", keyModify = "modify";
    private static final String mtgPath = USSD_BASE +MTG_MENUS;

    private static final List<String> menuSequence = Arrays.asList(START_KEY, keySubject, keyPlace, keyTime, keySend);

    private String nextMenuKey(String currentMenuKey) {
        return menuSequence.get(menuSequence.indexOf(currentMenuKey) + 1);
    }

    /*
    Opening menu. As of now, first prompt is to create a group.
     */
    @RequestMapping(value = mtgPath + START_KEY)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value="newMtg", required=false) boolean newMeeting) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.loadOrSaveUser(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        // first, check if the user has upcoming events, and, if so, prompt to manage / view

        if (newMeeting || eventManager.getUpcomingEventsUserCreated(sessionUser).size() == 0) {
            // initialize event to be filled out in subsequent menus
            Event meetingToCreate = eventManager.createEvent("", sessionUser);
            return menuBuilder(askForGroup(sessionUser, meetingToCreate.getId(), nextMenuKey(START_KEY), keyGroup));
        } else {
            return menuBuilder(askNewOld(sessionUser));
        }
    }

    private USSDMenu askForGroup(User sessionUser, Long eventId, String keyNext, String keyCreate) throws URISyntaxException {

        USSDMenu groupMenu;

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            groupMenu = firstGroupPrompt(keyCreate, eventId, sessionUser);
        } else {
            String promptMessage = getMessage(MTG_KEY, START_KEY, PROMPT + ".has-group", sessionUser);
            String existingGroupUri = MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyGroup;
            String newGroupUri = MTG_MENUS + keyNewGroup + EVENTID_URL + eventId;
            groupMenu = userGroupMenu(sessionUser, promptMessage, existingGroupUri, newGroupUri, GROUP_PARAM);
        }
        return groupMenu;
    }

    private USSDMenu askNewOld(User sessionUser) {

        // todo: paginate so there aren't too many, but think that will be a marginal problem for now

        USSDMenu askMenu = new USSDMenu("Manage an upcoming meeting, or call a new one?");
        List<Event> upcomingEvents = eventManager.getUpcomingEventsUserCreated(sessionUser);

        for (Event event : upcomingEvents) {
            Map<String, String> eventDescription = eventManager.getEventDescription(event);
            if (eventDescription.get("minimumData").equals("true")) {
                String menuLine = eventDescription.get("groupName") + ": " + eventDescription.get("eventSubject");
                askMenu.addMenuOption(MTG_MENUS + keyManage + EVENTID_URL + event.getId(), menuLine);
            }
        }

        askMenu.addMenuOption(MTG_MENUS + START_KEY + "?newMtg=true", "Call a new meeting");

        return askMenu;
    }

    /*
    The event management menu -- can't have too much complexity, just giving an RSVP total, and allowing cancel
    and change the date & time or location (other changes have too high a clunky UI vs number of use case trade off)
     */
    @RequestMapping(value=mtgPath + keyManage)
    @ResponseBody
    public Request meetingManage(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        Event meeting = eventManager.loadEvent(eventId);

        log.info("Inside the management menu, for event: " + meeting);

        // todo: check user's permissions on this group/event .. for now, just make sure it's the creating user
        String menuPrompt;
        if (meeting.isRsvpRequired()) {
            menuPrompt = getMessage(MTG_KEY, keyManage, PROMPT + ".rsvp", new String[]{"" + eventManager.getNumberInvitees(meeting),
                    "" + eventManager.getListOfUsersThatRSVPYesForEvent(meeting).size()}, sessionUser);
        } else {
            menuPrompt = getMessage(MTG_KEY, keyManage, PROMPT, "" + eventManager.getNumberInvitees(meeting), sessionUser);
        }

        log.info("Menu prompt will be: " + meeting);

        USSDMenu promptMenu = new USSDMenu(menuPrompt);
        promptMenu.addMenuOption(MTG_MENUS + keyMtgDetails, getMessage(MTG_KEY, keyMtgDetails, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + keyChangeDate, getMessage(MTG_KEY, keyChangeDate, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + keyChangeLocation, getMessage(MTG_KEY, keyChangeLocation, "option", sessionUser));
        promptMenu.addMenuOption(MTG_MENUS + keyCancel, getMessage(MTG_KEY, keyCancel, "option", sessionUser));

        return menuBuilder(promptMenu);
    }

    @RequestMapping(value=mtgPath + keyMtgDetails)
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
            mtgDescription = getMessage(MTG_KEY, keyMtgDetails, PROMPT + ".rsvp", messageFields, sessionUser);
        } else {
            String[] messageFields = new String[]{meetingDetails.get("groupName"), meetingDetails.get("location"), meetingDetails.get("dateTimeString"),
                                                    "" + eventManager.getNumberInvitees(meeting)};
            mtgDescription = getMessage(MTG_KEY, keyMtgDetails, PROMPT, messageFields, sessionUser);
        }

        USSDMenu promptMenu = new USSDMenu(mtgDescription);
        promptMenu.addMenuOption(MTG_MENUS + keyManage + EVENTID_URL + eventId, getMessage(MTG_KEY, keyMtgDetails, OPTION + "back", sessionUser));
        promptMenu.addMenuOption(START_KEY, getMessage(START_KEY, sessionUser));
        promptMenu.addMenuOption("exit", getMessage("exit.option", sessionUser));

        return menuBuilder(promptMenu);

    }

    /*
    Major todo for these: add a 'confirm' screen
    todo: add logging, etc.
     */

    @RequestMapping(value=mtgPath + keyChangeDate)
    public Request changeDate(@RequestParam(value=PHONE_PARAM) String inputNumber,
                              @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        // todo: check permissions; include this in the interrupted / resume sequences
        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String prompt = getMessage(MTG_KEY, keyChangeDate, PROMPT, eventManager.getEventDescription(eventId).get("dateTimeString"), sessionUser);

        USSDMenu promptMenu = new USSDMenu(prompt);
        promptMenu.setFreeText(true);
        promptMenu.setNextURI(MTG_MENUS + keyModify + EVENTID_URL + eventId + "&action=" + keyChangeDate);

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value=mtgPath + keyChangeLocation)
    public Request changeLocation(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String prompt = getMessage(MTG_KEY, keyChangeLocation, eventManager.getEventDescription(eventId).get("location"), sessionUser);

        USSDMenu promptMenu = new USSDMenu(prompt);
        promptMenu.setFreeText(true);
        promptMenu.setNextURI(MTG_MENUS + keyModify + EVENTID_URL + eventId + "&action=" + keyChangeLocation);
        return menuBuilder(promptMenu);

    }

    @RequestMapping(value=mtgPath + keyCancel)
    public Request meetingCancel(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String prompt = getMessage(MTG_KEY, keyCancel, PROMPT, sessionUser);
        USSDMenu promptMenu = new USSDMenu(prompt, optionsYesNo(sessionUser, keyModify + EVENTID_URL + eventId + "&action=" + keyCancel, "exit"));
        return menuBuilder(promptMenu);

    }

    @RequestMapping(value=mtgPath + keyModify)
    public Request modifyMeeting(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                 @RequestParam(value=EVENT_PARAM) Long eventId,
                                 @RequestParam("action") String action, @RequestParam(TEXT_PARAM) String userInput) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String menuPrompt;

        switch (action) {
            case keyChangeDate:
                updateEvent(eventId, keyTime, userInput);
                menuPrompt = getMessage(MTG_KEY, keyChangeDate, "done", sessionUser);
                break;
            case keyChangeLocation:
                updateEvent(eventId, keyPlace, userInput);
                menuPrompt = getMessage(MTG_KEY, keyChangeLocation, "done", sessionUser);
                break;
            case keyCancel:
                eventManager.cancelEvent(eventId);
                menuPrompt = getMessage(MTG_KEY, keyCancel, "done", sessionUser);
                break;
            default:
                menuPrompt = getMessage(MTG_KEY, keyModify, "error", sessionUser);
                break;
        }

        return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(sessionUser)));

    }

    /**
     * Menus to create and call a new meeting, including, if necessary, creating a new group for it
     */

    private USSDMenu firstGroupPrompt(String keyNext, Long eventId, User sessionUser) {

        USSDMenu groupMenu = new USSDMenu("");
        groupMenu.setFreeText(true);
        groupMenu.setPromptMessage(getMessage(MTG_KEY, START_KEY, PROMPT + ".new-group", sessionUser));
        groupMenu.setNextURI(MTG_MENUS + keyNext + EVENTID_URL + eventId);
        return groupMenu;

    }

    /*
    The group creation menu, the most complex of them. Since we only ever arrive here from askForGroup menu, we can
    name the parameters more naturally than the abstract/generic look up in the other menus.
    There are four cases for the user having arrived here:
        (1) the user had no groups before, and was asked to enter a set of numbers to create a group
        (2) the user had other groups, but selected "create new group" on the previous menu
        (3) the user has entered some numbers, and is being asked for more
        (4) the user was interrupted/timed out in the middle of entering numbers and is returning from start menu
     */

    @RequestMapping(value = mtgPath + keyNewGroup)
    @ResponseBody
    public Request newGroup(@RequestParam(value=PHONE_PARAM, required = true) String inputNumber,
                            @RequestParam(value=EVENT_PARAM, required = true) Long eventId) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, MTG_MENUS + keyNewGroup + EVENTID_URL + eventId);
        return menuBuilder(firstGroupPrompt(keyGroup, eventId, sessionUser));

    }

    @RequestMapping(value = mtgPath + keyGroup)
    @ResponseBody
    public Request createGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=false) String userResponse,
                               @RequestParam(value="prior_input", required=false) String priorInput,
                               HttpServletRequest request) throws URISyntaxException {

        User sessionUser;
        String thisUriBase = MTG_MENUS + keyGroup + EVENTID_URL + eventId;
        String keyNext = nextMenuKey(START_KEY);
        Event meetingToCreate = eventManager.loadEvent(eventId);

        // if 'request' parameter is '1', and priorInput exists, we have been interrupted, so just switch these strings
        if (userResponse.trim().equals("1") && priorInput != null) { userResponse = priorInput; }

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        if (userResponse.trim().equals("0")) {
            if (groupId != null) {
                // stop asking for numbers, set the event's group, and prompt for and pass whatever is next in the sequence
                sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + GROUP_PARAM + "=" + groupId + "&prior_input=0");
                updateEvent(eventId, keyGroup, "" + groupId);
                thisMenu.setPromptMessage(getMessage(MTG_KEY, keyNext, PROMPT, sessionUser));
                thisMenu.setNextURI(MTG_MENUS + nextMenuKey(keyNext) + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyNext);
            } else {
                // there were errors, so no group has been created, but user wants to stop ... need to insist on a number
                // alternate approach may be to provide option of returning to the group picking menu
                sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + TEXT_PARAM + "=0");
                thisMenu.setPromptMessage(getMessage(MTG_KEY, keyGroup, PROMPT + ".no-group", sessionUser));
                thisMenu.setNextURI(MTG_MENUS + keyGroup + EVENTID_URL + eventId);
            }
        } else {
            // process & validate the user's responses, and create a new group or add to the one we're building
            String priorInputEncoded;
            Map<String, List<String>> splitPhoneNumbers = PhoneNumberUtil.splitPhoneNumbers(userResponse);
            try { priorInputEncoded = URLEncoder.encode(userResponse, "UTF-8"); }
            catch (UnsupportedEncodingException e) { priorInputEncoded = userResponse; }
            if (groupId == null) {
                sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&prior_input=" + priorInputEncoded);
                String returnUri;
                if (splitPhoneNumbers.get(VALID).isEmpty()) { // avoid creating detritus groups if no valid numbers & user hangs up
                    returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId;
                } else {
                    Group createdGroup = groupManager.createNewGroup(sessionUser, splitPhoneNumbers.get(VALID));
                    returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId + "&groupId=" + createdGroup.getId();
                }
                thisMenu = numberEntryPrompt(returnUri, MTG_KEY, sessionUser, true, splitPhoneNumbers.get(ERROR));
            } else {
                sessionUser = userManager.loadOrSaveUser(inputNumber, thisUriBase + "&" + GROUP_PARAM + "=" + groupId + "&" +
                                                        "prior_input=" + priorInputEncoded);
                groupManager.addNumbersToGroup(groupId, splitPhoneNumbers.get(VALID));
                String returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId + "&groupId=" + groupId;
                thisMenu = numberEntryPrompt(returnUri, MTG_KEY, sessionUser, false, splitPhoneNumbers.get(ERROR));
            }
        }

        log.info("In the guts of the meeting/group creation menu ... User return URL is: " + sessionUser.getLastUssdMenu());
        return menuBuilder(thisMenu);

    }

    public USSDMenu numberEntryPrompt(String returnUri, String sectionKey, User sessionUser, boolean newGroup,
                                      List<String> errorNumbers) {

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        String promptKey = (newGroup) ? "created" : "added";

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(sectionKey, keyGroup, PROMPT + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(sectionKey, keyGroup, PROMPT_ERROR, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(returnUri);
        return thisMenu;

    }

    /*
    The subsequent menus are more straightforward, each filling in a part of the event data structure
    The auxiliary function at the end and the passing of the parameter name means we can shuffle these at will
    Not collapsing them into one function as may want to convert some from free text to a menu of options later
    Though even then, may be able to collapse them -- but then need to access which URL within method
     */

    // helper function to assemble the placeholder URLs from the menu; excludes 'text_param', since we get a '1' from the
    // interruption menu, and we don't want to create possible bugs through multiple parameters

    // NOTE: Whatever menu comes after the group selection _has_ to use GROUP_PARAM instead of TEXT_PARAM, because the
    // menu response will overwrite the 'request' parameter in the returned URL, and we will get a fail on the group

    private String assembleThisUri(Long eventId, String thisKey, String passedValueKey, String passedValue) {
        return (MTG_MENUS + thisKey + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + passedValueKey);
    }

    @RequestMapping(value = mtgPath + keySubject)
    @ResponseBody
    public Request getSubject(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                              @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                              @RequestParam(value=GROUP_PARAM, required=true) String passedValue) throws URISyntaxException {

        String keyNext = nextMenuKey(keySubject); // skipped for the moment, like keyDate
        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, keySubject, passedValueKey, passedValue));
        Event meetingToCreate = updateEvent(eventId, passedValueKey, passedValue);
        String promptMessage = getMessage(MTG_KEY, keySubject, PROMPT, sessionUser);

        USSDMenu thisMenu = new USSDMenu(promptMessage, MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keySubject);
        return menuBuilder(thisMenu);

    }

    @RequestMapping(value = mtgPath + keyTime)
    @ResponseBody
    public Request getTime(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                           @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                           @RequestParam(value=PASSED_FIELD, required=false) String passedValueKey,
                           @RequestParam(value=TEXT_PARAM, required=true) String passedValue) throws URISyntaxException {

        String keyNext = nextMenuKey(keyTime);
        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, keyTime, passedValueKey, passedValue));
        Event meetingToCreate = updateEvent(eventId, passedValueKey, passedValue);
        String promptMessage = getMessage(MTG_KEY, keyTime, PROMPT, sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyTime));

    }

    @RequestMapping(value = mtgPath + keyPlace)
    @ResponseBody
    public Request getPlace(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                            @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                            @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                            @RequestParam(value=TEXT_PARAM, required=true) String passedValue) throws URISyntaxException {

        // todo: add a lookup of group default places
        // todo: add error and exception handling

        String keyNext = nextMenuKey(keyPlace);
        User sessionUser = userManager.loadOrSaveUser(inputNumber, assembleThisUri(eventId, keyPlace, passedValueKey, passedValue));
        Event meetingToCreate = updateEvent(eventId, passedValueKey, passedValue);
        String promptMessage = getMessage(MTG_KEY, keyPlace, PROMPT, sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyPlace));
    }

    /*
    Finally, do the last update, assemble a text message and send it out -- most of this needs to move to the messaging layer
     */

    @RequestMapping(value = mtgPath + keySend)
    @ResponseBody
    public Request sendMessage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                               @RequestParam(value=TEXT_PARAM, required=true) String passedValue) throws URISyntaxException {

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful

        User sessionUser;
        try { sessionUser = userManager.loadOrSaveUser(inputNumber, null); } // so, 'menu to come back to' returns null
        catch (Exception e) { return noUserError; }

        // todo: use responses (from integration or from elsewhere, to display errors if numbers wrong

        Event meetingToSend = updateEvent(eventId, passedValueKey, passedValue);

        return menuBuilder(new USSDMenu(getMessage(MTG_KEY, keySend, PROMPT, sessionUser), optionsHomeExit(sessionUser)));
    }

    /*
     * Auxiliary functions to help with passing parameters around, to allow for flexibility in menu order
     * Possibly move to the higher level controller class
    */

    private Event updateEvent(Long eventId, String lastMenuKey, String passedValue) {

        // before doing anything, check if we have been passed the menu option from the 'you were interrupted' start prompt
        // and, if so, don't do anything, just return the event as it stands
        if (passedValue.equals("1")) { return eventManager.loadEvent(eventId); }

        Event eventToReturn;
        switch(lastMenuKey) {
            case keySubject:
                eventToReturn = eventManager.setSubject(eventId, passedValue);
                break;
            case keyTime:
                // todo: make sure we confirm date/time that's parsed, and/or set it null, so the message still sends
                eventToReturn = eventManager.setDateTimeString(eventId, passedValue);
                eventToReturn = eventManager.setEventTimestamp(eventId, Timestamp.valueOf(DateTimeUtil.parseDateTime(passedValue)));
                break;
            case keyPlace:
                eventToReturn = eventManager.setLocation(eventId, passedValue);
                break;
            case keyGroup:
                eventToReturn = eventManager.setGroup(eventId, Long.parseLong(passedValue));
                break;
            default:
                eventToReturn = eventManager.loadEvent(eventId);
        }
        return eventToReturn;
    }




}
