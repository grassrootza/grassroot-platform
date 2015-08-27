package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

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
     * todo: Make the prompts also follow the sequence somehow (via a map of some sort, likely)
     * todo: Replace "meeting" as the event "name" with a meeting subject
     */

    private static final String keyGroup = "group", keySubject="subject", keyDate = "date", keyTime = "time", keyPlace = "place", keySend = "send";
    private static final String mtgPath = USSD_BASE +MTG_MENUS;

    private static final List<String> menuSequence = Arrays.asList(START_KEY, keySubject, keyTime, keyPlace, keySend);

    private String nextMenuKey(String currentMenuKey) {
        return menuSequence.get(menuSequence.indexOf(currentMenuKey) + 1);
    }

    /*
    Opening menu. As of now, first prompt is to create a group.
     */
    @RequestMapping(value = mtgPath + START_KEY)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        // initialize event to be filled out in subsequent menus
        Event meetingToCreate = eventManager.createEvent("", sessionUser);

        return menuBuilder(askForGroup(sessionUser, meetingToCreate.getId(), nextMenuKey(START_KEY), keyGroup));
    }

    private USSDMenu askForGroup(User sessionUser, Long eventId, String keyNext, String keyCreate) throws URISyntaxException {

        USSDMenu groupMenu = new USSDMenu("");

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            // User is not part of any groups, so just ask for them to enter some numbers
            groupMenu.setFreeText(true);
            groupMenu.setPromptMessage(getMessage(MTG_KEY, START_KEY, PROMPT + ".new-group", sessionUser));
            groupMenu.setNextURI(MTG_MENUS + keyCreate + EVENTID_URL + eventId);

        } else {

            // User is part of some groups, so list them, with the option to create a new one
            String promptMessage = getMessage(MTG_KEY, START_KEY, PROMPT + ".has-group", sessionUser);
            String existingGroupUri = MTG_MENUS + keyNext + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyGroup;
            String newGroupUri = MTG_MENUS + keyCreate + EVENTID_URL + eventId;
            groupMenu = userGroupMenu(sessionUser, promptMessage, existingGroupUri, newGroupUri, TEXT_PARAM);

        }

        return groupMenu;
    }

    /*
    The group creation menu, the most complex of them. Since we only ever arrive here from askForGroup menu, we can
    name the parameters more naturally than the abstract/generic look up in the other menus.
    There are three cases for the user having arrived here:
        (1) the user had no groups before, and was asked to enter a set of numbers to create a group
        (2) the user had other groups, but selected "create new group" on the previous menu
        (3) the user has entered some numbers, and is being asked for more
     */

    @RequestMapping(value = mtgPath + keyGroup)
    @ResponseBody
    public Request createGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=false) String userResponse) throws URISyntaxException {

        User sessionUser;
        String keyNext = nextMenuKey(START_KEY);
        Event meetingToCreate = eventManager.loadEvent(eventId);

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        if (userResponse.trim().equals("0")) {
            // stop asking for numbers, set the event's group, and prompt for and pass whatever is next in the sequence
            meetingToCreate = updateEvent(eventId, keyGroup, "" + groupId);
            thisMenu.setPromptMessage(getMessage(MTG_KEY, keyNext, PROMPT, sessionUser));
            thisMenu.setNextURI(MTG_MENUS + nextMenuKey(keyNext) + EVENTID_URL + eventId + "&" + PASSED_FIELD + "=" + keyNext);
        } else {
            // process & validate the user's responses, and create a new group or add to the one we're building

            Map<String, List<String>> splitPhoneNumbers = splitPhoneNumbers(userResponse, " ");
            if (groupId == null) {
                String returnUri;
                if (splitPhoneNumbers.get(VALID).isEmpty()) { // avoid creating detritus groups if no valid numbers & user hangs up
                    returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId;
                } else {
                    Group createdGroup = groupManager.createNewGroup(sessionUser, splitPhoneNumbers.get(VALID));
                    returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId + "&groupId=" + createdGroup.getId();
                }
                thisMenu = numberEntryPrompt(returnUri, MTG_KEY, sessionUser, true, splitPhoneNumbers.get(ERROR));
            } else {
                groupManager.addNumbersToGroup(groupId, splitPhoneNumbers.get(VALID));
                String returnUri = MTG_MENUS + keyGroup + EVENTID_URL + eventId + "&groupId=" + groupId;
                thisMenu = numberEntryPrompt(returnUri, MTG_KEY, sessionUser, false, splitPhoneNumbers.get(ERROR));
            }
        }

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

    // todo change GROUP_PARAM to TEXT_PARAM in the parameter mapping once fix the upstream method

    @RequestMapping(value = mtgPath + keySubject)
    @ResponseBody
    public Request getSubject(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                              @RequestParam(value=PASSED_FIELD, required=true) String passedValueKey,
                              @RequestParam(value=TEXT_PARAM, required = true) String passedValue) throws URISyntaxException {

        String keyNext = nextMenuKey(keySubject); // skipped for the moment, like keyDate
        User sessionUser = userManager.findByInputNumber(inputNumber);
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
        User sessionUser = userManager.findByInputNumber(inputNumber);
        Event meetingToCreate = updateEvent(eventId, passedValueKey, passedValue);

        /*
        Inserting logic to parse whatever came back and, if it can be parsed, set the timestamp accordingly.
         */

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
        User sessionUser = userManager.findByInputNumber(inputNumber);
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
        // todo: split up the URI into multiple if it gets >2k chars (will be an issue when have 20+ person groups)
        // todo: add shortcode for RSVP reply

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (Exception e) { return noUserError; }

        Event meetingToSend = updateEvent(eventId, passedValueKey, passedValue);
        Group groupToMessage = meetingToSend.getAppliesToGroup();
        List<User> usersToMessage = groupToMessage.getGroupMembers();

        String groupName = (groupToMessage.hasName()) ? ("of group, " + groupToMessage.getGroupName() + ", ") : "";

        String msgText = "From " + sessionUser.getName("") + ": Meeting called " + groupName + "about " + meetingToSend.getName()
                + ", at time " + meetingToSend.getTimeOfEvent() + " and place " + meetingToSend.getEventLocation();

        System.out.println(msgText);

        RestTemplate sendGroupSMS = new RestTemplate();
        UriComponentsBuilder sendMsgURI = UriComponentsBuilder.newInstance().scheme("https").host(smsHost);
        sendMsgURI.path("send/").queryParam("username", smsUsername).queryParam("password", smsPassword);

        for (int i = 1; i <= usersToMessage.size(); i++) {
            sendMsgURI.queryParam("number" + i, usersToMessage.get(i-1).getPhoneNumber());
            sendMsgURI.queryParam("message" + i, msgText);
        }

        String messageResult = sendGroupSMS.getForObject(sendMsgURI.build().toUri(), String.class);
        System.out.println(messageResult);

        return menuBuilder(new USSDMenu(getMessage(MTG_KEY, keySend, PROMPT, sessionUser), optionsHomeExit(sessionUser)));
    }

    /*
     * Auxiliary functions to help with passing parameters around, to allow for flexibility in menu order
     * Possibly move to the higher level controller class
    */

    private Event updateEvent(Long eventId, String lastMenuKey, String passedValue) {
        Event eventToReturn;
        switch(lastMenuKey) {
            case keySubject:
                eventToReturn = eventManager.setSubject(eventId, passedValue);
                break;
            case keyDate:
                eventToReturn = eventManager.setDay(eventId, passedValue);
                break;
            case keyTime:
                eventToReturn = eventManager.setTime(eventId, passedValue);
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
