package za.org.grassroot.webapp.controller.ussd;

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
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.AbstractMap.SimpleEntry;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.enums.USSDSection.*;

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

    @Autowired
    private LogBookBroker logBookBroker;

    private static final Logger log = LoggerFactory.getLogger(USSDHomeController.class);

    private static final String path = homePath;
    private static final USSDSection thisSection = HOME;

    private static final String rsvpMenu = "rsvp",
            renameUserMenu = "rename-start",
            renameGroupAndStart = "group-start",
            promptGroupRename = "group-rename-prompt",
            promptConfirmGroupInactive = "group-inactive-confirm";

    private static final int hashPosition = Integer.valueOf(System.getenv("USSD_CODE_LENGTH"));

    private static final String openingMenuKey = String.join(".", Arrays.asList(homeKey, startMenu, optionsKey));

    private static final Map<USSDSection, String[]> openingMenuOptions = Collections.unmodifiableMap(Stream.of(
            new SimpleEntry<>(MEETINGS, new String[] { meetingMenus + startMenu, openingMenuKey + mtgKey}),
            new SimpleEntry<>(VOTES, new String[] { voteMenus + startMenu, openingMenuKey + voteKey}),
            new SimpleEntry<>(LOGBOOK, new String[] { logMenus + startMenu, openingMenuKey + logKey}),
            new SimpleEntry<>(GROUP_MANAGER, new String[] { groupMenus + startMenu, openingMenuKey + groupKey}),
            new SimpleEntry<>(USER_PROFILE, new String[] { userMenus + startMenu, openingMenuKey + userKey})).
            collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue())));

    private static final List<USSDSection> openingSequenceWithGroups = Arrays.asList(
            MEETINGS, VOTES, LOGBOOK, GROUP_MANAGER, USER_PROFILE);
    private static final List<USSDSection> openingSequenceWithoutGroups = Arrays.asList(
            USER_PROFILE, GROUP_MANAGER, MEETINGS, VOTES, LOGBOOK);

    private static final long daysPastLogbooks = 5; // just check for logbooks that crossed deadline in last ~week

    public USSDMenu welcomeMenu(String opening, User user) {

        USSDMenu homeMenu = new USSDMenu(opening);
        List<USSDSection> menuSequence = userManager.isPartOfActiveGroups(user) ?
                openingSequenceWithGroups : openingSequenceWithoutGroups;

        for (USSDSection section : menuSequence) {
            homeMenu.addMenuOption(openingMenuOptions.get(section)[0],
                                   getMessage(openingMenuOptions.get(section)[1], user));
        }

        return homeMenu;
    }

    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value= phoneNumber) String inputNumber,
                             @RequestParam(value= userInputParam, required=false) String enteredUSSD) throws URISyntaxException {

        Long startTime = System.currentTimeMillis();

        USSDMenu openingMenu;

        // first off, check if there is a cache entry for an interrupted menu, and if so, just assemble without touching DB
        if (userInterrupted(inputNumber)) {
            return menuBuilder(interruptedPrompt(inputNumber));
        }

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

        /*
        Adding some complex logic here to check for one of these things:
        (1) The user has appended a joining code, so we need to add them to a group
        (2) The user has an outstanding RSVP request for a meeting
        (3) The user has not named themselves, or a prior group
         */

        if (codeHasTrailingDigits(enteredUSSD)) {
            String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
            openingMenu = processTrailingDigits(trailingDigits, sessionUser);
        } else {
            if (!sessionUser.isHasInitiatedSession()) userManager.setInitiatedSession(sessionUser);
            USSDResponseTypes neededResponse = neededResponse(sessionUser);

            if (!neededResponse.equals(USSDResponseTypes.NONE)) {
                openingMenu = requestUserResponse(sessionUser, neededResponse);
            } else {
                openingMenu = defaultStartMenu(sessionUser);
            }
        }
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
        return menuBuilder(openingMenu, true);

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

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-interrupted", user));
        promptMenu.addMenuOption(returnUrl, getMessage(thisSection, startMenu, "interrupted.resume", user));
        promptMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", user));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        userLogger.recordUssdInterruption(user.getUid(), returnUrl);
        cacheManager.clearUssdMenuForUser(inputNumber);

        return promptMenu;

    }

    private boolean userInterrupted(String inputNumber) {
        return (userManager.getLastUssdMenu(inputNumber) != null);
    }

    /* Note: the sequence in which these are checked and returned sets the order of priority of responses */
    /* Note: this involves around four DB pings on first menu -- somewhat expensive -- need to consolidate somehow */

    private USSDResponseTypes neededResponse(User user) {

        if (userManager.needsToVote(user)) return USSDResponseTypes.VOTE;
        if (userManager.needsToRSVP(user)) return USSDResponseTypes.MTG_RSVP;
        if (userManager.hasIncompleteLogBooks(user.getUid(), daysPastLogbooks)) return USSDResponseTypes.RESPOND_LOGBOOK;
        if (userManager.needsToRenameSelf(user)) return USSDResponseTypes.RENAME_SELF;
        if (userManager.fetchGroupUserMustRename(user) != null) return USSDResponseTypes.NAME_GROUP;

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

    private USSDMenu requestUserResponse(User user, USSDResponseTypes response) throws URISyntaxException {

        USSDMenu openingMenu = new USSDMenu();

        switch (response) {
            case VOTE:
                openingMenu = assembleVoteMenu(user);
                break;
            case MTG_RSVP:
                openingMenu = assembleRsvpMenu(user);
                break;
            case RESPOND_LOGBOOK:
                openingMenu = assembleLogBookMenu(user);
                break;
            case RENAME_SELF:
                openingMenu.setPromptMessage(getMessage(thisSection, USSDController.startMenu, promptKey + "-rename", user));
                openingMenu.setFreeText(true);
                openingMenu.setNextURI(renameUserMenu);
                break;
            case NAME_GROUP:
                Group group = userManager.fetchGroupUserMustRename(user);
                openingMenu = (groupBroker.isDeactivationAvailable(user, group, true)) ?
                        renameGroupAllowInactive(user, group.getUid(), dateFormat.format(group.getCreatedDateTime().toLocalDateTime())) :
                        renameGroupNoInactiveOption(user, group.getUid(), dateFormat.format(group.getCreatedDateTime().toLocalDateTime()));
                break;
            case NONE:
                openingMenu = defaultStartMenu(user);
                break;
        }

        return openingMenu;

    }

    /*
    Section of helper methods for opening menu response handling
     */

    private USSDMenu assembleVoteMenu(User sessionUser) {
        log.info("Asking for a vote ... from user " + sessionUser);
        Vote vote = (Vote) eventManager.getOutstandingVotesForUser(sessionUser).get(0);

        final String[] promptFields = new String[]{ vote.getAncestorGroup().getName(""),
                vote.getCreatedByUser().nameToDisplay(),
                vote.getName()};

        final String voteUri = "vote" + entityUidUrlSuffix + vote.getUid() + "&response=";
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

        String[] meetingDetails = new String[] { meeting.getAncestorGroup().getName(""),
                meeting.getCreatedByUser().nameToDisplay(),
                meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(dateTimeFormat) };

        // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
        String defaultPrompt = getMessage(thisSection, startMenu, promptKey + "-" + rsvpMenu, meetingDetails, sessionUser);
        if (defaultPrompt.length() > 120)
            defaultPrompt = getMessage(thisSection, startMenu, promptKey + "-" + rsvpMenu + ".short", meetingDetails, sessionUser);

        String optionUri = rsvpMenu + entityUidUrlSuffix + meeting.getUid();
        USSDMenu openingMenu = new USSDMenu(defaultPrompt);
        openingMenu.setMenuOptions(new LinkedHashMap<>(optionsYesNo(sessionUser, optionUri, optionUri)));
        return openingMenu;
    }

    private USSDMenu assembleLogBookMenu(User user) {
        log.info("User has an incomplete logbook needing a response!");
        LogBook logBook = logBookBroker.fetchLogBookForUserResponse(user.getUid(), daysPastLogbooks, false);

        if (logBook == null) {
            log.info("For some reason, should have found a logbook to respond to, but didnt, user = {}", user);
            return welcomeMenu(getMessage(thisSection, startMenu, promptKey, user), user);
        } else {
            String[] promptFields = new String[]{
                    logBook.getParent().getName(),
                    logBook.getMessage(),
                    logBook.getActionByDateAtSAST().format(dateFormat) };
            String prompt = getMessage(thisSection, startMenu, promptKey + ".logbook", promptFields, user);
            String completeUri = "log-complete" + entityUidUrlSuffix + logBook.getUid();
            USSDMenu menu = new USSDMenu(prompt);
            menu.addMenuOptions(new LinkedHashMap<>(optionsYesNo(user, completeUri, completeUri)));
            menu.addMenuOption(completeUri + "&confirmed=unknown",
                               getMessage(thisSection, startMenu, logKey + "." + optionsKey + "unknown", user));
            return menu;
        }
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
                                  @RequestParam(value= entityUidParam) String meetingUid,
                                  @RequestParam(value= yesOrNoParam) String attending) throws URISyntaxException {

        String welcomeKey;
        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);

        if (attending.equals("yes")) {
            eventLogManagementService.rsvpForEvent(meeting.getId(), inputNumber, EventRSVPResponse.YES);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-yes"));
        } else {
            eventLogManagementService.rsvpForEvent(meeting.getId(), inputNumber, EventRSVPResponse.NO);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-no"));
        }

        return menuBuilder(new USSDMenu(getMessage(welcomeKey, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = path + "vote")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= entityUidParam) String voteUid,
                                  @RequestParam(value="response") String response) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Vote vote = (Vote) eventBroker.load(voteUid);

        // todo: switch this to uid
        eventLogManagementService.rsvpForEvent(vote.getId(), inputNumber, EventRSVPResponse.fromString(response));

        String prompt = getMessage(thisSection, startMenu, promptKey + ".vote-recorded", sessionUser);
        return menuBuilder(new USSDMenu(prompt, optionsHomeExit(sessionUser)));
    }

    @RequestMapping(value = path + "log-complete")
    @ResponseBody
    public Request logBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = entityUidParam) String logBookUid,
                                @RequestParam(value = yesOrNoParam) String response) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);

        String prompt;
        if (response.equalsIgnoreCase("yes")) {
            boolean stateChanged = logBookBroker.confirmCompletion(user.getUid(), logBookUid, LocalDateTime.now());
            prompt = stateChanged ? getMessage(thisSection, startMenu, promptKey + ".logbook-completed", user) :
                    getMessage(thisSection, startMenu, promptKey + ".logbook-unchanged", user);
        } else {
            prompt = getMessage(thisSection, startMenu, promptKey + ".logbook-marked-no", user);
        }

        return menuBuilder(welcomeMenu(prompt, user));
    }

    @RequestMapping(value = path + renameUserMenu)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value= phoneNumber) String inputNumber,
                                  @RequestParam(value= userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(userName) || "".equals(userName.trim())) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, sessionUser);
            userLogger.recordUserLog(sessionUser.getUid(), UserLogType.USER_SKIPPED_NAME, "");
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

        User user = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if (groupName.equals("0") || groupName.trim().equals("")) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, user);
            userLogger.recordUserLog(user.getUid(), UserLogType.USER_SKIPPED_NAME, groupUid);
        } else {
            groupBroker.updateName(user.getUid(), groupUid, groupName);
            welcomeMessage = getMessage(thisSection, startMenu, promptKey + "-group-do", user.nameToDisplay(), user);
        }

        return menuBuilder(welcomeMenu(welcomeMessage, user));

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
        String sizeOfGroup = "" + (group.getMembers().size() - 1); // subtracting the group creator
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
                                         @RequestParam(value = "newMenu", required = false) String menuForNew,
                                         @RequestParam(value = "newOption", required = false) String optionForNew,
                                         @RequestParam(value = "page") Integer pageNumber,
                                         @RequestParam(value = "nextUrl") String nextUrl,
                                         @RequestParam(value = "pastPresentBoth") Integer pastPresentBoth,
                                         @RequestParam(value = "includeGroupName") boolean includeGroupName) throws URISyntaxException {
        // toto: error handling on the section
        return menuBuilder(eventUtil.listPaginatedEvents(
                userManager.findByInputNumber(inputNumber), fromString(section),
                prompt, nextUrl, (menuForNew != null), menuForNew, optionForNew, includeGroupName, pastPresentBoth, pageNumber));
    }

    @RequestMapping(value = path + U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {
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