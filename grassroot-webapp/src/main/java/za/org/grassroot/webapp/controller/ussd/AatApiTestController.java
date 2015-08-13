package za.org.grassroot.webapp.controller.ussd;

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

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;


import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller to play around with the AAT api
 * To do: abstract the tuples of menu option and URL redirect
 * To do: abstract out the messages, so can introduce a dictionary mechanism of some sort to deal with languages
 * To do: write a phone number parsing method / converter so we get them consistent. Coming in as 27....
 * To do: avoid hard-coding the URLs in the menus, so we can swap them around later
 * To do: add in functions to prompt returning user for display name, and use display names in building groups
 * To do: wire up creating groups sub-routines in various sub-menus
 * To do: Check if responses are less than 140 characters before sending
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class AatApiTestController {

    /**
     * Set up of wiring, some strings that are used frequently, and the main menu
     * To do: Insert logic to check if user has set their display name and, if not, prompt for it
     * To do: Insert logic to check for unnamed groups and then prompt to name them
     * To do: Move 'manage groups' earlier in the main menu
     */

    String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";
    private String smsHost = "xml2sms.gsm.co.za";

    private String smsUsername = System.getenv("SMSUSER");
    private String smsPassword = System.getenv("SMSPASS");

    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<Option>());

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

        String phoneNumber = convertPhoneNumber(passedNumber);
        User sessionUser = loadOrSaveUser(phoneNumber);

        if (sessionUser.needsToRenameSelf(10)) {
            return new Request("Hi! We notice you haven't set a name yet. What should we call you?", freeText("rename-start"));
        } else if (sessionUser.needsToRenameGroup()) {
            return new Request("Hi! Last time you created a group, but it doesn't have a name yet. What's it called?",
                    freeText("group-start"));
        } else {
            String welcomeMessage = sessionUser.hasName() ? ("Hi " + sessionUser.getName("") + ". What do you want to do?") :
                    "Hi! Welcome to GrassRoot. What will you do?";
            return welcomeMenu(welcomeMessage);
        }
    }

    @RequestMapping(value = "/ussd/rename-start")
    @ResponseBody
    public Request renameAndStart(@RequestParam(value="msisdn") String passedNumber,
                                  @RequestParam(value="request") String userName) throws URISyntaxException {

        User sessionUser = loadOrSaveUser(convertPhoneNumber(passedNumber));
        sessionUser.setDisplayName(userName);
        sessionUser = userRepository.save(sessionUser);

        return welcomeMenu("Thanks " + userName + ". What do you want to do?");
    }

    @RequestMapping(value = "/ussd/group-start")
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value="msisdn") String passedNumber,
                                     @RequestParam(value="groupId") Long groupId,
                                     @RequestParam(value="request") String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        Group groupToRename = groupRepository.findOne(groupId);
        groupToRename.setGroupName(groupName);
        groupToRename = groupRepository.save(groupToRename);

        return welcomeMenu("Thanks! Now what do you want to do?");

    }

    public Request welcomeMenu(String opening) throws URISyntaxException {

        final Option meetingOrg = new Option("Call a meeting", 1,1, new URI(baseURI + "mtg"),true);
        final Option voteTake = new Option("Take a vote", 2,2, new URI(baseURI + "vote"),true);
        final Option logAction = new Option("Record an action", 3,3, new URI(baseURI + "log"),true);
        final Option manageGroups = new Option("Manage groups", 4, 4, new URI(baseURI + "group"), true);
        final Option userProfile = new Option("Change profile", 5, 5, new URI(baseURI + "user"), true);
        return new Request(opening, Arrays.asList(meetingOrg, voteTake, logAction, manageGroups, userProfile));

    }

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

        try { sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
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

        String phoneNumber = convertPhoneNumber(inputNumber);
        String returnMessage;

        User sessionUser = new User();
        Group groupToMessage = new Group();

        try { sessionUser = userRepository.findByPhoneNumber(phoneNumber).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        if (groupId != null) {
            if (groupId == 0) {
                return new Request("Okay. We'll create a new group for this meeting. Please enter the numbers for it.",
                        freeText("mtg2"));
            } else {
                groupToMessage = groupRepository.findOne(groupId);
                returnMessage = "Okay, please enter the message to send to the group.";
            }
        } else {
            groupToMessage = createNewGroup(sessionUser, userResponse);
            returnMessage = "Okay, we just created a group with those numbers. Please enter a message to send to them.";
        }
        return new Request(returnMessage, freeText("mtg3?groupId=" + groupToMessage.getId()));
    }

    @RequestMapping(value = "/ussd/mtg3")
    @ResponseBody
    public Request sendMessage(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) String groupId,
                               @RequestParam(value="request", required=true) String userResponse) throws URISyntaxException {

        User userSending = new User();
        try { userSending = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
        catch (Exception e) { return noUserError; }

        String msgText = "From: " + userSending.getName("") + ": " + userResponse;

        // todo: various forms of error handling here (e.g., non-existent group, invalid users, etc)
        // todo: store the response from the SMS gateway and use it to state how many messages successful
        // todo: split up the URI into multiple if it gets >2k chars (will be an issue when have 20+ person groups)

        Group groupToMessage = groupRepository.findOne(Long.parseLong(groupId));
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

    /**
     * Starting the group management menu flow here
     * To do: Add in validation and checking that group is valid, and user can call a meeting on it
     * To do: Add in extracting names and numbers from groups without names so users know what group it is
     * To do: Stub out remaining menus
     */

    @RequestMapping(value = "ussd/group")
    @ResponseBody
    public Request groupList(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        User userForSession = userRepository.findByPhoneNumber(phoneNumber).iterator().next();

        String returnMessage = "Okay! Please pick one of the groups you belong to:";
        return new Request(returnMessage, userGroupMenu(userForSession, "group/menu", true));

    }

    @RequestMapping(value = "ussd/group/menu")
    @ResponseBody
    public Request groupMenu(@RequestParam(value="msisdn", required=true) String inputNumber,
                             @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: check what permissions the user has and only display options that they can do
        // todo: think about slimming down the menu size

        String returnMessage = "Group selected. What would you like to do?";
        String groupIdP = "?groupId=" + groupId;

        Option listGroup = new Option("List group members", 1, 1, new URI(baseURI + "group/list" + groupIdP), true);
        Option renameGroup = new Option("Rename the group", 2, 2, new URI(baseURI + "group/rename" + groupIdP), true);
        Option addNumber = new Option("Add a phone number to the group", 3, 3, new URI(baseURI + "group/addnumber" + groupIdP), true);
        Option removeMe = new Option("Remove me from the group", 4, 4, new URI(baseURI + "group/unsubscribe" + groupIdP), true);
        Option removeNumber = new Option("Remove a number from the group", 5, 5, new URI(baseURI + "group/delnumber" + groupIdP), true);
        Option delGroup = new Option("Delete this group (beta only)", 6, 6, new URI(baseURI + "group/delgroup" + groupIdP), true);

        return new Request(returnMessage, Arrays.asList(listGroup, renameGroup, addNumber, removeMe, removeNumber, delGroup));

    }

    @RequestMapping(value = "ussd/group/list")
    @ResponseBody
    public Request listGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                             @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: only list users who are not the same as the user calling the function
        // todo: check if user has a display name, and, if so, just print the display name

        Group groupToList = new Group();
        try { groupToList = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        List<String> usersList = new ArrayList<>();
        for (User userToList : groupToList.getGroupMembers()) {
            usersList.add(invertPhoneNumber(userToList.getPhoneNumber()));
        }

        String returnMessage = "Users in this group are: " + String.join(", ", usersList);

        return new Request(returnMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = "ussd/group/rename")
    @ResponseBody
    public Request renamePrompt(@RequestParam(value="msisdn", required=true) String inputNumber,
                                @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        Group groupToRename = new Group();
        String promptMessage;

        try { groupToRename = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        if (groupToRename.getGroupName().trim().length() == 0)
            promptMessage = "This group doesn't have a name yet. Please enter a name.";
        else
            promptMessage = "This group's current name is " + groupToRename.getGroupName() + ". What do you want to rename it?";

        return new Request(promptMessage, freeText("group/rename2?groupId=" + groupId));

    }

    @RequestMapping(value = "ussd/group/rename2")
    @ResponseBody
    public Request renameGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) Long groupId,
                               @RequestParam(value="request", required=true) String newName) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        Group groupToRename = new Group();
        try { groupToRename = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        groupToRename.setGroupName(newName);
        groupToRename = groupRepository.save(groupToRename);

        return new Request("Group successfully renamed to " + groupToRename.getGroupName(), new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/addnumber")
    @ResponseBody
    public Request addNumberInput(@RequestParam(value="msisdn", required=true) String inputNumber,
                                    @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        String promptMessage = "Okay, we'll add a number to this group. Please enter it below.";
        return new Request(promptMessage, freeText("group/addnumber2?groupId=" + groupId));

    }

    @RequestMapping(value = "ussd/group/addnumber2")
    @ResponseBody
    public Request addNummberToGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                                     @RequestParam(value="groupId", required=true) Long groupId,
                                     @RequestParam(value="request", required=true) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it
        // todo: check the user-to-add isn't already part of the group, and, if so, notify the user who is adding
        // todo: build logic to handle it if the number submitted is badly formatted/doesn't work/etc

        Group sessionGroup = new Group();
        try { sessionGroup = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        List<User> groupMembers = sessionGroup.getGroupMembers();
        groupMembers.add(loadOrSaveUser(convertPhoneNumber(numberToAdd)));
        sessionGroup.setGroupMembers(groupMembers);
        sessionGroup = groupRepository.save(sessionGroup);

        return new Request("Done! The group has been updated.", new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/unsubscribe")
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value="msisdn", required=true) String inputNumber,
                                      @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        String promptMessage = "Are you sure you want to remove yourself from this group?";
        Option yesOption = new Option("Yes, take me off.", 1, 1, new URI(baseURI + "group/unsubscribe2?groupId=" + groupId), true);
        Option noOption = new Option("No, return to the last menu", 2, 2, new URI(baseURI + "group/menu?groupId=" + groupId), true);

        return new Request(promptMessage, Arrays.asList(yesOption, noOption));

    }

    @RequestMapping(value = "ussd/group/unsubscribe2")
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value="msisdn", required=true) String inputNumber,
                                 @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        User sessionUser = new User();
        try { sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        Group sessionGroup = new Group();
        try { sessionGroup = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        // todo: add error and exception handling, as well as validation and checking (e.g., if user in group, etc)
        // todo: check if the list in the user is updated too ...

        sessionGroup.getGroupMembers().remove(sessionUser);
        sessionGroup = groupRepository.save(sessionGroup);

        String returnMessage = "Done! You won't receive messages from that group anymore.";

        return new Request(returnMessage, new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/delgroup")
    @ResponseBody
    public Request deleteConfirm(@RequestParam(value="msisdn", required=true) String inputNumber,
                                 @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: add confirmation screen
        // todo: check for user permissions
        // todo: generally make this more than a quick-and-dirty to clean up prototype database

        String returnMessage;
        Group sessionGroup = new Group();
        try { sessionGroup = groupRepository.findOne(groupId); }
        catch (Exception e) { return noGroupError; }

        try {
            sessionGroup.setGroupMembers(new ArrayList<User>());
            sessionGroup = groupRepository.save(sessionGroup);
            groupRepository.delete(sessionGroup.getId());
            returnMessage = "Success! The group is gone.";
        } catch (Exception e) {
            returnMessage = "Nope, something went wrong with deleting that group.";
        }

        return new Request(returnMessage, new ArrayList<Option>());

    }

    /**
     * Starting the user profile management flow here
     */

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

    @RequestMapping(value = "ussd/user/name")
    @ResponseBody
    public Request userDisplayName(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = new User();
        try { sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        String promptPhrase;
        if (!sessionUser.hasName()) {
            promptPhrase = "You haven't set your name yet. What name do you want to use?";
        } else {
            promptPhrase = "Your name is currently set to '" + sessionUser.getDisplayName() + "'. What do you want to change it to?";
        }

        return new Request(promptPhrase, freeText("user/name2"));
    }

    @RequestMapping(value = "ussd/user/name2")
    @ResponseBody
    public Request userChangeName(@RequestParam(value="msisdn", required=true) String inputNumber,
                                  @RequestParam(value="request", required=true) String newName) throws URISyntaxException {

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser = new User();
        try { sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next(); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setDisplayName(newName);
        sessionUser = userRepository.save(sessionUser);

        return new Request("Success! Name changed to " + sessionUser.getDisplayName(), new ArrayList<Option>());
    }

    /**
     * Starting stubs and commonly-used mini flows here
     */

    @RequestMapping(value = { "/ussd/error", "/ussd/vote", "/ussd/log", "/ussd/grp2" })
    @ResponseBody
    public Request notBuilt() throws URISyntaxException {
        String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        return new Request(errorMessage, new ArrayList<Option>());
    }

    /**
     * Auxiliary and helper methods start here ...
     * To do: Create an 'invert phoneNumber' aux method, to turn '2781...' into user readable format
     * To do: Expand -- a lot -- the various methods needed to handle phone number inputs
     * To do: Move some of the code for renaming groups from menu function down here
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

    public List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    public List<Option> userGroupMenu(User userForSession, String path, boolean optionNewGroup) throws URISyntaxException {

        // todo: some way to handle pagination if user has many groups -- USSD can only handle five options on a menu ...
        // todo: also, lousy user experience if too many -- should sort by last accessed & most accessed (some combo)

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
            menuBuilder.add(new Option ("Create a new group", listLength+1, listLength+1, new URI(baseURI+path+"?groupId=0"), true));

        return menuBuilder;
    }

    public Group createNewGroup(User creatingUser, String phoneNumbers) {

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        Group groupToCreate = new Group();

        groupToCreate.setCreatedByUser(creatingUser);
        groupToCreate.setGroupName(""); // column not-null, so use blank string as default

        List<User> usersToCreateGroup = usersFromNumbers(phoneNumbers);
        usersToCreateGroup.add(creatingUser); // So that later actions pick up whoever created group
        groupToCreate.setGroupMembers(usersToCreateGroup);

        return groupRepository.save(groupToCreate);

    }

    public List<User> usersFromNumbers(String listOfNumbers) {
        List<User> usersToReturn = new ArrayList<User>();

        // todo: uh, make less strong assumptions that users are perfectly well behaved ...

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

    // todo: build out the phone number / string handling methods, probably in service layer somewhere
    // todo: probably distinguish between numbers we get via msisdn param, and user inputted numbers, for speed

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

    public String invertPhoneNumber(String storedNumber) {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

        List<String> numComponents = new ArrayList<>();
        String prefix = String.join("", Arrays.asList("0", storedNumber.substring(2, 4)));
        String midnumbers, finalnumbers;

        try {
            midnumbers = storedNumber.substring(4,7);
            finalnumbers = storedNumber.substring(7,11);
        } catch (Exception e) { // in case the string doesn't have enough digits ...
            midnumbers = storedNumber.substring(4);
            finalnumbers = "";
        }

        return String.join(" ", Arrays.asList(prefix, midnumbers, finalnumbers));

    }
}
