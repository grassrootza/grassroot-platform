package za.org.grassroot.webapp.controller.ussd;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;


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

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    USSDEventUtil eventUtil;

    Logger log = LoggerFactory.getLogger(getClass());
    private static final String path = homePath + "/";
    private static final USSDSection home = USSDSection.BASE;

    private static final String keyRsvp = "rsvp", keyRenameStart = "rename-start", keyGroupNameStart = "group-start";
    private static final int hashPosition = Integer.valueOf(System.getenv("USSD_CODE_LENGTH"));

    public USSDMenu welcomeMenu(String opening, User sessionUser) throws URISyntaxException {

        USSDMenu homeMenu = new USSDMenu(opening);
        Locale menuLang = new Locale(getLanguage(sessionUser));

        homeMenu.addMenuOption(meetingMenus + startMenu, getMessage(homeKey, startMenu, optionsKey + mtgKey, menuLang));
        homeMenu.addMenuOption(voteMenus + startMenu, getMessage(homeKey, startMenu, optionsKey + voteKey, menuLang));
        homeMenu.addMenuOption(logMenus + startMenu, getMessage(homeKey, startMenu, optionsKey + logKey, menuLang));
        homeMenu.addMenuOption(groupMenus + startMenu, getMessage(homeKey, startMenu, optionsKey + groupKey, menuLang));
        homeMenu.addMenuOption(userMenus + startMenu, getMessage(homeKey, startMenu, optionsKey + userKey, menuLang));

        return homeMenu;
    }

    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value= phoneNumber) String inputNumber,
                             @RequestParam(value= userInputParam, required=false) String enteredUSSD) throws URISyntaxException {

        USSDMenu openingMenu;
        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        /*
        Adding some complex logic here to check for one of these things:
        (1) The user has appended a joining code, so we need to add them to a group
        (2) The user was in the middle of something (e.g., adding numbers), and might want to continue
        (3) The user has an outstanding RSVP request for a meeting
        (4) The user has not named themselves, or a prior group
        These will be processed in order, as below
        TODO: take a hard look at all this code to make it is as efficient as possible, given frequency of calling it
        and the need to make it highly efficient
         */

        if (codeHasTrailingDigits(enteredUSSD)) {
            String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
            openingMenu = processTrailingDigits(trailingDigits, sessionUser);
        } else if (userInterrupted(sessionUser)) {
            openingMenu = interruptedPrompt(sessionUser);
        } else if (userResponseNeeded(sessionUser)) {
            openingMenu = requestUserResponse(sessionUser);
        } else if (firstSession(sessionUser)) {
            sessionUser = userManager.setInitiatedSession(sessionUser);
            openingMenu = askForLanguage(sessionUser);
        } else {
            openingMenu = defaultStartMenu(sessionUser);
        }

        return menuBuilder(openingMenu);

    }

    private boolean firstSession(User sessionUser) {
        return userManager.isFirstInitiatedSession(sessionUser);
    }

    private USSDMenu askForLanguage(User sessionUser) {

        String prompt = getMessage(homeKey, startMenu, promptKey + "-language", sessionUser);
        String nextUrl = "start_language";
        USSDMenu promptMenu = new USSDMenu(prompt);

        for (Map.Entry<String, String> entry : userManager.getImplementedLanguages().entrySet()) {
            promptMenu.addMenuOption(nextUrl + "?language=" + entry.getKey(), entry.getValue());
        }

        return promptMenu;
    }

    @RequestMapping(value = path + startMenu + "_language")
    @ResponseBody
    public Request languageSetMenu(@RequestParam(value= phoneNumber) String inputNumber,
                                   @RequestParam(value="language") String language) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        sessionUser = userManager.setUserLanguage(sessionUser, language);
        return menuBuilder(defaultStartMenu(sessionUser));

    }

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = path + startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {

        return menuBuilder(defaultStartMenu(userManager.loadOrSaveUser(inputNumber)));

    }

    private USSDMenu interruptedPrompt(User sessionUser) {

        String returnUrl = sessionUser.getLastUssdMenu();
        log.info("The user was interrupted somewhere ...Here's the URL: " + returnUrl);

        USSDMenu promptMenu = new USSDMenu(getMessage(homeKey, startMenu, promptKey + "-interrupted", sessionUser));
        promptMenu.addMenuOption(returnUrl, getMessage(homeKey, startMenu, "interrupted.resume", sessionUser));
        promptMenu.addMenuOption(startMenu + "_force", getMessage(homeKey, startMenu, "interrupted.start", sessionUser));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        userManager.resetLastUssdMenu(sessionUser);

        return promptMenu;

    }

    private boolean userInterrupted(User sessionUser) {
        return (sessionUser.getLastUssdMenu() != null && !sessionUser.getLastUssdMenu().trim().equals(""));
    }

    private boolean userResponseNeeded(User sessionUser) {
        log.info("Checking if user needs to respond to anything, either a vote or an RSVP ...");
        return userManager.needsToVoteOrRSVP(sessionUser) || userManager.needsToRenameSelf(sessionUser);

        /* For the moment, removing the group rename and user rename, as is overloading the start menu
        and use cases seem limited (users also getting confused). Will re-evaluate later.
        return userManager.needsToRSVP(sessionUser) || groupManager.needsToRenameGroup(sessionUser)
                || userManager.needsToRenameSelf(sessionUser); */
    }

    private USSDResponseTypes neededResponse(User sessionUser) {

        /* Note: the sequence in which these are checked and returned sets the order of priority of responses */

        if (userManager.needsToVote(sessionUser)) {
            log.info("User needs to vote!");
            return USSDResponseTypes.VOTE;
        }
        if (userManager.needsToRSVP(sessionUser)) return USSDResponseTypes.MTG_RSVP;
        if (userManager.needsToRenameSelf(sessionUser)) return USSDResponseTypes.RENAME_SELF;
        // if (groupManager.needsToRenameGroup(sessionUser)) return USSDResponseTypes.NAME_GROUP; // disabled for now

        return USSDResponseTypes.NONE;

    }

    private USSDMenu processTrailingDigits(String trailingDigits, User sessionUser) throws URISyntaxException {

        USSDMenu returnMenu;

        // todo: a switch logic for token ranges

        System.out.println("Processing trailing digits ..." + trailingDigits);

        if (groupManager.tokenExists(trailingDigits)) {
            // todo: basic validation, checking, etc.
            log.info("Found a token with these trailing digits ...");
            Group groupToJoin = groupManager.getGroupByToken(trailingDigits);
            groupManager.addGroupMember(groupToJoin, sessionUser);
            String prompt = (groupToJoin.hasName()) ?
                    getMessage(homeKey, startMenu, promptKey + ".group.token.named", groupToJoin.getGroupName(), sessionUser) :
                    getMessage(homeKey, startMenu, promptKey + ".group.token.unnamed", sessionUser);
            returnMenu = welcomeMenu(prompt, sessionUser);
        } else {
            System.out.println("Whoops, couldn't find the code");
            returnMenu = welcomeMenu(getMessage(homeKey, startMenu, promptKey + ".unknown.request", sessionUser), sessionUser);
        }

        return returnMenu;

    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private USSDMenu defaultStartMenu(User sessionUser) throws URISyntaxException {

        String welcomeMessage = sessionUser.hasName() ? getMessage(homeKey, startMenu, promptKey + "-named", sessionUser.getName(""), sessionUser) :
                    getMessage(homeKey, startMenu, promptKey, sessionUser);
        return welcomeMenu(welcomeMessage, sessionUser);

    }

    private USSDMenu requestUserResponse(User sessionUser) throws URISyntaxException {

        USSDMenu startMenu = new USSDMenu();

        switch (neededResponse(sessionUser)) {
            case VOTE:
                log.info("Asking for a vote ... from user " + sessionUser);
                Long voteId = eventManager.getNextOutstandingVote(sessionUser);

                final Map<String, String> voteDetails = eventManager.getEventDescription(voteId);
                final String[] promptFields = new String[]{ voteDetails.get("groupName"), voteDetails.get("creatingUser"),
                        voteDetails.get("eventSubject")};

                final String voteUri = "vote" + eventIdUrlSuffix + voteId + "&response=";
                final String optionMsgKey = voteKey + "." + optionsKey;

                startMenu.setPromptMessage(getMessage(homeKey, USSDController.startMenu, promptKey + "-vote", promptFields, sessionUser));

                startMenu.addMenuOption(voteUri + "yes", getMessage(optionMsgKey + "yes", sessionUser));
                startMenu.addMenuOption(voteUri + "no", getMessage(optionMsgKey + "no", sessionUser));
                startMenu.addMenuOption(voteUri + "maybe", getMessage(optionMsgKey + "abstain", sessionUser));
                break;
            case MTG_RSVP:
                log.info("Asking for rsvp!");
                Event meeting = eventManager.getOutstandingRSVPForUser(sessionUser).get(0);
                String[] meetingDetails = eventManager.populateNotificationFields(meeting);

                // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
                String defaultPrompt = getMessage(homeKey, USSDController.startMenu, promptKey + "-" + keyRsvp, meetingDetails, sessionUser);
                if (defaultPrompt.length() > 120)
                    defaultPrompt = getMessage(homeKey, USSDController.startMenu, promptKey + "-" + keyRsvp + ".short", meetingDetails, sessionUser);

                String optionUri = keyRsvp + eventIdUrlSuffix + meeting.getId();
                startMenu.setPromptMessage(defaultPrompt);
                startMenu.setMenuOptions(new LinkedHashMap<>(optionsYesNo(sessionUser, optionUri, optionUri)));
                break;
            case RENAME_SELF:
                startMenu.setPromptMessage(getMessage(homeKey, USSDController.startMenu, promptKey + "-rename", sessionUser));
                startMenu.setFreeText(true);
                startMenu.setNextURI(keyRenameStart);
                break;
            case NAME_GROUP:
                startMenu.setPromptMessage(getMessage(homeKey, USSDController.startMenu, promptKey + "-group-rename", sessionUser));
                startMenu.setFreeText(true);
                startMenu.setNextURI(keyGroupNameStart + groupIdUrlSuffix + groupManager.groupToRename(sessionUser));
                break;
            case NONE:
                startMenu = defaultStartMenu(sessionUser);
        }

        return startMenu;

    }

    /*
    Menus to process responses to votes and RSVPs,
     */

    @RequestMapping(value = path + keyRsvp)
    @ResponseBody
    public Request rsvpAndWelcome(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= eventIdParam) Long eventId,
                                  @RequestParam(value= yesOrNoParam) String attending) throws URISyntaxException {

        String welcomeKey;
        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        if (attending.equals("yes")) {
            eventLogManagementService.rsvpForEvent(eventId, inputNumber, EventRSVPResponse.YES);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-yes"));
        } else {
            eventLogManagementService.rsvpForEvent(eventId, inputNumber, EventRSVPResponse.NO);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-no"));
        }

        return menuBuilder(new USSDMenu(getMessage(welcomeKey, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = path + "vote")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= eventIdParam) Long voteId,
                                  @RequestParam(value="response") String response) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        eventLogManagementService.rsvpForEvent(voteId, inputNumber, EventRSVPResponse.fromString(response));

        return menuBuilder(new USSDMenu(getMessage(homeKey, startMenu, promptKey, "vote-recorded", sessionUser),
                                        optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = path + keyRenameStart)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (userName.equals("0") || userName.trim().equals("")) {
            welcomeMessage = getMessage(homeKey, startMenu, promptKey, sessionUser);
        } else {
            sessionUser = userManager.setDisplayName(sessionUser, userName);
            welcomeMessage = getMessage(homeKey, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = path + keyGroupNameStart)
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value= phoneNumber) String inputNumber,
                                     @RequestParam(value= groupIdParam) Long groupId,
                                     @RequestParam(value= userInputParam) String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (groupName.equals("0") || groupName.trim().equals("")) {
            welcomeMessage = getMessage(homeKey, startMenu, promptKey, sessionUser);
        } else {
            Group groupToRename = groupManager.loadGroup(groupId);
            groupToRename.setGroupName(groupName);
            groupToRename = groupManager.saveGroup(groupToRename);
            welcomeMessage = getMessage(homeKey, startMenu, promptKey + "-group-do", sessionUser.nameToDisplay(), sessionUser);
        }

        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));

    }

    /*
    Helper methods, for group pagination, event pagination, etc.
     */

    @RequestMapping(value = path + "group_page")
    @ResponseBody
    public Request groupPaginationHelper(@RequestParam(value= phoneNumber) String inputNumber,
                                         @RequestParam(value="prompt") String prompt,
                                         @RequestParam(value="page") Integer pageNumber,
                                         @RequestParam(value="existingUri") String existingUri,
                                         @RequestParam(value="newUri", required=false) String newUri) throws URISyntaxException {

        /*
         todo: this is going to need a way to pass the purpose of the group, or the permission filter (since which groups are
         displayed/paginated will vary a lot
          */
        return menuBuilder(ussdGroupUtil.
                userGroupMenuPaginated(userManager.findByInputNumber(inputNumber), prompt, existingUri, newUri, pageNumber));

    }

    @RequestMapping(value = path + "event_page")
    @ResponseBody
    public Request eventPaginationHelper(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = "section") String section,
                                         @RequestParam(value = "prompt") String prompt,
                                         @RequestParam(value = "page") Integer pageNumber,
                                         @RequestParam(value = "nextUrl") String nextUrl,
                                         @RequestParam(value = "pastPresentBoth") Integer pastPresentBoth,
                                         @RequestParam(value = "includeGroupName") boolean includeGroupName) throws URISyntaxException {
        // toto: error handling on the section
        return menuBuilder(eventUtil.listPaginatedEvents(
                userManager.findByInputNumber(inputNumber), USSDSection.fromString(section),
                prompt, nextUrl, includeGroupName, pastPresentBoth, pageNumber));
    }

    @RequestMapping(value = path + U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {
        // String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        String errorMessage = messageSource.getMessage("ussd.error", null, new Locale("en"));
        return menuBuilder(new USSDMenu(errorMessage, optionsHomeExit(userManager.findByInputNumber(inputNumber))));
    }

    @RequestMapping(value = path + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {
        userManager.resetLastUssdMenu(userManager.loadOrSaveUser(inputNumber));
        String exitMessage = getMessage("exit." + promptKey, userManager.loadOrSaveUser(inputNumber));
        return menuBuilder(new USSDMenu(exitMessage)); // todo: check if methods can handle empty list of options
    }

    @RequestMapping(value = path + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = path + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return tooLongError;
    }


}