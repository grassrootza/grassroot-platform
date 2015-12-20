package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveGroupMenu;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveGroupMenuWithParams;

/**
 * @author luke on 2015/08/14.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDGroupController extends USSDController {

    /**
     * Starting the group management menu flow here
     */

    private static final Logger log = LoggerFactory.getLogger(USSDGroupController.class);

    private static final String existingGroupMenu = "menu",
            createGroupMenu = "create",
            createGroupAddToken = "create-token",
            createGroupAddNumbers = "add-numbers",
            listGroupMembers = "list",
            renameGroupPrompt = "rename",
            addMemberPrompt = "addnumber", // probably should rename this to prevent confusion w/ above
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token",
            mergeGroupMenu = "merge",
            inactiveMenu = "inactive";

    private static final String groupPath = homePath + groupMenus;

    /*
    First menu: display a list of groups, with the option to create a new one
     */
    @RequestMapping(value = groupPath + startMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                             @RequestParam(value="interrupted", required=false) boolean interrupted) throws URISyntaxException {

        // in case went "back" from menu in middle of create group
        User sessionUser = (interrupted) ? userManager.findByInputNumber(inputNumber, null) : userManager.findByInputNumber(inputNumber);
        return menuBuilder(ussdGroupUtil.askForGroupAllowCreateNew(sessionUser, USSDSection.GROUP_MANAGER,
                                                                   existingGroupMenu, createGroupMenu, null));

    }

    /*
    Second menu: once the user has selected a group, give them options to name, create join code, add a member, or unsub themselves
    Major todo: check what permissions the user has and only display options that they can do
     */

    @RequestMapping(value = groupPath + existingGroupMenu)
    @ResponseBody
    public Request groupMenu(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                             @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        if (groupId == 0) { return createPrompt(inputNumber); }

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(existingGroupMenu, groupId));
        String returnMessage = getMessage(groupKey, existingGroupMenu, promptKey, sessionUser);
        USSDMenu listMenu = new USSDMenu(returnMessage);

        String groupParam = groupIdUrlSuffix + groupId;
        String menuKey = groupKey + "." + existingGroupMenu + "." + optionsKey;

        listMenu.addMenuOption(groupMenus + groupTokenMenu + groupParam, getMessage(menuKey + groupTokenMenu, sessionUser));
        listMenu.addMenuOption(groupMenus + addMemberPrompt + groupParam, getMessage(menuKey + addMemberPrompt, sessionUser));
        listMenu.addMenuOption(groupMenus + unsubscribePrompt + groupParam, getMessage(menuKey + unsubscribePrompt, sessionUser));
        listMenu.addMenuOption(groupMenus + renameGroupPrompt + groupParam, getMessage(menuKey + renameGroupPrompt, sessionUser));

        if (groupManager.isGroupCreatedByUser(groupId, sessionUser))
            listMenu.addMenuOption(groupMenus + mergeGroupMenu + groupParam, getMessage(menuKey + mergeGroupMenu, sessionUser));

        if (groupManager.canUserMakeGroupInactive(sessionUser, groupId))
            listMenu.addMenuOption(groupMenus + inactiveMenu + groupParam, getMessage(menuKey + inactiveMenu, sessionUser));

        return menuBuilder(listMenu);

    }

    /*
    The user is creating a group. First, ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupMenu)
    @ResponseBody
    public Request createPrompt(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + createGroupMenu);
        return menuBuilder(new USSDMenu(getMessage(groupKey, createGroupMenu, promptKey, sessionUser),
                                        groupMenus + createGroupMenu + doSuffix));

    }

    /*
    The user has given a name, now ask whether to enter numbers or just go straight to a joining code
     */

    @RequestMapping(value = groupPath + createGroupMenu + doSuffix)
    @ResponseBody
    public Request createGroupWithName(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                       @RequestParam(value= userInputParam, required=true) String groupName,
                                       @RequestParam(value="interrupted", required=false) boolean interrupted,
                                       @RequestParam(value= groupIdParam, required=false) Long groupId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);

        Group createdGroup = (interrupted) ?    groupManager.loadGroup(groupId) :
                                                groupManager.createNewGroup(user, groupName);

        userManager.setLastUssdMenu(user, interruptedUrl(createGroupMenu + doSuffix, createdGroup.getId(), null));
        groupManager.addGroupMember(createdGroup, user); // since the constructor doesn't do this (todo: have a method that does)

        USSDMenu menu = new USSDMenu(getMessage(groupKey, createGroupMenu + doSuffix, promptKey, createdGroup.getGroupName(), user));
        menu.addMenuOption(groupMenus + createGroupAddToken + groupIdUrlSuffix + createdGroup.getId(),
                           getMessage(groupKey, createGroupMenu + doSuffix, optionsKey + "token", user));
        menu.addMenuOption(groupMenus + createGroupAddNumbers + groupIdUrlSuffix + createdGroup.getId(),
                           getMessage(groupKey, createGroupMenu + doSuffix, optionsKey + "numbers", user));

        return menuBuilder(menu);
    }

    // todo: consider moving these into a USSDUtil or similar class, to make easier to alter / work with / test
    private String interruptedUrl(String currentMenu, Long groupId, String input) {
        String priorInput = (input == null) ? "" : "&prior_input=" + input;
        return groupMenus + currentMenu + groupIdUrlSuffix + groupId + "&interrupted=1" + priorInput;
    }

    /*
    Create an indefinite token and then give the options to go home or add some numbers
     */
    @RequestMapping(value = groupPath + createGroupAddToken)
    @ResponseBody
    public Request createGroupCreateIndefiniteToken(@RequestParam(phoneNumber) String inputNumber,
                                                    @RequestParam(groupIdParam) Long groupId,
                                                    @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        // todo: various forms of error and permission checking
        User user = userManager.findByInputNumber(inputNumber, interruptedUrl(createGroupAddToken, groupId, null));
        Group group = groupManager.loadGroup(groupId);

        String token;

        /*  the only case of coming here and the group has a code is after interruption or after 'add numbers' via create
            hence there is no need to check if the code expiry date has passed (by definition, the code is valid) */
        if (interrupted || (group.getGroupTokenCode() != null && !group.getGroupTokenCode().equals(""))) {
            token = groupManager.loadGroup(groupId).getGroupTokenCode();
        } else {
            token = groupManager.generateGroupToken(groupId).getGroupTokenCode();
        }

        USSDMenu menu = new USSDMenu(getMessage(groupKey, createGroupAddToken, promptKey, token, user));
        menu.addMenuOption(groupMenus + createGroupAddNumbers + groupIdUrlSuffix + groupId,
                           getMessage(groupKey, createGroupAddToken, optionsKey + "add", user));
        menu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                           getMessage(groupKey, createGroupAddToken, optionsKey + "home", user));
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
                                                      @RequestParam(groupIdParam) Long groupId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, interruptedUrl(createGroupAddNumbers, groupId, null));
        USSDMenu menu = new USSDMenu(getMessage(groupKey, createGroupAddNumbers, promptKey, user));
        menu.setNextURI(groupMenus + createGroupAddNumbers + doSuffix + groupIdUrlSuffix + groupId);
        menu.setFreeText(true);
        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + createGroupAddNumbers + doSuffix)
    @ResponseBody
    public Request addNumbersToNewlyCreatedGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                                 @RequestParam(value= groupIdParam, required=true) Long groupId,
                                                 @RequestParam(value= userInputParam, required=true) String userInput,
                                                 @RequestParam(value="prior_input", required=false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        User sessionUser;
        USSDMenu thisMenu = new USSDMenu(true);

        final String userResponse = (priorInput == null) ? userInput : priorInput;
        final String inputToSave = URLEncoder.encode(userResponse, "UTF-8");
        sessionUser = userManager.findByInputNumber(inputNumber,
                                                    interruptedUrl(createGroupAddNumbers + doSuffix, groupId, inputToSave));

        if (userResponse.trim().equals("0")) { // stop asking for numbers, reset interrupt prompt and give options to go back

            thisMenu.setFreeText(false);
            thisMenu.setPromptMessage(getMessage(groupKey, createGroupAddNumbers, promptKey + ".done", sessionUser));
            thisMenu.addMenuOption(groupMenus + createGroupAddToken + groupIdUrlSuffix + groupId,
                                   getMessage(groupKey, createGroupAddNumbers, optionsKey + "token", sessionUser));
            thisMenu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                                   getMessage(groupKey, createGroupAddNumbers, optionsKey + "home", sessionUser));
            thisMenu.addMenuOption("exit", getMessage("exit.option", sessionUser));

        } else {

            Map<String, List<String>> splitPhoneNumbers = PhoneNumberUtil.splitPhoneNumbers(userResponse);
            PhoneNumberUtil.splitPhoneNumbers(userResponse);
            groupManager.addNumbersToGroup(groupId, splitPhoneNumbers.get("valid"));
            thisMenu = numberEntryPrompt(groupId, "added", sessionUser, splitPhoneNumbers.get("error"));

        }

        return menuBuilder(thisMenu);

    }

    /*
    Helper function to process the previously entered numbers and display which ones didn't work
     */
    public USSDMenu numberEntryPrompt(Long groupId, String promptKey, User sessionUser, List<String> errorNumbers) {

        USSDMenu thisMenu = new USSDMenu(true);

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(groupKey, createGroupAddNumbers, USSDController.promptKey + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(groupKey, createGroupAddNumbers, errorPromptKey, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(groupMenus + createGroupAddNumbers + doSuffix + groupIdUrlSuffix + groupId); // loop back to group menu
        return thisMenu;

    }

    /*
    Menu options to rename a group, either existing, or if a new group, to give it a name
    Major todo: integrate permissions for existing groups
     */

    @RequestMapping(value = groupPath + renameGroupPrompt)
    @ResponseBody
    public Request renamePrompt(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                @RequestParam(value= groupIdParam, required=true) Long groupId,
                                @RequestParam(value="newgroup", required=false) boolean newGroup) throws URISyntaxException {

        Group groupToRename;
        String promptMessage;

        String newGroupPassed = newGroup ? ("&newgroup=" + newGroup) : "";
        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenuWithParams(renameGroupPrompt, groupId, newGroupPassed));

        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        if (groupToRename.getGroupName().trim().length() == 0)
            promptMessage = getMessage(groupKey, renameGroupPrompt, promptKey + "1", sessionUser);
        else
            promptMessage = getMessage(groupKey, renameGroupPrompt, promptKey + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, groupMenus + renameGroupPrompt + doSuffix + groupIdUrlSuffix + groupId + newGroupPassed));

    }

    @RequestMapping(value = groupPath + renameGroupPrompt + doSuffix)
    @ResponseBody
    public Request renameGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value= userInputParam, required=true) String newName,
                               @RequestParam(value="prior_input", required=false) String priorInput,
                               @RequestParam(value="newgroup", required=false) boolean newGroup) throws URISyntaxException {

        // todo: consolidate into one service call

        Group groupToRename = groupManager.loadGroup(groupId);
        User sessionUser;

        String name = (priorInput == null) ? newName : priorInput;

        groupToRename.setGroupName(name);
        groupManager.saveGroup(groupToRename);

        USSDMenu thisMenu;

        if (!newGroup) {
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu(getMessage(groupKey, renameGroupPrompt + doSuffix, promptKey, newName, sessionUser),
                                    optionsHomeExit(sessionUser));
        } else {
            // todo: should probably encode name
            sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + renameGroupPrompt + doSuffix +
                    groupIdUrlSuffix + groupId + "&newgroup=1&prior_input=" + name);
            String messageKey = groupKey + "." + renameGroupPrompt + doSuffix + ".";
            thisMenu = new USSDMenu(getMessage(messageKey + promptKey + "-new", sessionUser));
            thisMenu.addMenuOption(groupMenus + groupTokenMenu + groupIdUrlSuffix + groupId, getMessage(messageKey + optionsKey + "token", sessionUser));
            thisMenu.addMenuOption(startMenu, getMessage(messageKey + optionsKey + "start", sessionUser));
        }

        return menuBuilder(thisMenu);

    }

    /*
    Menus to create a group token, depending on whether one exists
     */

    @RequestMapping(value = groupPath + groupTokenMenu)
    @ResponseBody
    public Request groupToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                              @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: validate that this user has permission to create a token for this group

        User sessionUser;
        USSDMenu tokenMenu = new USSDMenu();
        Group sessionGroup = groupManager.loadGroup(groupId);

        if (groupManager.groupHasValidToken(sessionGroup)) {
            /* Existing token, just ask if extend or close, and since last menu set an interruption flag, reset to null*/
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            String tokenCode = sessionGroup.getGroupTokenCode();
            tokenMenu.setPromptMessage(getMessage(groupKey, groupTokenMenu, promptKey + ".exists", tokenCode, sessionUser));
            tokenMenu.addMenuOption(groupMenus + groupTokenMenu + "-extend" + groupIdUrlSuffix + groupId,
                                    getMessage(groupKey, groupTokenMenu, optionsKey + "extend", sessionUser));
            tokenMenu.addMenuOption(groupMenus + groupTokenMenu + "-close" + groupIdUrlSuffix + groupId,
                                    getMessage(groupKey, groupTokenMenu, optionsKey + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu, groupId));
            String daySuffix = getMessage(groupKey, groupTokenMenu, optionsKey + "days", sessionUser);
            tokenMenu.setPromptMessage(getMessage(groupKey, groupTokenMenu, promptKey, sessionUser));
            String daysUrl = groupMenus + groupTokenMenu + doSuffix + groupIdUrlSuffix + groupId + "&days=";
            tokenMenu.addMenuOption(daysUrl + "0", "Permanent (can be closed at any time)");
            tokenMenu.addMenuOption(daysUrl + "1", "One day");
            tokenMenu.addMenuOption(daysUrl + "7", "One week");
        }

        return menuBuilder(tokenMenu);

    }

    @RequestMapping(value = groupPath + groupTokenMenu + doSuffix)
    @ResponseBody
    public Request createToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value="days", required=true) Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber, null); }
        catch (Exception e) { return noUserError; }

        Group group;

        if (daysValid == 0) {
            group = groupManager.generateGroupToken(groupId);
        } else {
            group = groupManager.generateGroupToken(groupId, daysValid);
        }

        USSDMenu returnMessage = new USSDMenu(getMessage(groupKey, groupTokenMenu, "created",
                                                         group.getGroupTokenCode(), sessionUser),
                                              optionsHomeExit(sessionUser));

        return menuBuilder(returnMessage);

    }

    @RequestMapping(value = groupPath + groupTokenMenu + "-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value="days", required=false) Integer daysValid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Group sessionGroup = groupManager.loadGroup(groupId);
        USSDMenu promptMenu = new USSDMenu();

        String thisUri = groupMenus + groupTokenMenu + "-extend" + groupIdUrlSuffix + groupId;

        if (daysValid == null) {

            if (sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongTimestamp())) {

                // todo : probably want to check this before the option even arises
                promptMenu.setPromptMessage("This token is open until you close it, and cannot be extended");
                promptMenu.addMenuOption(groupMenus + groupTokenMenu + groupIdUrlSuffix + groupId, "Back to token menu");
                promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId, "Back to group menu");
                promptMenu.addMenuOptions(optionsHomeExit(sessionUser));

            } else {

                promptMenu.setPromptMessage(getMessage(groupKey, groupTokenMenu, promptKey + ".extend", sessionUser));
                promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId,
                                         getMessage(groupKey, groupTokenMenu, optionsKey + "extend.none", sessionUser));
                String daySuffix = getMessage(groupKey, groupTokenMenu, optionsKey + "days", sessionUser);
                for (int i = 1; i <= 3; i++)
                    promptMenu.addMenuOption(thisUri + "&days=" + i, i + daySuffix);
            }

        } else {
            sessionGroup = groupManager.extendGroupToken(sessionGroup, daysValid);
            // todo: use a proper date formatter here
            String date = sessionGroup.getTokenExpiryDateTime().toLocalDateTime().toString();
            promptMenu = new USSDMenu(getMessage(groupKey, groupTokenMenu, promptKey + ".extend.done", date, sessionUser),
                                        optionsHomeExit(sessionUser));
        }

        return menuBuilder(promptMenu);

    }


    @RequestMapping(value = groupPath + groupTokenMenu + "-close")
    @ResponseBody
    public Request extendToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value= yesOrNoParam, required=false) String confirmed) throws URISyntaxException {

        // todo: check the token and group match, and that the user has token admin rights
        User sessionUser;
        USSDMenu thisMenu;

        if (confirmed == null) {
            sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu + "-close", groupId));
            String beginUri = groupMenus + groupTokenMenu, endUri = groupIdUrlSuffix + groupId;
            thisMenu = new USSDMenu(getMessage(groupKey, groupTokenMenu, promptKey + ".close", sessionUser),
                                    optionsYesNo(sessionUser, beginUri + "-close" + endUri));
        } else if (confirmed.equals("yes")) {
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            // todo: error handling here (bad token, etc., also, security)
            groupManager.invalidateGroupToken(groupId);
            thisMenu = new USSDMenu(getMessage(groupKey, groupTokenMenu, promptKey + ".close-done", sessionUser), optionsHomeExit(sessionUser));
        } else {
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu("Okay, cancelled.", optionsHomeExit(sessionUser));
        }

        return menuBuilder(thisMenu);

    }

    // todo: decide if we want to even keep this
    @RequestMapping(value = groupPath + listGroupMembers)
    @ResponseBody
    public Request listGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                             @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: only list users who are not the same as the user calling the function
        // todo: check if user has a display name, and, if so, just print the display name
        // todo: sorting and pagination

        Group groupToList;
        try { groupToList = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        List<String> usersList = new ArrayList<>();
        for (User userToList : groupToList.getGroupMembers()) {
            usersList.add(PhoneNumberUtil.invertPhoneNumber(userToList.getPhoneNumber()));
        }

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String returnMessage = getMessage(groupKey, listGroupMembers, promptKey,
                                          String.join(", ", usersList), sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));
    }


    @RequestMapping(value = groupPath + addMemberPrompt)
    @ResponseBody
    public Request addNumberInput(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                  @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(addMemberPrompt, groupId));
        String promptMessage = getMessage(groupKey, addMemberPrompt, promptKey, sessionUser);
        return menuBuilder(new USSDMenu(promptMessage, groupMenus + addMemberPrompt + doSuffix + groupIdUrlSuffix + groupId));

    }

    @RequestMapping(value = groupPath + addMemberPrompt + doSuffix)
    @ResponseBody
    public Request addNumberToGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                     @RequestParam(value= groupIdParam, required=true) Long groupId,
                                     @RequestParam(value= userInputParam, required=true) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it
        // todo: check the user-to-add isn't already part of the group, and, if so, notify the user who is adding
        // todo: use the logic to handle it if the number submitted is badly formatted/doesn't work/etc

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        groupManager.addNumberToGroup(groupId, numberToAdd);

        return menuBuilder(new USSDMenu(getMessage(groupKey, addMemberPrompt + doSuffix, promptKey, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = groupPath + unsubscribePrompt)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                      @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(unsubscribePrompt, groupId));
        String menuKey = groupKey + "." + unsubscribePrompt + ".";

        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + promptKey, sessionUser));
        promptMenu.addMenuOption(groupMenus + unsubscribePrompt + doSuffix + groupIdUrlSuffix + groupId,
                                 getMessage(menuKey + optionsKey + "confirm", sessionUser));
        promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId,
                                 getMessage(menuKey + optionsKey + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    // todo: security & permissions so we make sure it is the user themselves doing this
    @RequestMapping(value = groupPath + unsubscribePrompt + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                 @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber, null); }
        catch (NoSuchElementException e) { return noUserError; }

        // todo: add error and exception handling, as well as validation and checking (e.g., if user in group, etc)
        // todo: thorough integration testing

        groupManager.removeGroupMember(groupId, sessionUser);
        String returnMessage = getMessage(groupKey, unsubscribePrompt + doSuffix, promptKey, sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));

    }

    /*
    Menus to merge groups
     */
    @RequestMapping(value = groupPath + mergeGroupMenu)
    @ResponseBody
    public Request selectMergeGroups(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                     @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(mergeGroupMenu, groupId));
        // todo: debug why this is returning inactive groups (service method has active flag)
        List<Group> mergeCandidates = groupManager.getMergeCandidates(user, groupId);

        if (mergeCandidates == null || mergeCandidates.size() == 0) {
            menu = new USSDMenu(getMessage(groupKey, mergeGroupMenu, promptKey + ".error", user));
            menu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId, getMessage(groupKey, mergeGroupMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(groupKey, mergeGroupMenu, promptKey, user));
            menu = ussdGroupUtil.addListOfGroupsToMenu(menu, groupMenus + mergeGroupMenu + "-confirm?firstGroupSelected=" + groupId,
                                         mergeCandidates, user);
        }

        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-confirm")
    @ResponseBody
    public Request confirmMerge(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                @RequestParam(value= groupIdParam, required=true) Long groupId1,
                                @RequestParam(value="firstGroupSelected", required=true) Long firstGroupSelected) throws URISyntaxException {

        // todo: specify which one is smaller
        // todo: check permissions
        String returnUrl = groupMenus + mergeGroupMenu + "-confirm" + groupIdUrlSuffix + groupId1
                + "&firstGroupSelected=" + firstGroupSelected;
        User user = userManager.findByInputNumber(inputNumber, returnUrl);
        String[] groupNames = new String[] { groupManager.loadGroup(groupId1).getName(""),
                groupManager.loadGroup(firstGroupSelected).getName("") };

        USSDMenu menu = new USSDMenu(getMessage(groupKey, mergeGroupMenu + "-confirm", promptKey, groupNames, user));

        menu.addMenuOption(groupMenus + mergeGroupMenu + doSuffix + groupIdUrlSuffix + groupId1 + "&SecondGroupId=" + firstGroupSelected,
                           getMessage(groupKey, mergeGroupMenu + "-confirm", optionsKey + "yes", user));
        menu.addMenuOption(groupMenus + mergeGroupMenu + groupIdUrlSuffix + firstGroupSelected,
                           getMessage(groupKey, mergeGroupMenu + "-confirm", optionsKey + "no1", user));
        menu.addMenuOption(groupMenus + startMenu,
                           getMessage(groupKey, mergeGroupMenu + "-confirm", optionsKey + "no2", user));

        return menuBuilder(menu);
    }

    // todo: refactor this to make it sensible
    @RequestMapping(value = groupPath + mergeGroupMenu + doSuffix)
    @ResponseBody
    public Request mergeDo(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                           @RequestParam(value= groupIdParam, required=true) Long firstGroupId,
                           @RequestParam(value="SecondGroupId", required=true) Long secondGroupId) throws URISyntaxException {


        User user = userManager.findByInputNumber(inputNumber, null); // resetting return flag
        USSDMenu menu = new USSDMenu();

        // trying to debug why it's giving errors some times

        //try {
            groupManager.mergeGroups(firstGroupId, secondGroupId).getId();
            menu.setPromptMessage(getMessage(groupKey, mergeGroupMenu + doSuffix, promptKey, user));
        /*} catch (Exception e) {
            menu.setPromptMessage(getMessage(groupKey, mergeGroupMenu + doSuffix, errorPromptKey, user));
        }*/

        menu.addMenuOption(groupMenus + startMenu, getMessage(groupKey, mergeGroupMenu + doSuffix, optionsKey + "group", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + inactiveMenu)
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                   @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: add exception handling
        // todo: check permissions to do this
        User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
        USSDMenu menu = new USSDMenu(getMessage(groupKey, inactiveMenu, promptKey, user));
        menu.addMenuOption(groupMenus + inactiveMenu + doSuffix + groupIdUrlSuffix + groupId,
                           getMessage(groupKey, inactiveMenu, optionsKey + "confirm", user));
        menu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId,
                           getMessage(groupKey, inactiveMenu, optionsKey + "cancel", user));
        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + inactiveMenu + doSuffix)
    @ResponseBody
    public Request inactiveDo(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                              @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: check for user role & permissions (must have all of them)

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;

        // todo: rather make this rely on an exception from services layer and move logic there
        if (groupManager.canUserMakeGroupInactive(user, groupId)) {
            groupManager.setGroupInactive(groupId);
            menu = new USSDMenu(getMessage(groupKey, inactiveMenu + doSuffix, promptKey + ".success", user), optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(groupKey, inactiveMenu + doSuffix, errorPromptKey, user), optionsHomeExit(user));
        }

        return menuBuilder(menu);

    }

}
