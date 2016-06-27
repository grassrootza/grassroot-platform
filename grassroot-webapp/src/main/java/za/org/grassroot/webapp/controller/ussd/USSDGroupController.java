package za.org.grassroot.webapp.controller.ussd;

import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Set;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * @author luke on 2015/08/14.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDGroupController extends USSDController {

    @Autowired
    private PermissionBroker permissionBroker;

    private static final Logger log = LoggerFactory.getLogger(USSDGroupController.class);

    private static final String
            existingGroupMenu = "menu",
            advancedGroupMenu = "advanced",
            createGroupMenu = "create",
            closeGroupToken = "create-token",
            createGroupAddNumbers = "add-numbers",
            listGroupMembers = "list",
            renameGroupPrompt = "rename",
            addMemberPrompt = "addnumber", // probably should rename this to prevent confusion w/ above
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token",
            mergeGroupMenu = "merge",
            inactiveMenu = "inactive",
            validity = "validity.";

    private static final String groupPath = homePath + groupMenus;
    private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

    private static final String groupUidParam = "groupUid";

    /*
    First menu: display a list of groups, with the option to create a new one
     */
    @RequestMapping(value = groupPath + startMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                             @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        // in case went "back" from menu in middle of create group
        User sessionUser = (interrupted) ? userManager.findByInputNumber(inputNumber, null) :
                userManager.findByInputNumber(inputNumber);
        return menuBuilder(ussdGroupUtil.askForGroupAllowCreateNew(sessionUser, USSDSection.GROUP_MANAGER, existingGroupMenu,
                createGroupMenu, createGroupMenu + doSuffix, null));
    }

    /*
    Second menu: once the user has selected a group, give them options to name, create join code, add a member, or unsub themselves
    Major todo: check what permissions the user has and only display options that they can do
     */

    @RequestMapping(value = groupPath + existingGroupMenu)
    @ResponseBody
    public Request groupMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        return (groupUid == null || groupUid.equals("")) ? createPrompt(inputNumber) :
                menuBuilder(ussdGroupUtil.existingGroupMenu(userManager.findByInputNumber(inputNumber), groupUid, false));
    }

    @RequestMapping(value = groupPath + advancedGroupMenu)
    @ResponseBody
    public Request advancedGroupMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        return menuBuilder(ussdGroupUtil.advancedGroupOptionsMenu(userManager.findByInputNumber(inputNumber), groupUid));
    }

    /*
    The user is creating a group. First, ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupMenu)
    @ResponseBody
    public Request createPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + createGroupMenu);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(sessionUser, thisSection, createGroupMenu + doSuffix));
    }

    /*
    The user has given a name, now ask whether to enter numbers or just go straight to a joining code
     */

    @RequestMapping(value = groupPath + createGroupMenu + doSuffix)
    @ResponseBody
    public Request createGroupWithName(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                       @RequestParam(value = userInputParam, required = true) String groupName,
                                       @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                       @RequestParam(value = groupUidParam, required = false) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        Group createdGroup;
        USSDMenu menu;
        if (!isValidGroupName(groupName)) {
            menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, thisSection, createGroupMenu + doSuffix);
        } else {
            if (interrupted) {
                createdGroup = groupBroker.load(groupUid);
            } else {
                Long startTime = System.currentTimeMillis();
                MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
                createdGroup = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(creator),
                        GroupPermissionTemplate.DEFAULT_GROUP, null, null);
                groupBroker.openJoinToken(user.getUid(), createdGroup.getUid(), false, null);
                Long endTime = System.currentTimeMillis();
                log.info(String.format("Group has been created ... time taken ... %d msecs", endTime - startTime));
            }

            String joiningCode = "*134*1994*" + createdGroup.getGroupTokenCode() + "#";
            userManager.setLastUssdMenu(user, saveGroupMenuWithInput(createGroupMenu + doSuffix, createdGroup.getUid(), groupName));
            menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey,
                    new String[]{groupName, joiningCode}, user));

            menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, createdGroup.getUid()),
                    getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "numbers", user));
            menu.addMenuOption(groupMenuWithId(closeGroupToken, createdGroup.getUid()),
                    getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "token", user));

        }
        return menuBuilder(menu);
    }

    /*
    Create an indefinite token and then give the options to go home or add some numbers
     */
    @RequestMapping(value = groupPath + closeGroupToken)
    @ResponseBody
    public Request createGroupCreateIndefiniteToken(@RequestParam(phoneNumber) String inputNumber,
                                                    @RequestParam(groupUidParam) String groupUid,
                                                    @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        // todo: various forms of error and permission checking
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(closeGroupToken, groupUid));
        Group group = groupBroker.load(groupUid);

        /*  the only case of coming here and the group has a code is after interruption or after 'add numbers' via create
            hence there is no need to check if the code expiry date has passed (by definition, the code is valid) */

        groupBroker.closeJoinToken(user.getUid(), group.getUid());

        USSDMenu menu = new USSDMenu(getMessage(thisSection, closeGroupToken, promptKey, user));

        menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupUid),
                getMessage(thisSection, closeGroupToken, optionsKey + "add", user));
        menu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                getMessage(thisSection, closeGroupToken, optionsKey + "home", user));
        menu.addMenuOption("exit", getMessage("exit.option", user));

        return menuBuilder(menu);
    }

    /*
    todo: check permissions on user (more simply, that they created the group) to avoid hand hacking through Url
    Generates a loop, where it keeps asking for additional numbers and adds them to group over and over, until the
    user enters "0", when wrap up, and ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupAddNumbers)
    @ResponseBody
    public Request createGroupAddNumbersOpeningPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddNumbers, groupUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, createGroupAddNumbers, promptKey, user),
                groupMenuWithId(createGroupAddNumbers + doSuffix, groupUid)));
    }

    @RequestMapping(value = groupPath + createGroupAddNumbers + doSuffix)
    @ResponseBody
    public Request addNumbersToNewlyCreatedGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                                 @RequestParam(value = groupUidParam, required = true) String groupUid,
                                                 @RequestParam(value = userInputParam, required = true) String userInput,
                                                 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        USSDMenu thisMenu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithInput(createGroupAddNumbers + doSuffix, groupUid, userResponse));

        if (!userResponse.trim().equals("0")) {
            thisMenu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
                    userResponse, createGroupAddNumbers + doSuffix);
        } else { // stop asking for numbers, reset interrupt prompt and give options to go back
            Group group = groupBroker.load(groupUid);
            String prompt = (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime().toInstant())) ?
                    getMessage(thisSection, createGroupAddNumbers, promptKey + ".done.token", group.getGroupTokenCode(), user) :
                    getMessage(thisSection, createGroupAddNumbers, promptKey + ".done", user);
            thisMenu = new USSDMenu(prompt);
            thisMenu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                    getMessage(thisSection, createGroupAddNumbers, optionsKey + "home", user));
            thisMenu.addMenuOption(groupMenus + closeGroupToken + groupUidUrlSuffix + groupUid,
                    getMessage(thisSection, createGroupAddNumbers, optionsKey + "token", user));
            thisMenu.addMenuOption("exit", getMessage("exit.option", user));
        }

        return menuBuilder(thisMenu);
    }

    /*
    Menu options to rename a group, either existing, or if a new group, to give it a name
    Major todo: integrate permissions for existing groups
     */

    @RequestMapping(value = groupPath + renameGroupPrompt)
    @ResponseBody
    public Request renamePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(renameGroupPrompt, groupUid));
        Group groupToRename = groupBroker.load(groupUid);

        log.info("renaming group with this name : " + groupToRename.getGroupName());

        String promptMessage = (groupToRename.getGroupName().trim().length() == 0) ?
                getMessage(thisSection, renameGroupPrompt, promptKey + "1", sessionUser) :
                getMessage(thisSection, renameGroupPrompt, promptKey + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, groupMenuWithId(renameGroupPrompt + doSuffix, groupUid)));

    }

    @RequestMapping(value = groupPath + renameGroupPrompt + doSuffix)
    @ResponseBody
    public Request renameGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = userInputParam, required = true) String newName,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        if (!interrupted) groupBroker.updateName(user.getUid(), groupUid, newName);
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, renameGroupPrompt + doSuffix, promptKey, newName, user),
                optionsHomeExit(user));

        return menuBuilder(thisMenu);
    }

    /*
    SECTION: MENUS FOR GROUP TOKENS
     */

    @RequestMapping(value = groupPath + groupTokenMenu)
    @ResponseBody
    public Request groupToken(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        // todo: validate that this user has permission to create a token for this group

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu, groupUid));
        USSDMenu tokenMenu;
        Group sessionGroup = groupBroker.load(groupUid);

        if (sessionGroup.hasValidGroupTokenCode()) {
            String tokenCode = sessionGroup.getGroupTokenCode();
            boolean indefiniteToken = sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongTimestamp());
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".exists", tokenCode, sessionUser));
            if (!indefiniteToken) tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupUid),
                    getMessage(thisSection, groupTokenMenu, optionsKey + "extend", sessionUser));
            tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-close", groupUid),
                    getMessage(thisSection, groupTokenMenu, optionsKey + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey, sessionUser));
            String daysUrl = groupMenus + groupTokenMenu + doSuffix + groupUidUrlSuffix + groupUid + "&days=";
            tokenMenu.addMenuOption(daysUrl + "0", getMessage(thisSection, groupTokenMenu, validity + "permanent", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "1", getMessage(thisSection, groupTokenMenu, validity + "day", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "7", getMessage(thisSection, groupTokenMenu, validity + "week", sessionUser));
        }
        return menuBuilder(tokenMenu);
    }

    @RequestMapping(value = groupPath + groupTokenMenu + doSuffix)
    @ResponseBody
    public Request createToken(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam) String groupUid,
                               @RequestParam(value = "days") Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */
        User user = userManager.findByInputNumber(inputNumber, null);
        String token = (daysValid == 0) ? groupBroker.openJoinToken(user.getUid(), groupUid, false, null) :
                groupBroker.openJoinToken(user.getUid(), groupUid, true, LocalDateTime.now().plusDays(daysValid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, groupTokenMenu, "created", token, user),
                optionsHomeExit(user)));
    }

    @RequestMapping(value = groupPath + groupTokenMenu + "-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = "days", required = false) Integer daysValid) throws URISyntaxException {

        String urlToSave = (daysValid == null) ? saveGroupMenu(groupTokenMenu + "-extend", groupUid) : null;
        User sessionUser = userManager.findByInputNumber(inputNumber, urlToSave);
        Group sessionGroup = groupBroker.load(groupUid);
        USSDMenu promptMenu;

        if (daysValid == null) {
            // means we are still asking for the number of days to extend
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend", sessionUser));
            promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupUidUrlSuffix + groupUid,
                    getMessage(thisSection, groupTokenMenu, optionsKey + "extend.none", sessionUser));
            String daySuffix = getMessage(thisSection, groupTokenMenu, optionsKey + "days", sessionUser);
            for (int i = 1; i <= 3; i++)
                promptMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupUid) + "&days=" + i, i + daySuffix);
        } else {
            // we have been passed a number of days to extend
            LocalDateTime newExpiry = LocalDateTime.now().plusDays(daysValid);
            groupBroker.openJoinToken(sessionUser.getUid(), sessionGroup.getUid(), true, newExpiry);
            String date = newExpiry.format(dateFormat);
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend.done", date, sessionUser),
                    optionsHomeExit(sessionUser));
        }
        return menuBuilder(promptMenu);
    }


    @RequestMapping(value = groupPath + groupTokenMenu + "-close")
    @ResponseBody
    public Request extendToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = yesOrNoParam, required = false) String confirmed) throws URISyntaxException {

        // todo: check the token and group match, and that the user has token admin rights
        User user;
        USSDMenu thisMenu;

        if (confirmed == null) {
            user = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu + "-close", groupUid));
            String beginUri = groupMenus + groupTokenMenu, endUri = groupUidUrlSuffix + groupUid;
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close", user),
                    optionsYesNo(user, beginUri + "-close" + endUri));
        } else if ("yes".equals(confirmed)) {
            user = userManager.findByInputNumber(inputNumber, null);
            // todo: error handling here (bad token, etc., also, security)
            groupBroker.closeJoinToken(user.getUid(), groupUid);
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close-done", user),
                    optionsHomeExit(user));
        } else {
            user = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu("Okay, cancelled", optionsHomeExit(user));
        }

        return menuBuilder(thisMenu);

    }

    /**
     * SECTION: MENUS TO ADD MEMBERS, UNSUBSCRIBE, AND LIST MEMBERS
     * todo: decide if want some kind of "list members" function (maybe delivered via SMS)?
     */

    @RequestMapping(value = groupPath + addMemberPrompt)
    @ResponseBody
    public Request addNumberInput(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        Group group = groupBroker.load(groupUid);
        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(addMemberPrompt, groupUid));
        String promptMessage;

        if (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime().toInstant())) {
            promptMessage = getMessage(thisSection, addMemberPrompt, promptKey + ".token", group.getGroupTokenCode(), sessionUser);
        } else {
            promptMessage = getMessage(thisSection, addMemberPrompt, promptKey, sessionUser);
        }

        return menuBuilder(new USSDMenu(promptMessage, groupMenus + addMemberPrompt + doSuffix + groupUidUrlSuffix + groupUid));

    }

    @RequestMapping(value = groupPath + addMemberPrompt + doSuffix)
    @ResponseBody
    public Request addNumberToGroup(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = groupUidParam) String groupUid,
                                    @RequestParam(value = userInputParam) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        USSDMenu thisMenu = (numberToAdd.trim().equals("0")) ?
                new USSDMenu(getMessage(thisSection, addMemberPrompt + doSuffix, promptKey, sessionUser),
                        optionsHomeExit(sessionUser)) :
                ussdGroupUtil.addNumbersToExistingGroup(sessionUser, groupUid, thisSection, numberToAdd, addMemberPrompt + doSuffix);

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = groupPath + unsubscribePrompt)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(unsubscribePrompt, groupUid));

        String menuKey = groupKey + "." + unsubscribePrompt + ".";
        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + promptKey, sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(unsubscribePrompt + doSuffix, groupUid),
                getMessage(menuKey + optionsKey + "confirm", sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                getMessage(menuKey + optionsKey + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    // todo: security & permissions so we make sure it is the user themselves doing this
    @RequestMapping(value = groupPath + unsubscribePrompt + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        // todo: add error and exception handling, as well as validation and checking (e.g., if user in group, etc)

        groupBroker.unsubscribeMember(sessionUser.getUid(), groupUid);
        String returnMessage = getMessage(thisSection, unsubscribePrompt + doSuffix, promptKey, sessionUser);
        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));
    }

    /**
     * SECTION: MERGING GROUP MENUS (AND DEACTIVATE)
     */

    @RequestMapping(value = groupPath + mergeGroupMenu)
    @ResponseBody
    public Request selectMergeGroups(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(mergeGroupMenu, groupUid));
        // todo: debug why this is returning inactive groups (service method has active flag)
        Set<Group> mergeCandidates = groupBroker.mergeCandidates(user.getUid(), groupUid);

        if (mergeCandidates == null || mergeCandidates.size() == 0) {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey + ".error", user));
            menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                    getMessage(thisSection, mergeGroupMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey, user));
            menu = ussdGroupUtil.addListOfGroupsToMenu(menu, groupMenus + mergeGroupMenu + "-confirm?firstGroupSelected=" + groupUid,
                    new ArrayList<>(mergeCandidates), user);
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-confirm")
    @ResponseBody
    public Request confirmMerge(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = groupUidParam) String groupUid1,
                                @RequestParam(value = "firstGroupSelected") String firstGroupSelected) throws URISyntaxException {

        // todo: specify which one is smaller
        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithParams(mergeGroupMenu + "-confirm", groupUid1,
                        "&firstGroupSelected=" + firstGroupSelected));
        String[] groupNames = new String[]{groupBroker.load(groupUid1).getName(""),
                groupBroker.load(firstGroupSelected).getName("")};

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-confirm", promptKey, groupNames, user));
        String urlRoot = groupMenus + mergeGroupMenu + doSuffix + "&groupUid1=" + groupUid1 + "&groupUid2=" + firstGroupSelected + "&action=";
        String messageRoot = thisSection.toKey() + mergeGroupMenu + "-confirm." + optionsKey;

        menu.addMenuOption(urlRoot + "inactive", getMessage(messageRoot + "yes.inactive", user));
        menu.addMenuOption(urlRoot + "active", getMessage(messageRoot + "yes.active", user));
        menu.addMenuOption(urlRoot + "new", getMessage(messageRoot + "yes.newgroup", user));
        menu.addMenuOption(groupMenuWithId(mergeGroupMenu, firstGroupSelected), getMessage(messageRoot + "change.second", user));
        menu.addMenuOption(groupMenus + existingGroupMenu, getMessage(messageRoot + "change.both", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-newname")
    @ResponseBody
    public Request nameNewMergedGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                      @RequestParam(value = "groupUid1") String groupUid1,
                                      @RequestParam(value = "groupUid2") String groupUid2) throws URISyntaxException {

        String groupsSuffix = "?groupUid1=" + groupUid1 + "&groupUid2=" + groupUid2;
        User user = userManager.findByInputNumber(inputNumber, groupMenus + mergeGroupMenu + "-newname" + groupsSuffix);
        return menuBuilder(new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-newname", promptKey, user),
                groupMenus + mergeGroupMenu + doSuffix + groupsSuffix));
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + doSuffix)
    @ResponseBody
    public Request mergeDo(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                           @RequestParam(value = groupUidParam + "1", required = true) String firstGroupUid,
                           @RequestParam(value = groupUidParam + "2", required = true) String secondGroupUid,
                           @RequestParam(value = "action", required = false) String action,
                           @RequestParam(value = userInputParam, required = false) String userInput) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null); // resetting return flag
        Group firstGroup = groupBroker.load(firstGroupUid);
        Group secondGroup = groupBroker.load(secondGroupUid);

        Group resultGroup;

        switch (action) {
            case "inactive":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, false, null);
                break;
            case "active":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), true, false, false, null);
                break;
            case "new":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, true, userInput);
                break;
            default:
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, false, null);
                break;
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + doSuffix, promptKey, user));

        menu.addMenuOption(groupMenuWithId(existingGroupMenu, resultGroup.getUid()),
                getMessage(thisSection, mergeGroupMenu + doSuffix, optionsKey + "group", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + inactiveMenu)
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        // todo: check permissions to do this and add exception handling
        User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
        USSDMenu menu = new USSDMenu(getMessage(thisSection, inactiveMenu, promptKey, user));
        menu.addMenuOption(groupMenuWithId(inactiveMenu + doSuffix, groupUid),
                getMessage(thisSection, inactiveMenu, optionsKey + "confirm", user));
        menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                getMessage(thisSection, inactiveMenu, optionsKey + "cancel", user));
        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + inactiveMenu + doSuffix)
    @ResponseBody
    public Request inactiveDo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        // todo: check for user role & permissions (must have all of them)

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;

        // todo: rather make this rely on an exception from services layer and move logic there
        try {
            groupBroker.deactivate(user.getUid(), groupUid, true);
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, promptKey + ".success", user), optionsHomeExit(user));
        } catch (GroupDeactivationNotAvailableException e) {
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, errorPromptKey, user), optionsHomeExit(user));
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + listGroupMembers)
    @ResponseBody
    public Request listGroupMemberSize(@RequestParam String msisdn, @RequestParam String groupUid) throws URISyntaxException {
        final User user = userManager.findByInputNumber(msisdn);
        final Group group = groupBroker.load(groupUid);

        // need to do this here as aren't calling service broker method ...
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        final GroupLog lastLog = groupBroker.getMostRecentLog(group);
        final String lastModified = DateTimeUtil.convertToUserTimeZone(lastLog.getCreatedDateTime(), DateTimeUtil.getSAST())
                .format(DateTimeFormatter.ofPattern("dd-MM"));
        final String lastMessage = lastLog.getGroupLogType().toString();

        final int groupSize = group.getMemberships().size();
        final String[] promptParams = new String[]{String.valueOf(groupSize), lastModified, lastMessage};

        final String prompt = getMessage(thisSection, listGroupMembers, promptKey, promptParams, user);

        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid), getMessage(thisSection, listGroupMembers, optionsKey + "back", user));
        menu.addMenuOption(thisSection.toPath() + startMenu, getMessage(thisSection, listGroupMembers, optionsKey + "back-grp", user));
        menu.addMenuOption(startMenu, getMessage(startMenu, user));

        return menuBuilder(menu);
    }


    private boolean isValidGroupName(String groupName) {
        return groupName.length() > 1;
    }

}