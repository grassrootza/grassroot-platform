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
import za.org.grassroot.services.UserManager;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;


import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for the USSD menu
 * todo: abstract out the messages, so can introduce a dictionary mechanism of some sort to deal with languages
 * todo: avoid hard-coding the URLs in the menus, so we can swap them around later
 * todo: create mini-routines of common menu flows (e.g., create a group) so they can be inserted in multiple flows
 * todo: Check if responses are less than 140 characters before sending
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDHomeController extends USSDController {

    public Request welcomeMenu(String opening) throws URISyntaxException {

        Map<String, String> welcomeOptions = new LinkedHashMap<>();
        welcomeOptions.put("mtg", "Call a meeting");
        welcomeOptions.put("vote", "Take a vote");
        welcomeOptions.put("log", "Record an action");
        welcomeOptions.put("group", "Manage groups");
        welcomeOptions.put("user", "Change profile");
        return new Request(opening, createMenu(welcomeOptions));
    }

    @RequestMapping(value = "/ussd/start")
    @ResponseBody
    public Request startMenu(@RequestParam(value="msisdn") String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        if (sessionUser.needsToRenameSelf(10)) {
            return new Request("Hi! We notice you haven't set a name yet. What should we call you?", freeText("rename-start"));
        } else if (sessionUser.needsToRenameGroup() != null) {
            return new Request("Hi! Last time you created a group, but it doesn't have a name yet. What's it called?",
                    freeText("group-start?groupId=" + sessionUser.needsToRenameGroup().getId()));
        } else {
            String welcomeMessage = sessionUser.hasName() ? ("Hi " + sessionUser.getName("") + ". What do you want to do?") :
                    "Hi! Welcome to GrassRoot. What will you do?";
            return welcomeMenu(welcomeMessage);
        }
    }

    @RequestMapping(value = "/ussd/rename-start")
    @ResponseBody
    public Request renameAndStart(@RequestParam(value="msisdn") String inputNumber,
                                  @RequestParam(value="request") String userName) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        sessionUser.setDisplayName(userName);
        sessionUser = userManager.save(sessionUser);

        return welcomeMenu("Thanks " + userName + ". What do you want to do?");
    }

    @RequestMapping(value = "/ussd/group-start")
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value="msisdn") String passedNumber,
                                     @RequestParam(value="groupId") Long groupId,
                                     @RequestParam(value="request") String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        Group groupToRename = groupManager.loadGroup(groupId);
        groupToRename.setGroupName(groupName);
        groupToRename = groupManager.saveGroup(groupToRename);

        return welcomeMenu("Thanks! Now what do you want to do?");

    }

    @RequestMapping(value = { "/ussd/error", "/ussd/vote", "/ussd/log", "/ussd/grp2" })
    @ResponseBody
    public Request notBuilt() throws URISyntaxException {
        String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        return new Request(errorMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = "/ussd/test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

}
