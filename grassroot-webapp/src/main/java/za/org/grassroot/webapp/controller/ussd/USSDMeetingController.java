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
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.ArrayList;
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
     * To do: Carve these out into their own controller class to make everything more readable
     * To do: Use a folder URL structure for the different menu trees
     * To do: Various forms of validation and checking throughout
     */

    @RequestMapping(value = "/ussd/mtg")
    @ResponseBody
    public Request meetingOrg(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            String promptMessage = "Okay, we'll set up a meeting. Please enter the phone numbers of the people to invite." +
                    " You can enter multiple numbers separated by a space or comma.";
            return new Request(promptMessage, freeText("mtg2"));
        } else {
            String promptMessage = "Do you want to call a meeting of an existing group, or create a new one?";
            return new Request(promptMessage, userGroupMenu(sessionUser, "mtg2", true));
        }
    }

    @RequestMapping(value = "/ussd/mtg2")
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
                        freeText("mtg2"));
            } else {
                groupToMessage = groupManager.loadGroup(groupId);
                returnMessage = "Okay, please enter the message to send to the group.";
            }
        } else {
            groupToMessage = groupManager.createNewGroup(sessionUser, userResponse);
            returnMessage = "Okay, we just created a group with those numbers. Please enter a message to send to them.";
        }
        return new Request(returnMessage, freeText("mtg3?groupId=" + groupToMessage.getId()));
    }

    @RequestMapping(value = "/ussd/mtg3")
    @ResponseBody
    public Request sendMessage(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) Long groupId,
                               @RequestParam(value="request", required=true) String userResponse) throws URISyntaxException {

        User userSending = new User();
        try { userSending = userManager.findByInputNumber(inputNumber); }
        catch (Exception e) { return noUserError; }

        String msgText = "From: " + userSending.getName("") + ": " + userResponse;

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful
        // todo: split up the URI into multiple if it gets >2k chars (will be an issue when have 20+ person groups)

        Group groupToMessage = groupManager.loadGroup(groupId);
        List<User> usersToMessage = groupToMessage.getGroupMembers();

        RestTemplate sendGroupSMS = new RestTemplate();
        UriComponentsBuilder sendMsgURI = UriComponentsBuilder.newInstance().scheme("https").host(smsHost);
        sendMsgURI.path("send/").queryParam("username", smsUsername).queryParam("password", smsPassword);

        for (int i = 1; i <= usersToMessage.size(); i++) {
            sendMsgURI.queryParam("number" + i, usersToMessage.get(i-1).getPhoneNumber());
            sendMsgURI.queryParam("message" + i, msgText);
        }

        String messageResult = sendGroupSMS.getForObject(sendMsgURI.build().toUri(), String.class);

        String returnMessage = "Done! We sent the message.";
        return new Request(returnMessage, new ArrayList<Option>());
    }
}
