package za.org.grassroot.meeting_organizer.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.meeting_organizer.model.AAT.Option;
import za.org.grassroot.meeting_organizer.model.AAT.Request;
import za.org.grassroot.meeting_organizer.model.Group;
import za.org.grassroot.meeting_organizer.model.User;
import za.org.grassroot.meeting_organizer.service.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.service.repository.UserRepository;

import javax.net.ssl.HttpsURLConnection;
import javax.print.URIException;
import javax.servlet.http.HttpServletRequest;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller to play around with the AAT api
 * To do: abstract the tuples of menu option and URL redirect
 * To do: abstract out the messages, so can introduce a dictionary mechanism of some sort to deal with languages
 * To do: write a phone number parsing method / converter so we get them consistent. Coming in as 27....
 * To do: avoid hard-coding the URLs in the menus, so we can swap them around later
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class AatApiTestController {

    String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";
    private UriComponentsBuilder smsBaseUri = UriComponentsBuilder.newInstance().scheme("https").host("xml2sms.gsm.co.za");
    private String smsUsername = ""; // todo: set these when get from AAT
    private String smsPassword = ""; // todo: set these when get from AAT

    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @RequestMapping(value = "/ussd/test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = "/ussd/start")
    @ResponseBody
    public Request startMenu(@RequestParam(value="msisdn") String passedNumber) throws URISyntaxException {
        // So we need to create a user from this. Here we go.
        String phoneNumber = convertPhoneNumber(passedNumber);
        User sessionUser = loadOrSaveUser(phoneNumber);

        String welcomeMessage = "Hello! Welcome to GrassRoot. What do you want to do?";
        final Option meetingOrg = new Option("Call a meeting", 1,1, new URI(baseURI + "mtg"),true);
        final Option voteTake = new Option("Take a vote", 2,2, new URI(baseURI + "vote"),true);
        final Option logAction = new Option("Record an action", 3,3, new URI(baseURI + "log"),true);
        final Option userProfile = new Option("Change my profile", 4, 4, new URI(baseURI + "user"), true);
        final Option manageGroups = new Option("Manage groups", 5, 5, new URI(baseURI + "group"), true);
        return new Request(welcomeMessage, Arrays.asList(meetingOrg, voteTake, logAction, userProfile, manageGroups));
    }

    @RequestMapping(value = "/ussd/mtg")
    @ResponseBody
    public Request meetingOrg(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();

        try { sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.getGroupsPartOf().isEmpty()) {
            String promptMessage = "Okay, we'll set up a meeting. Please enter the numbers of the people to invite.";
            return new Request(promptMessage, Collections.singletonList(freeText("mtg2")));
        } else {
            String promptMessage = "Do you want to call a meeting of an existing group, or create a new one?";
            return new Request(promptMessage, userGroupMenu(sessionUser, "mtg2", true));
        }
    }

    @RequestMapping(value = "/ussd/mtg2")
    @ResponseBody
    public Request saveNumbers(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="request", required=false) String userResponse,
                               @RequestParam(value="groupId", required=false) Integer groupId) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        String returnMessage;

        User sessionUser = new User();
        Group groupToMessage = new Group();

        try { sessionUser = userRepository.findByPhoneNumber(phoneNumber).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        if (groupId != null) {
            if (groupId == 0) {
                return new Request("Okay. We'll create a new group for this meeting. Please enter the numbers for it.",
                        Collections.singletonList(freeText("mtg2")));
            } else {
                groupToMessage = groupRepository.findOne(groupId);
                returnMessage = "Okay, please enter the message to send to the group.";
            }
        } else {
            groupToMessage = createNewGroup(sessionUser, userResponse);
            returnMessage = "Okay, we just created a group with those numbers. Please enter a message to send to them.";
        }
        return new Request(returnMessage, Collections.singletonList(freeText("mtg3?groupId=" + groupToMessage.getId())));
    }

    @RequestMapping(value = "/ussd/mtg3")
    @ResponseBody
    public Request sendMessage(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) String groupId,
                               @RequestParam(value="request", required=true) String userResponse) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful
        // todo: split up the URI into multiple if it gets >2k chars (will be an issue when have 20+ person groups)

        Group groupToMessage = groupRepository.findOne(Integer.parseInt(groupId));
        List<User> usersToMessage = groupToMessage.getGroupMembers();

        RestTemplate sendGroupSMS = new RestTemplate();
        UriComponentsBuilder sendMsgURI = smsBaseUri.path("send/").queryParam("username", smsUsername).queryParam("password", smsPassword);

        for (int i = 1; i <= usersToMessage.size(); i++) {
            sendMsgURI = sendMsgURI.queryParam("number" + i, usersToMessage.get(i-1).getPhoneNumber());
            sendMsgURI = sendMsgURI.queryParam("message" + i, userResponse);
        }

        String returnMessage = sendMsgURI.build().toUriString(); // use for debugging, for now
        // String returnMessage = sendGroupSMS.getForObject(sendMsgURI.build().toUri(), String.class);

        // String returnMessage = "Well, when we get the SMS gateway up, that will have sent the message. We hope.";
        return new Request(returnMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = "ussd/user")
    @ResponseBody
    public Request userProfile(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        User userForSession = userRepository.findByPhoneNumber(phoneNumber).iterator().next();

        String returnMessage = "What would you like to do?";

        final Option changeName = new Option("Change my display name", 1,1, new URI(baseURI + "user/name"),true);
        final Option changeLanguage = new Option("Change my language", 2,2, new URI(baseURI + "user/language"),true);
        final Option addNumber = new Option("Add phone number to my profile", 3,3, new URI(baseURI + "user/phone"), true);

        return new Request(returnMessage, Arrays.asList(changeName, changeLanguage, addNumber));

    }

    @RequestMapping(value = "ussd/group")
    @ResponseBody
    public Request groupList(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        User userForSession = userRepository.findByPhoneNumber(phoneNumber).iterator().next();

        String returnMessage = "Okay! Please pick one of the groups you belong to:";
        return new Request(returnMessage, userGroupMenu(userForSession, "grp2", true));

    }

    @RequestMapping(value = { "/ussd/error", "/ussd/vote", "/ussd/log", "/ussd/grp2" })
    @ResponseBody
    public Request notBuilt() throws URISyntaxException {
        String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        return new Request(errorMessage, new ArrayList<Option>());
    }

    /*
     * Auxiliary and helper methods start here ...
     * */

    public User loadOrSaveUser(String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
            User sessionUser = new User();
            sessionUser.setPhoneNumber(phoneNumber);
            return userRepository.save(sessionUser);
        } else {
            return userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        }
    }

    public Option freeText(String urlEnding) throws URISyntaxException {
        return new Option("", 1, 1, new URI(baseURI + urlEnding), false);
    }

    public List<Option> userGroupMenu(User userForSession, String path, boolean optionNewGroup) throws URISyntaxException {

        List<Group> groupsPartOf = userForSession.getGroupsPartOf();
        List<Option> menuBuilder = new ArrayList<Option>();
        int listLength = groupsPartOf.size();

        for (int i = 0; i < listLength; i++) {
            Group groupForMenu = groupsPartOf.get(i);
            String groupName = groupForMenu.getGroupName();
            if (groupName == null || groupName.isEmpty())
                groupName = "Unnamed group, created on " + String.format("%1$TD", groupForMenu.getCreatedDateTime());
            menuBuilder.add(new Option(groupName,i+1,i+1, new URI(baseURI+path+"?groupId="+groupForMenu.getId()),true));
        }

        if (optionNewGroup)
            menuBuilder.add(new Option ("New group", listLength, listLength, new URI(baseURI+path+"?groupId=0"), true));

        return menuBuilder;
    }

    public Group createNewGroup(User creatingUser, String phoneNumbers) {

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        System.out.println("Got to createNewGroup with this string:" + phoneNumbers);

        Group groupToCreate = new Group();

        groupToCreate.setCreatedByUser(creatingUser);
        groupToCreate.setGroupName(""); // column not-null, so use blank string as default

        List<User> usersToCreateGroup = usersFromNumbers(phoneNumbers);
        usersToCreateGroup.add(creatingUser); // So that later actions pick up whoever created group
        groupToCreate.setGroupMembers(usersToCreateGroup);

        return groupRepository.save(groupToCreate);

    }

    public String convertPhoneNumber(String inputString) {

        // todo: decide on our preferred string format, for now keeping it at for 27 (not discarding info)
        // todo: add error handling to this.
        // todo: consider using Guava libraries, or another, for when we get to tricky user input
        // todo: put this in a wrapper class for a bunch of auxiliary methods? think we'll use this a lot

        int codeLocation = inputString.indexOf("27");
        boolean hasCountryCode = (codeLocation >= 0 && codeLocation < 2); // allowing '1' for '+' and 2 for '00'
        if (hasCountryCode) {
            return inputString.substring(codeLocation);
        } else {
            String truncedNumber = (inputString.charAt(0) == '0') ? inputString.substring(1) : inputString;
            return "27" + truncedNumber;
        }
    }

    public List<User> usersFromNumbers(String listOfNumbers) {
        List<User> usersToReturn = new ArrayList<User>();

        // todo: uh, make less strong assumptions that users are perfectly well behaved ...

        System.out.println("Got to usersFromNumbers with this string:" + listOfNumbers);
        listOfNumbers = listOfNumbers.replace("\"", ""); // in case the response is passed with quotes around it
        List<String> splitNumbers = Arrays.asList(listOfNumbers.split(" "));
        List<User> usersToAdd = new ArrayList<User>();

        for (String inputNumber : splitNumbers) {
            String phoneNumber = convertPhoneNumber(inputNumber);
            if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
                User userToCreate = new User();
                userToCreate.setPhoneNumber(phoneNumber);
                userRepository.save(userToCreate); // removing in deployment, so don't swamp Heroku DB with crud
                usersToAdd.add(userToCreate);
            } else {
                usersToAdd.add(userRepository.findByPhoneNumber(phoneNumber).iterator().next());
            }
        }
        return usersToAdd;
    }
}
