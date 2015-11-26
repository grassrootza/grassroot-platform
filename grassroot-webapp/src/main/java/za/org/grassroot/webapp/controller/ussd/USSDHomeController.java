package za.org.grassroot.webapp.controller.ussd;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
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
import za.org.grassroot.webapp.enums.UssdOpeningPrompt;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;


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

    Logger log = LoggerFactory.getLogger(getClass());

    private static final String keyRsvp = "rsvp", keyRenameStart = "rename-start", keyGroupNameStart = "group-start";
    private static final int hashPosition = Integer.valueOf(System.getenv("USSD_CODE_LENGTH"));

    public USSDMenu welcomeMenu(String opening, User sessionUser) throws URISyntaxException {

        USSDMenu homeMenu = new USSDMenu(opening);
        Locale menuLang = new Locale(getLanguage(sessionUser));

        homeMenu.addMenuOption(MTG_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + MTG_KEY, menuLang));
        homeMenu.addMenuOption(VOTE_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + VOTE_KEY, menuLang));
        homeMenu.addMenuOption(LOG_MENUS, getMessage(HOME_KEY, START_KEY, OPTION + LOG_KEY, menuLang));
        homeMenu.addMenuOption(GROUP_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + GROUP_KEY, menuLang));
        homeMenu.addMenuOption(USER_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + USER_KEY, menuLang));

        return homeMenu;
    }

    @RequestMapping(value = USSD_BASE + START_KEY)
    @ResponseBody
    public Request startMenu(@RequestParam(value=PHONE_PARAM) String inputNumber,
                             @RequestParam(value=TEXT_PARAM, required=false) String enteredUSSD) throws URISyntaxException {

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

        return (checkMenuLength(openingMenu, true)) ? menuBuilder(openingMenu) : menuBuilder(fixMenuLength(openingMenu, true));

    }

    private boolean firstSession(User sessionUser) {
        return userManager.isFirstInitiatedSession(sessionUser);
    }

    private USSDMenu askForLanguage(User sessionUser) {

        String prompt = getMessage(HOME_KEY, START_KEY, PROMPT + "-language", sessionUser);
        String nextUrl = "start_language";
        USSDMenu promptMenu = new USSDMenu(prompt);

        for (Map.Entry<String, String> entry : userManager.getImplementedLanguages().entrySet()) {
            promptMenu.addMenuOption(nextUrl + "?language=" + entry.getKey(), entry.getValue());
        }

        return promptMenu;
    }

    @RequestMapping(value = USSD_BASE + START_KEY + "_language")
    @ResponseBody
    public Request languageSetMenu(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                   @RequestParam(value="language") String language) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        sessionUser = userManager.setUserLanguage(sessionUser, language);
        return menuBuilder(defaultStartMenu(sessionUser));

    }

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = USSD_BASE + START_KEY + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value=PHONE_PARAM) String inputNumber) throws URISyntaxException {

        return menuBuilder(defaultStartMenu(userManager.loadOrSaveUser(inputNumber)));

    }

    private USSDMenu interruptedPrompt(User sessionUser) {

        String returnUrl = userManager.getLastUssdMenu(sessionUser);
        log.info("The user was interrupted somewhere ...Here's the URL: " + returnUrl);

        // try { returnUrl = URLEncoder.encode(sessionUser.getLastUssdMenu(), "UTF-8"); }
        // catch (Exception e) { returnUrl = sessionUser.getLastUssdMenu(); }

        USSDMenu promptMenu = new USSDMenu(getMessage(HOME_KEY, START_KEY, PROMPT + "-interrupted", sessionUser));
        promptMenu.addMenuOption(returnUrl, getMessage(HOME_KEY, START_KEY, "interrupted.resume", sessionUser));
        promptMenu.addMenuOption(START_KEY + "_force", getMessage(HOME_KEY, START_KEY, "interrupted.start", sessionUser));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        sessionUser = userManager.resetLastUssdMenu(sessionUser);

        return promptMenu;

    }

    private boolean userInterrupted(User sessionUser) {
        String lastMenu = userManager.getLastUssdMenu(sessionUser);
        return (lastMenu != null && !lastMenu.trim().equals(""));
    }

    private boolean userResponseNeeded(User sessionUser) {
        log.info("Checking if user needs to respond to anything, either a vote or an RSVP ...");
        return userManager.needsToVoteOrRSVP(sessionUser);

        /* For the moment, removing the group rename and user rename, as is overloading the start menu
        and use cases seem limited (users also getting confused). Will re-evaluate later.
        return userManager.needsToRSVP(sessionUser) || groupManager.needsToRenameGroup(sessionUser)
                || userManager.needsToRenameSelf(sessionUser); */
    }

    private UssdOpeningPrompt neededResponse(User sessionUser) {

        /* Note: the sequence in which these are checked and returned sets the order of priority of responses */

        if (userManager.needsToVote(sessionUser)) {
            log.info("User needs to vote!");
            return UssdOpeningPrompt.VOTE;
        }
        if (userManager.needsToRSVP(sessionUser)) return UssdOpeningPrompt.MTG_RSVP;
        if (userManager.needsToRenameSelf(sessionUser)) return UssdOpeningPrompt.RENAME_SELF;
        if (groupManager.needsToRenameGroup(sessionUser)) return UssdOpeningPrompt.NAME_GROUP;

        return UssdOpeningPrompt.NONE;

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
                    getMessage(HOME_KEY, START_KEY, PROMPT + ".group.token.named", groupToJoin.getGroupName(), sessionUser) :
                    getMessage(HOME_KEY, START_KEY, PROMPT + ".group.token.unnamed", sessionUser);
            returnMenu = welcomeMenu(prompt, sessionUser);
        } else {
            System.out.println("Whoops, couldn't find the code");
            returnMenu = welcomeMenu(getMessage(HOME_KEY, START_KEY, PROMPT + ".unknown.request", sessionUser), sessionUser);
        }

        return returnMenu;

    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private List<Integer> codePassedDigits(String enteredUSSD) {
        List<String> splitCodes = Arrays.asList(enteredUSSD.split("\\*"));
        List<Integer> listOfCodes = new ArrayList<>();

        for (String code : splitCodes) listOfCodes.add(Integer.parseInt(code));

        return listOfCodes;
    }

    private USSDMenu defaultStartMenu(User sessionUser) throws URISyntaxException {

        String welcomeMessage = sessionUser.hasName() ? getMessage(HOME_KEY, START_KEY, PROMPT + "-named", sessionUser.getName(""), sessionUser) :
                    getMessage(HOME_KEY, START_KEY, PROMPT, sessionUser);
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

                final String voteUri = "vote" + EVENTID_URL + voteId + "&response=";
                final String optionMsgKey = VOTE_KEY + "." + OPTION;

                startMenu.setPromptMessage(getMessage(HOME_KEY, START_KEY, PROMPT + "-vote", promptFields, sessionUser));

                startMenu.addMenuOption(voteUri + "yes", getMessage(optionMsgKey + "yes", sessionUser));
                startMenu.addMenuOption(voteUri + "no", getMessage(optionMsgKey + "no", sessionUser));
                startMenu.addMenuOption(voteUri + "maybe", getMessage(optionMsgKey + "abstain", sessionUser));
                break;
            case MTG_RSVP:
                log.info("Asking for rsvp!");
                Event meeting = eventManager.getOutstandingRSVPForUser(sessionUser).get(0);
                String[] meetingDetails = eventManager.populateNotificationFields(meeting);

                // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
                String defaultPrompt = getMessage(HOME_KEY, START_KEY, PROMPT + "-" + keyRsvp, meetingDetails, sessionUser);
                if (defaultPrompt.length() > 120)
                    defaultPrompt = getMessage(HOME_KEY, START_KEY, PROMPT + "-" + keyRsvp + ".short", meetingDetails, sessionUser);

                String optionUri = keyRsvp + EVENTID_URL + meeting.getId();
                startMenu.setPromptMessage(defaultPrompt);
                startMenu.setMenuOptions(new LinkedHashMap<>(optionsYesNo(sessionUser, optionUri, optionUri)));
                break;
            case RENAME_SELF:
                startMenu.setPromptMessage(getMessage(HOME_KEY, START_KEY, PROMPT + "-rename", sessionUser));
                startMenu.setFreeText(true);
                startMenu.setNextURI(keyRenameStart);
                break;
            case NAME_GROUP:
                startMenu.setPromptMessage(getMessage(HOME_KEY, START_KEY, PROMPT + "-group-rename", sessionUser));
                startMenu.setFreeText(true);
                startMenu.setNextURI(keyGroupNameStart + GROUPID_URL + groupManager.groupToRename(sessionUser));
                break;
            case NONE:
                startMenu = defaultStartMenu(sessionUser);
        }

        return startMenu;

    }

    /*
    Menus to process responses to votes and RSVPs,
     */

    @RequestMapping(value = USSD_BASE + keyRsvp)
    @ResponseBody
    public Request rsvpAndWelcome(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM) Long eventId,
                                  @RequestParam(value="confirmed") String attending) throws URISyntaxException {

        String welcomeKey;
        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        if (attending.equals("yes")) {
            eventLogManagementService.rsvpForEvent(eventId, inputNumber, EventRSVPResponse.YES);
            welcomeKey = String.join(".", Arrays.asList(HOME_KEY, START_KEY, PROMPT, "rsvp-yes"));
        } else {
            eventLogManagementService.rsvpForEvent(eventId, inputNumber, EventRSVPResponse.NO);
            welcomeKey = String.join(".", Arrays.asList(HOME_KEY, START_KEY, PROMPT, "rsvp-no"));
        }

        return menuBuilder(new USSDMenu(getMessage(welcomeKey, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = USSD_BASE + "vote")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=EVENT_PARAM) Long voteId,
                                  @RequestParam(value="response") String response) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        eventLogManagementService.rsvpForEvent(voteId, inputNumber, EventRSVPResponse.fromString(response));

        return menuBuilder(new USSDMenu(getMessage(HOME_KEY, START_KEY, PROMPT, "vote-recorded", sessionUser),
                                        optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = USSD_BASE + keyRenameStart)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=TEXT_PARAM) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (userName.equals("0") || userName.trim().equals("")) {
            welcomeMessage = getMessage(HOME_KEY, START_KEY, PROMPT, sessionUser);
        } else {
            sessionUser.setDisplayName(userName);
            sessionUser = userManager.save(sessionUser);
            welcomeMessage = getMessage(HOME_KEY, START_KEY, PROMPT + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = USSD_BASE + keyGroupNameStart)
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                     @RequestParam(value=GROUP_PARAM) Long groupId,
                                     @RequestParam(value=TEXT_PARAM) String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (groupName.equals("0") || groupName.trim().equals("")) {
            welcomeMessage = getMessage(HOME_KEY, START_KEY, PROMPT, sessionUser);
        } else {
            Group groupToRename = groupManager.loadGroup(groupId);
            groupToRename.setGroupName(groupName);
            groupToRename = groupManager.saveGroup(groupToRename);
            welcomeMessage = getMessage(HOME_KEY, START_KEY, PROMPT + "-group-do", sessionUser.nameToDisplay(), sessionUser);
        }

        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));

    }

    /*
    Helper methods, for group pagination, etc.
     */

    @RequestMapping(value = USSD_BASE + "group_page")
    @ResponseBody
    public Request groupPaginationHelper(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                         @RequestParam(value="prompt") String prompt,
                                         @RequestParam(value="page") Integer pageNumber,
                                         @RequestParam(value="existingUri") String existingUri,
                                         @RequestParam(value="newUri", required=false) String newUri) throws URISyntaxException {

        /*
         todo: this is going to need a way to pass the purpose of the group, or the permission filter (since which groups are
         displayed/paginated will vary a lot
          */
        return menuBuilder(userGroupMenu(userManager.findByInputNumber(inputNumber), prompt, existingUri, newUri, pageNumber));

    }

    @RequestMapping(value = { USSD_BASE + U404, USSD_BASE + LOG_MENUS })
    @ResponseBody
    public Request notBuilt(@RequestParam(value=PHONE_PARAM) String inputNumber) throws URISyntaxException {
        // String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        String errorMessage = messageSource.getMessage("ussd.error", null, new Locale("en"));
        return menuBuilder(new USSDMenu(errorMessage, optionsHomeExit(userManager.findByInputNumber(inputNumber))));
    }

    @RequestMapping(value = USSD_BASE + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value=PHONE_PARAM) String inputNumber) throws URISyntaxException {
        userManager.resetLastUssdMenu(userManager.loadOrSaveUser(inputNumber));
        String exitMessage = getMessage("exit." + PROMPT, userManager.loadOrSaveUser(inputNumber));
        return menuBuilder(new USSDMenu(exitMessage)); // todo: check if methods can handle empty list of options
    }

    @RequestMapping(value = USSD_BASE + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = USSD_BASE + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return tooLongError;
    }


}