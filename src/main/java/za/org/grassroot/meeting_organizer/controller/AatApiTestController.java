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
import za.org.grassroot.meeting_organizer.model.AAT.Option;
import za.org.grassroot.meeting_organizer.model.AAT.Request;
import za.org.grassroot.meeting_organizer.model.Group;
import za.org.grassroot.meeting_organizer.model.User;
import za.org.grassroot.meeting_organizer.service.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.service.repository.UserRepository;

import javax.print.URIException;

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

        // todo: probably another auxiliary function here ("loadOrSaveUser")
        User sessionUser = new User();
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
            sessionUser.setPhoneNumber(phoneNumber);
            userRepository.save(sessionUser);
        } else {
            sessionUser = userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        }

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

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        // note: not calling the instance of User as we don't use anything ... yet
        // todo: look up the user's groups, and give a menu of those, if they exist (w/ scrolling)
        // todo: Add an example in promptMessage, but make sure not too long (already too much, maybe).
        String promptMessage = "Okay, we'll set up a meeting. Please enter the numbers of the people to invite.";
        Option freeText = new Option("", 1, 1, new URI(baseURI + "mtg2"), false);
        return new Request(promptMessage, Collections.singletonList(freeText));
    }

    @RequestMapping(value = "/ussd/mtg2")
    @ResponseBody
    public Request saveNumbers(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="request", required=true) String userResponse) throws URISyntaxException {

        String phoneNumber = convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) return noUserError;

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        Group groupToMessage = new Group();
        User groupCreator = userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        groupToMessage.setCreatedByUser(groupCreator);
        groupToMessage.setGroupName(""); // column not-null, so use blank string as default

        List<User> usersToCreateGroup = usersFromNumbers(userResponse);
        usersToCreateGroup.add(groupCreator); // So that later actions pick up whoever created group
        groupToMessage.setGroupMembers(usersToCreateGroup);

        groupRepository.save(groupToMessage);

        List<String> transientDisplay = new ArrayList<String>();
        for (User userToDisplay : usersToCreateGroup)
            transientDisplay.add(userToDisplay.getPhoneNumber());

        String returnMessage = "We just created a group with these numbers: " + String.join(" ", transientDisplay) +
                ". We'll do something with it soon, promise.";
        return new Request(returnMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = { "/ussd/error", "/ussd/vote", "/ussd/log", "/ussd/user", "ussd/group" })
    @ResponseBody
    public Request notBuilt() throws URISyntaxException {
        String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        return new Request(errorMessage, new ArrayList<Option>());
    }

    // todo: decide on our preferred string format, for now keeping it at for 27 (not discarding info)
    // todo: add error handling to this.
    // todo: consider using Guava libraries, or another, for when we get to tricky user input
    // todo: put this in a wrapper class for a bunch of auxiliary methods? think we'll use this a lot
    public String convertPhoneNumber(String inputString) {
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
