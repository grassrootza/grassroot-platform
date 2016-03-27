package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
    private EventLogManagementService eventLogManagementService;

    @Autowired
    private USSDEventUtil eventUtil;

    Logger log = LoggerFactory.getLogger(getClass());
    private static final String path = homePath + "/";
    private static final USSDSection thisSection = USSDSection.HOME;

    private static final String rsvpMenu = "rsvp", renameUserMenu = "rename-start", renameGroupAndStart = "group-start",
            promptGroupRename = "group-rename-prompt", promptConfirmGroupInactive = "group-inactive-confirm";
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

        Long startTime = System.currentTimeMillis();

        USSDMenu openingMenu;
        // first off, check if there is a cache entry for an interrupted menu
        if (userInterrupted(inputNumber)) {
            return menuBuilder(interruptedPrompt(inputNumber));
        }

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
        } else if (userResponseNeeded(sessionUser)) {
            openingMenu = requestUserResponse(sessionUser);
        } else if (firstSession(sessionUser)) {
            sessionUser = userManager.setInitiatedSession(sessionUser);
            openingMenu = askForLanguage(sessionUser);
        } else {
            openingMenu = defaultStartMenu(sessionUser);
        }
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
        return menuBuilder(openingMenu, true);

    }

    private boolean firstSession(User sessionUser) {
        return userManager.isFirstInitiatedSession(sessionUser);
    }

    private USSDMenu askForLanguage(User sessionUser) {

        String prompt = getMessage(thisSection, startMenu, promptKey + "-language", sessionUser);
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

    private USSDMenu interruptedPrompt(String inputNumber) {

        String returnUrl = userManager.getLastUssdMenu(inputNumber);
        log.info("The user was interrupted somewhere ...Here's the URL: " + returnUrl);

        User sessionUser = userManager.findByInputNumber(inputNumber);
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-interrupted", sessionUser));
        promptMenu.addMenuOption(returnUrl, getMessage(thisSection, startMenu, "interrupted.resume", sessionUser));
        promptMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", sessionUser));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        userManager.resetLastUssdMenu(sessionUser);

        return promptMenu;

    }

    private boolean userInterrupted(String inputNumber) {
        return (userManager.getLastUssdMenu(inputNumber) != null);
    }

    private boolean userResponseNeeded(User sessionUser) {
        // todo: optimize this -- it is currently doing 2-4 DB calls on every start menu ... need to consolidate somehow to one
        log.info("Checking if user needs to respond to anything, either a vote or an RSVP ...");
        return userManager.needsToVoteOrRSVP(sessionUser) || sessionUser.needsToRenameSelf(5)
            || (userManager.fetchGroupUserMustRename(sessionUser) != null);
    }

    private USSDResponseTypes neededResponse(User sessionUser) {

        /* Note: the sequence in which these are checked and returned sets the order of priority of responses */
        /* Note: this involves around four DB pings on first menu -- extremely expensive -- need to consolidate somehow */

        if (userManager.needsToVote(sessionUser)) {
            log.info("User needs to vote!");
            return USSDResponseTypes.VOTE;
        }
        if (userManager.needsToRSVP(sessionUser)) return USSDResponseTypes.MTG_RSVP;
        if (userManager.needsToRenameSelf(sessionUser)) return USSDResponseTypes.RENAME_SELF;
        if (userManager.fetchGroupUserMustRename(sessionUser) != null) return USSDResponseTypes.NAME_GROUP;

        return USSDResponseTypes.NONE;

    }

    private USSDMenu processTrailingDigits(String trailingDigits, User sessionUser) throws URISyntaxException {

        USSDMenu returnMenu;

        // todo: a switch logic for token ranges

        System.out.println("Processing trailing digits ..." + trailingDigits);

        Group groupToJoin = groupBroker.findGroupFromJoinCode(trailingDigits);
        if (groupToJoin != null) {
            log.info("Found a token with these trailing digits ...");
            // todo: remove "findBy" above and consolidate into the service call (which throws the 'cant find error'
            groupBroker.addMemberViaJoinCode(sessionUser.getUid(), groupToJoin.getUid(), trailingDigits);

            String prompt = (groupToJoin.hasName()) ?
                    getMessage(thisSection, startMenu, promptKey + ".group.token.named", groupToJoin.getGroupName(), sessionUser) :
                    getMessage(thisSection, startMenu, promptKey + ".group.token.unnamed", sessionUser);
            returnMenu = welcomeMenu(prompt, sessionUser);
        } else {
            log.info("Whoops, couldn't find the code");
            returnMenu = welcomeMenu(getMessage(thisSection, startMenu, promptKey + ".unknown.request", sessionUser), sessionUser);
        }

        return returnMenu;

    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private USSDMenu defaultStartMenu(User sessionUser) throws URISyntaxException {

        String welcomeMessage = sessionUser.hasName() ? getMessage(thisSection, startMenu, promptKey + "-named", sessionUser.getName(""), sessionUser) :
                    getMessage(thisSection, startMenu, promptKey, sessionUser);
        return welcomeMenu(welcomeMessage, sessionUser);

    }

    private USSDMenu requestUserResponse(User sessionUser) throws URISyntaxException {

        USSDMenu openingMenu = new USSDMenu();

        switch (neededResponse(sessionUser)) {
            case VOTE:
                openingMenu = assembleVoteMenu(sessionUser);
                break;
            case MTG_RSVP:
                openingMenu = assembleRsvpMenu(sessionUser);
                break;
            case RENAME_SELF:
                openingMenu.setPromptMessage(getMessage(thisSection, USSDController.startMenu, promptKey + "-rename", sessionUser));
                openingMenu.setFreeText(true);
                openingMenu.setNextURI(renameUserMenu);
                break;
            case NAME_GROUP:
                Group group = userManager.fetchGroupUserMustRename(sessionUser);
                openingMenu = (groupBroker.isDeactivationAvailable(sessionUser, group, true)) ?
                        renameGroupAllowInactive(sessionUser, group.getUid(), dateFormat.format(group.getCreatedDateTime().toLocalDateTime())) :
                        renameGroupNoInactiveOption(sessionUser, group.getUid(), dateFormat.format(group.getCreatedDateTime().toLocalDateTime()));
                break;
            case NONE:
                openingMenu = defaultStartMenu(sessionUser);
                break;
        }

        return openingMenu;

    }

    /*
    Section of helper methods for opening menu response handling
     */

    private USSDMenu assembleVoteMenu(User sessionUser) {
        log.info("Asking for a vote ... from user " + sessionUser);
        Long voteId = eventManager.getNextOutstandingVote(sessionUser);
        Vote vote = (Vote) eventManager.loadEvent(voteId);

        final String[] promptFields = new String[]{ vote.getAppliesToGroup().getName(""),
                vote.getCreatedByUser().nameToDisplay(),
                vote.getName()};

        final String voteUri = "vote" + eventIdUrlSuffix + voteId + "&response=";
        final String optionMsgKey = voteKey + "." + optionsKey;

        USSDMenu openingMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-vote", promptFields, sessionUser));

        openingMenu.addMenuOption(voteUri + "yes", getMessage(optionMsgKey + "yes", sessionUser));
        openingMenu.addMenuOption(voteUri + "no", getMessage(optionMsgKey + "no", sessionUser));
        openingMenu.addMenuOption(voteUri + "maybe", getMessage(optionMsgKey + "abstain", sessionUser));

        return openingMenu;
    }

    private USSDMenu assembleRsvpMenu(User sessionUser) {
        log.info("Asking for rsvp!");
        Event meeting = eventManager.getOutstandingRSVPForUser(sessionUser).get(0);

        String[] meetingDetails = new String[] { meeting.getAppliesToGroup().getName(""),
                meeting.getCreatedByUser().nameToDisplay(),
                meeting.getName(),
                meeting.getEventStartDateTime().toLocalDateTime().format(dateTimeFormat) };

        // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
        String defaultPrompt = getMessage(thisSection, USSDController.startMenu, promptKey + "-" + rsvpMenu, meetingDetails, sessionUser);
        if (defaultPrompt.length() > 120)
            defaultPrompt = getMessage(thisSection, USSDController.startMenu, promptKey + "-" + rsvpMenu + ".short", meetingDetails, sessionUser);

        String optionUri = rsvpMenu + eventIdUrlSuffix + meeting.getId();
        USSDMenu openingMenu = new USSDMenu(defaultPrompt);
        openingMenu.setMenuOptions(new LinkedHashMap<>(optionsYesNo(sessionUser, optionUri, optionUri)));
        return openingMenu;
    }

    private USSDMenu renameGroupNoInactiveOption(User user, String groupUid, String dateCreated) {
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-group-rename", dateCreated, user));
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(renameGroupAndStart + groupUidUrlSuffix + groupUid);
        return thisMenu;
    }

    private USSDMenu renameGroupAllowInactive(User user, String groupUid, String dateCreated) {
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-group-options", dateCreated, user));
        thisMenu.setFreeText(false);

        thisMenu.addMenuOption(promptGroupRename + groupUidUrlSuffix + groupUid,
                               getMessage(thisSection, startMenu, "group.options.rename", user));
        thisMenu.addMenuOption(promptConfirmGroupInactive + groupUidUrlSuffix + groupUid,
                               getMessage(thisSection, startMenu, "group.options.inactive", user));
        thisMenu.addMenuOption(groupMenus + "merge" + groupUidUrlSuffix + groupUid,
                               getMessage(thisSection, startMenu, "group.options.merge", user));
        thisMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", user));

        return thisMenu;
    }

    /*
    Menus to process responses to votes and RSVPs,
     */

    @RequestMapping(value = path + rsvpMenu)
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

        return menuBuilder(new USSDMenu(getMessage(thisSection, startMenu, promptKey, "vote-recorded", sessionUser),
                                        optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = path + renameUserMenu)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(userName) || "".equals(userName.trim())) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, sessionUser);
        } else {
            sessionUser = userManager.setDisplayName(sessionUser, userName);
            welcomeMessage = getMessage(thisSection, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = path + renameGroupAndStart)
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value= phoneNumber) String inputNumber,
                                     @RequestParam(value= groupUidParam) String groupUid,
                                     @RequestParam(value= userInputParam) String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (groupName.equals("0") || groupName.trim().equals("")) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, sessionUser);
        } else {
            groupBroker.updateName(sessionUser.getUid(), groupUid, groupName);
            welcomeMessage = getMessage(thisSection, startMenu, promptKey + "-group-do", sessionUser.nameToDisplay(), sessionUser);
        }

        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));

    }

    @RequestMapping(value = path + promptGroupRename)
    @ResponseBody
    public Request askForGroupName(@RequestParam(value=phoneNumber) String inputNumber,
                                   @RequestParam(value=groupUidParam) String groupUid) throws URISyntaxException {
        Group group = groupBroker.load(groupUid);
        return menuBuilder(renameGroupNoInactiveOption(userManager.findByInputNumber(inputNumber), groupUid,
                                                       dateFormat.format(group.getCreatedDateTime().toLocalDateTime())));
    }

    @RequestMapping(value = path + promptConfirmGroupInactive)
    @ResponseBody
    public Request confirmGroupInactive(@RequestParam(value=phoneNumber) String inputNumber,
                                        @RequestParam(value=groupUidParam) String groupUid) throws URISyntaxException {
        // todo: another round of checks that this should be allowed
        User user = userManager.findByInputNumber(inputNumber);
        Group group = groupBroker.load(groupUid);
        String sizeOfGroup = "" + (groupManager.getGroupSize(group.getId(), false) - 1); // subtracting the group creator
        String optionsPrefix = thisSection.toKey() + "group.inactive." + optionsKey;

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, "group", "inactive." + promptKey, sizeOfGroup, user));
        thisMenu.addMenuOption(promptConfirmGroupInactive + doSuffix + groupUidUrlSuffix + groupUid,
                               getMessage(optionsPrefix + "confirm", user));
        thisMenu.addMenuOption(groupMenus + "merge" + groupUidUrlSuffix + groupUid,
                               getMessage(optionsPrefix + "merge", user));
        thisMenu.addMenuOption(startMenu + "_force", getMessage(optionsPrefix + "cancel", user));

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = path + promptConfirmGroupInactive + doSuffix)
    @ResponseBody
    public Request setGroupInactiveAndStart(@RequestParam(value=phoneNumber) String inputNumber,
                                            @RequestParam(value=groupUidParam) String groupUid) throws URISyntaxException {
        // todo: permission checks
        User sessionUser = userManager.findByInputNumber(inputNumber);
        log.info("At the request of user: " + sessionUser + ", we are setting inactive this group ... " + groupUid);
        groupBroker.deactivate(sessionUser.getUid(), groupUid, true);
        String welcomeMessage = getMessage(thisSection, "group", "inactive." + promptKey + ".done", sessionUser);
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    /*
    Helper methods, for group pagination, event pagination, etc.
     */

    // todo: make sure this works with permissions ... by passing in the section
    @RequestMapping(value = path + "group_page")
    @ResponseBody
    public Request groupPaginationHelper(@RequestParam(value= phoneNumber) String inputNumber,
                                         @RequestParam(value="prompt") String prompt,
                                         @RequestParam(value="page") Integer pageNumber,
                                         @RequestParam(value="existingUri") String existingUri,
                                         @RequestParam(value="newUri", required=false) String newUri) throws URISyntaxException {

        /*
         todo: likely need to add permission checking to the list of parameters, but for now just saying "false"
          */
        return menuBuilder(ussdGroupUtil.userGroupMenuPaginated(userManager.findByInputNumber(inputNumber), prompt, existingUri,
                                       newUri, pageNumber, null));

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
        User user = userManager.findByInputNumber(inputNumber, null);
        String exitMessage = getMessage("exit." + promptKey, user);
        return menuBuilder(new USSDMenu(exitMessage));
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