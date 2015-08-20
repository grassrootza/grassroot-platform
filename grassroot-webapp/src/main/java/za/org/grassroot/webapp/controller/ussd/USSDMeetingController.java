package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDMeetingController extends USSDController {

    /**
     * The meeting organizer menus
     * To do: Decide whether to use the menu abstraction now built into this, or not
     * To do: Various forms of validation and checking throughout
     */

    @RequestMapping(value = USSD_BASE + MTG_MENUS + START_KEY)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            String promptMessage = "Okay, we'll set up a meeting. Please enter the phone numbers of the people to invite." +
                    " You can enter multiple numbers separated by a space or comma.";
            return new Request(promptMessage, freeText("mtg/group"));
        } else {
            String promptMessage = "Do you want to call a meeting of an existing group, or create a new one?";
            return new Request(promptMessage, userGroupMenu(sessionUser, "mtg/group", true));
        }
    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + "group")
    @ResponseBody
    public Request saveNumbers(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="request", required=false) String userResponse,
                               @RequestParam(value="groupId", required=false) Long groupId) throws URISyntaxException {

        String returnMessage;

        User sessionUser = new User();
        Group groupToMessage = new Group();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (groupId != null) {
            if (groupId == 0) {
                return new Request("Okay. We'll create a new group for this meeting. Please enter the numbers for it.",
                        freeText("mtg/group"));
            } else {
                groupToMessage = groupManager.loadGroup(groupId);
                returnMessage = "Okay, please enter the date for the meeting.";
            }
        } else {
            groupToMessage = groupManager.createNewGroup(sessionUser, userResponse);
            returnMessage = "Okay, we just created a group with those numbers. What day do you want the meeting?";
        }
        return new Request(returnMessage, freeText("mtg/time?groupId=" + groupToMessage.getId()));
    }

    // todo: instead of handing along the groupId, date, time, etc., create an event and hand over its ID
    // todo: create some default options for the next 3 days, for date
    // todo: clean up the flow and logic between these menus (they are getting a little confusing, even to me ...)

    @RequestMapping(value = USSD_BASE + MTG_MENUS + "date")
    @ResponseBody
    public Request getDate(@RequestParam(value="msisdn", required=true) String inputNumber,
                           @RequestParam(value="groupId", required=false) Long groupId) throws URISyntaxException {

        HashMap<String, String> menuValues = new HashMap<>();

        menuValues.put("returnMessage", "Okay. What day do you want the meeting?");
        menuValues.put("url", "mtg/time?groupId=" + groupId);

        // String returnMessage = "Okay. What day do you want the meeting?";
        return new Request(menuValues.get("returnMessage"), freeText("mtg/time?groupId=" + groupId));

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + "time")
    @ResponseBody
    public Request getTime(@RequestParam(value="msisdn", required=true) String inputNumber,
                           @RequestParam(value="groupId", required=false) Long groupId,
                           @RequestParam(value="request", required=true) String meetingDate) throws URISyntaxException {

        String returnMessage = "Okay. What time?";
        return new Request(returnMessage, freeText("mtg/place?groupId=" + groupId + "&date=" + meetingDate));

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + "place")
    @ResponseBody
    public Request getPlace(@RequestParam(value="msisdn", required=true) String inputNumber,
                            @RequestParam(value="groupId", required=false) Long groupId,
                            @RequestParam(value="date", required=true) String meetingDate,
                            @RequestParam(value="request", required=true) String meetingTime) throws URISyntaxException {

        // todo: add a lookup of group default places

        String returnMessage = "Done. What place?";
        return new Request(returnMessage, freeText("mtg/message?groupId=" + groupId + "&date=" + meetingDate + "&time=" + meetingTime));

    }

    @RequestMapping(value = USSD_BASE + MTG_MENUS + "message")
    @ResponseBody
    public Request sendMessage(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) Long groupId,
                               @RequestParam(value="date", required=true) String meetingDate,
                               @RequestParam(value="time", required=true) String meetingTime,
                               @RequestParam(value="request", required=true) String meetingPlace) throws URISyntaxException {

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
        return new Request(returnMessage, new ArrayList<Option>());
    }
}
