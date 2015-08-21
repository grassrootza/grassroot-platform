package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
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
     * The meeting organizer menus
     * todo: Various forms of validation and checking throughout
     * todo: Think of a way to pull together the common method set up stuff (try to load user, get next key)
     * todo: Make the prompts also follow the sequence somehow (via a map of some sort, likely)
     */

    private static final String keyGroup = "group", keyDate = "date", keyTime = "time", keyPlace = "place", keySend = "send";

    private static final List<String> menuSequence = Arrays.asList(START_KEY, keyGroup, keyTime, keyPlace, keySend);

    private String nextMenuKey(String currentMenuKey) {
        return menuSequence.get(menuSequence.indexOf(currentMenuKey) + 1);
    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + START_KEY)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu thisMenu = new USSDMenu("");
        String keyNext = nextMenuKey(START_KEY);

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            thisMenu.setFreeText(true);
            thisMenu.setPromptMessage("Okay, we'll set up a meeting. Please enter the phone numbers of the people to invite." +
                                              " You can enter multiple numbers separated by a space or comma.");
            thisMenu.setNextURI(MTG_MENUS + keyNext);
        } else {
            String promptMessage = "Do you want to call a meeting of an existing group, or create a new one?";
            thisMenu = userGroupMenu(sessionUser, promptMessage, MTG_MENUS + keyNext, true);
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + keyGroup)
    @ResponseBody
    public Request saveNumbers(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=TEXT_PARAM, required=false) String userResponse,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId) throws URISyntaxException {

        String returnMessage;
        String keyNext = nextMenuKey(keyGroup);

        User sessionUser = new User();
        Group groupToMessage = new Group();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        if (groupId != null) {
            if (groupId == 0) {
                thisMenu.setPromptMessage("Okay. We'll create a new group for this meeting. Please enter the numbers for it.");
                thisMenu.setNextURI(MTG_MENUS + keyGroup);
            } else {
                groupToMessage = groupManager.loadGroup(groupId);
                thisMenu.setPromptMessage("Okay, please enter the date for the meeting.");
                thisMenu.setNextURI(MTG_MENUS + keyNext + GROUPID_URL + groupToMessage.getId());
            }
        } else {
            groupToMessage = groupManager.createNewGroup(sessionUser, userResponse);
            thisMenu.setPromptMessage("Okay, we just created a group with those numbers. What day do you want the meeting?");
            thisMenu.setNextURI(MTG_MENUS + keyNext + GROUPID_URL + groupToMessage.getId());
        }
        return menuBuilder(thisMenu);

    }

    // todo: instead of handing along the groupId, date, time, etc., create an event and hand over its ID
    // todo: create some default options for the next 3 days, for date
    // todo: clean up the flow and logic between these menus (they are getting a little confusing, even to me ...)

    @RequestMapping(value = USSD_BASE + MTG_MENUS + keyDate)
    @ResponseBody
    public Request getDate(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                           @RequestParam(value=GROUP_PARAM, required=false) Long groupId) throws URISyntaxException {

        // todo: make groupId not required and check for it in passing, in case we shuffle the sequence
        String keyNext = nextMenuKey(keyDate);
        USSDMenu thisMenu = new USSDMenu("Okay. What day do you want the meeting?", MTG_MENUS + keyNext + GROUPID_URL + groupId);
        return menuBuilder(thisMenu);

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + keyTime)
    @ResponseBody
    public Request getTime(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                           @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                           @RequestParam(value=TEXT_PARAM, required=true) String meetingDate) throws URISyntaxException {

        String keyNext = nextMenuKey(keyTime);
        return menuBuilder(new USSDMenu("Okay. What time?", MTG_MENUS + keyNext + GROUPID_URL + groupId + "&date=" + meetingDate));

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + keyPlace)
    @ResponseBody
    public Request getPlace(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                            @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                            @RequestParam(value="date", required=true) String meetingDate,
                            @RequestParam(value=TEXT_PARAM, required=true) String meetingTime) throws URISyntaxException {

        // todo: add a lookup of group default places

        String returnMessage = "Done. What place?";
        String keyNext = nextMenuKey(keyPlace);
        return menuBuilder(new USSDMenu(returnMessage, MTG_MENUS + keyNext + GROUPID_URL + groupId + "&date="
                + meetingDate + "&time=" + meetingTime));

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + keySend)
    @ResponseBody
    public Request sendMessage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value="date", required=true) String meetingDate,
                               @RequestParam(value="time", required=true) String meetingTime,
                               @RequestParam(value=TEXT_PARAM, required=true) String meetingPlace) throws URISyntaxException {

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful
        // todo: split up the URI into multiple if it gets >2k chars (will be an issue when have 20+ person groups)
        // todo: add shortcode for RSVP reply

        User userSending = new User();
        try { userSending = userManager.findByInputNumber(inputNumber); }
        catch (Exception e) { return noUserError; }

        Group groupToMessage = groupManager.loadGroup(groupId);
        List<User> usersToMessage = groupToMessage.getGroupMembers();

        String groupName = (groupToMessage.hasName()) ? ("of group, " + groupToMessage.getGroupName() + ", ") : "";

        String msgText = "From " + userSending.getName("") + ": Meeting called " + groupName + "on " + meetingDate
                + ", at time " + meetingTime + " and place " + meetingPlace;

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

        String returnMessage = "Done! We sent the message.";
        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit));
    }
}
