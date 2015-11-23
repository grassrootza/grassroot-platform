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
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDGroupController extends USSDController {

    /**
     * Starting the group management menu flow here
     */

    Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String existingGroupMenu = "menu",
            createGroupMenu = "create",
            listGroupMembers = "list",
            renameGroupPrompt = "rename",
            addMemberPrompt = "addnumber",
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token",
            mergeGroupMenu = "merge",
            inactiveMenu = "inactive";

    private static final String groupPath = USSD_BASE + GROUP_MENUS;

    /*
    First menu: display a list of groups, with the option to create a new one
     */
    @RequestMapping(value = groupPath + START_KEY)
    @ResponseBody
    public Request groupList(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        String returnMessage = getMessage(GROUP_KEY, START_KEY, PROMPT, sessionUser);

        return menuBuilder(userGroupMenu(sessionUser, returnMessage, GROUP_MENUS + existingGroupMenu, true));

    }

    /*
    Second menu: once the user has selected a group, give them options to name, create join code, add a member, or unsub themselves
    Major todo: check what permissions the user has and only display options that they can do
     */

    @RequestMapping(value = groupPath + existingGroupMenu)
    @ResponseBody
    public Request groupMenu(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                             @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        if (groupId == 0) { return createPrompt(inputNumber); }

        User sessionUser = userManager.loadOrSaveUser(inputNumber, GROUP_MENUS + existingGroupMenu + GROUPID_URL + groupId);
        String returnMessage = getMessage(GROUP_KEY, existingGroupMenu, PROMPT, sessionUser);
        USSDMenu listMenu = new USSDMenu(returnMessage);

        String groupParam = GROUPID_URL + groupId;
        String menuKey = GROUP_KEY + "." + existingGroupMenu + "." + OPTION;

        listMenu.addMenuOption(GROUP_MENUS + groupTokenMenu + groupParam, getMessage(menuKey + groupTokenMenu, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + addMemberPrompt + groupParam, getMessage(menuKey + addMemberPrompt, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + unsubscribePrompt + groupParam, getMessage(menuKey + unsubscribePrompt, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + renameGroupPrompt + groupParam, getMessage(menuKey + renameGroupPrompt, sessionUser));

        if (groupManager.isGroupCreatedByUser(groupId, sessionUser))
            listMenu.addMenuOption(GROUP_MENUS + mergeGroupMenu + groupParam, getMessage(menuKey + mergeGroupMenu, sessionUser));

        if (groupManager.canUserMakeGroupInactive(sessionUser, groupId))
            listMenu.addMenuOption(GROUP_MENUS + inactiveMenu + groupParam, getMessage(menuKey + inactiveMenu, sessionUser));

        return menuBuilder(listMenu);

    }

    /*
    The user is creating a group, so ask for the first number
     */

    @RequestMapping(value = groupPath + createGroupMenu)
    @ResponseBody
    public Request createPrompt(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber, GROUP_MENUS + createGroupMenu);

        return menuBuilder(new USSDMenu(getMessage(GROUP_KEY, createGroupMenu, PROMPT, sessionUser),
                                        GROUP_MENUS + createGroupMenu + DO_SUFFIX));

    }

    /*
    Generates a loop, where it keeps asking for additional numbers and adds them to group over and over, until the
    user enters "0", when wrap up, and ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupMenu + DO_SUFFIX)
    @ResponseBody
    public Request createGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=true) String userInput,
                               @RequestParam(value="prior_input", required=false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        USSDMenu thisMenu = new USSDMenu(true);

        final String userResponse = (priorInput == null) ? userInput : priorInput;
        final String groupParameter = (groupId == null) ? "?" : GROUPID_URL + groupId + "&";
        final String inputToSave = URLEncoder.encode(userResponse, "UTF-8");

        String urlToSave = GROUP_MENUS + createGroupMenu + DO_SUFFIX + groupParameter + "prior_input=" + inputToSave;

        User sessionUser = userManager.loadOrSaveUser(inputNumber, urlToSave);

        if (userResponse.trim().equals("0")) { // stop asking for numbers and go on to naming the group
            thisMenu.setPromptMessage(getMessage(GROUP_KEY, createGroupMenu + DO_SUFFIX, PROMPT + ".done", sessionUser));
            thisMenu.setNextURI(GROUP_MENUS + renameGroupPrompt + DO_SUFFIX + GROUPID_URL + groupId + "&newgroup=1"); // reusing the rename function
        } else {
            Map<String, List<String>> splitPhoneNumbers = PhoneNumberUtil.splitPhoneNumbers(userResponse);
            PhoneNumberUtil.splitPhoneNumbers(userResponse);
            if (groupId == null) { // creating a new group, process numbers and ask for more
                Group createdGroup = groupManager.createNewGroup(sessionUser, splitPhoneNumbers.get("valid"));
                thisMenu = numberEntryPrompt(createdGroup.getId(), "created", sessionUser, splitPhoneNumbers.get("error"));
                userManager.setLastUssdMenu(sessionUser, GROUP_MENUS + createGroupMenu + DO_SUFFIX + GROUPID_URL + createdGroup.getId() + "&prior_input=" + inputToSave);
            } else { // adding to a group, process numbers and ask to fix errors or to stop
                groupManager.addNumbersToGroup(groupId, splitPhoneNumbers.get("valid"));
                thisMenu = numberEntryPrompt(groupId, "added", sessionUser, splitPhoneNumbers.get("error"));
            }
        }

        return menuBuilder(thisMenu);

    }

    /*
    Helper function to process the previously entered numbers and display which ones didn't work
     */
    public USSDMenu numberEntryPrompt(Long groupId, String promptKey, User sessionUser, List<String> errorNumbers) {

        USSDMenu thisMenu = new USSDMenu(true);

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(GROUP_KEY, createGroupMenu + DO_SUFFIX, PROMPT + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(GROUP_KEY, createGroupMenu + DO_SUFFIX, PROMPT_ERROR, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(GROUP_MENUS + createGroupMenu + DO_SUFFIX + GROUPID_URL + groupId); // loop back to group menu
        return thisMenu;

    }

    /*
    Menu options to rename a group, either existing, or if a new group, to give it a name
    Major todo: integrate permissions for existing groups
     */

    @RequestMapping(value = groupPath + renameGroupPrompt)
    @ResponseBody
    public Request renamePrompt(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                                @RequestParam(value="newgroup", required=false) Integer newGroup) throws URISyntaxException {

        Group groupToRename;
        String promptMessage;
        String newGroupPassed = (newGroup == null) ? "" : ("&newgroup=" + newGroup);

        User sessionUser = userManager.loadOrSaveUser(inputNumber, groupPath + renameGroupPrompt + GROUPID_URL + groupId + newGroupPassed);

        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        if (groupToRename.getGroupName().trim().length() == 0)
            promptMessage = getMessage(GROUP_KEY, renameGroupPrompt, PROMPT + "1", sessionUser);
        else
            promptMessage = getMessage(GROUP_KEY, renameGroupPrompt, PROMPT + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, GROUP_MENUS + renameGroupPrompt + DO_SUFFIX + GROUPID_URL + groupId + newGroupPassed));

    }

    @RequestMapping(value = groupPath + renameGroupPrompt + DO_SUFFIX)
    @ResponseBody
    public Request renameGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=true) String newName,
                               @RequestParam(value="prior_input", required=false) String priorInput,
                               @RequestParam(value="newgroup", required=false) Integer newGroup) throws URISyntaxException {

        Group groupToRename;
        User sessionUser = userManager.findByInputNumber(inputNumber);
        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        String name = (priorInput == null) ? newName : priorInput;

        groupToRename.setGroupName(name);
        groupManager.saveGroup(groupToRename);

        USSDMenu thisMenu;

        if (newGroup == null || newGroup != 1) {
            sessionUser = userManager.loadOrSaveUser(inputNumber, null);
            thisMenu = new USSDMenu(getMessage(GROUP_KEY, renameGroupPrompt + DO_SUFFIX, PROMPT, newName, sessionUser),
                                    optionsHomeExit(sessionUser));
        } else {
            sessionUser = userManager.loadOrSaveUser(inputNumber, GROUP_MENUS + renameGroupPrompt + DO_SUFFIX +
                                                    GROUPID_URL + groupId + "&newgroup=1&prior_input=" + name);
            String messageKey = GROUP_KEY + "." + renameGroupPrompt + DO_SUFFIX + ".";
            thisMenu = new USSDMenu(getMessage(messageKey + PROMPT + "-new", sessionUser));
            thisMenu.addMenuOption(GROUP_MENUS + groupTokenMenu + GROUPID_URL + groupId, getMessage(messageKey + OPTION + "token", sessionUser));
            thisMenu.addMenuOption(START_KEY, getMessage(messageKey + OPTION + "start", sessionUser));
        }

        return menuBuilder(thisMenu);

    }

    /*
    Menus to create a group token, depending on whether one exists
     */

    @RequestMapping(value = groupPath + groupTokenMenu)
    @ResponseBody
    public Request groupToken(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: validate that this user has permission to create a token for this group
        // todo: check that there is not already a group token valid & open

        User sessionUser;
        USSDMenu tokenMenu = new USSDMenu();
        Group sessionGroup = groupManager.loadGroup(groupId);

        if (groupManager.groupHasValidToken(sessionGroup)) {
            /* Existing token, so just ask if extend or close, no need to deal with interruption logic */
            sessionUser = userManager.loadOrSaveUser(inputNumber);
            String tokenCode = sessionGroup.getGroupTokenCode();
            tokenMenu.setPromptMessage(getMessage(GROUP_KEY, groupTokenMenu, PROMPT + ".exists", tokenCode, sessionUser));
            tokenMenu.addMenuOption(GROUP_MENUS + groupTokenMenu + "-extend" + GROUPID_URL + groupId + TOKEN_URL + tokenCode,
                                    getMessage(GROUP_KEY, groupTokenMenu, OPTION + "extend", sessionUser));
            tokenMenu.addMenuOption(GROUP_MENUS + groupTokenMenu + "-close" + GROUPID_URL + groupId + TOKEN_URL + tokenCode,
                                    getMessage(GROUP_KEY, groupTokenMenu, OPTION + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            sessionUser = userManager.loadOrSaveUser(inputNumber, GROUP_MENUS + groupTokenMenu + GROUPID_URL + groupId);
            String daySuffix = getMessage(GROUP_KEY, groupTokenMenu, OPTION + "days", sessionUser);
            tokenMenu.setPromptMessage(getMessage(GROUP_KEY, groupTokenMenu, PROMPT, sessionUser));
            String daysUrl = GROUP_MENUS + groupTokenMenu + DO_SUFFIX + GROUPID_URL + groupId + "&days=";
            for (int i = 1; i <= 5; i++) {
                tokenMenu.addMenuOption(daysUrl + i, i + daySuffix);
            }
        }

        return menuBuilder(tokenMenu);

    }

    @RequestMapping(value = groupPath + groupTokenMenu + DO_SUFFIX)
    @ResponseBody
    public Request createToken(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value="days", required=true) Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */

        User sessionUser;
        try { sessionUser = userManager.loadOrSaveUser(inputNumber, null); }
        catch (Exception e) { return noUserError; }

        Group sessionGroup = groupManager.generateGroupToken(groupId, daysValid);

        USSDMenu returnMessage = new USSDMenu(getMessage(GROUP_KEY, groupTokenMenu, "created",
                                                         sessionGroup.getGroupTokenCode(), sessionUser),
                                              optionsHomeExit(sessionUser));

        return menuBuilder(returnMessage);

    }

    @RequestMapping(value = groupPath + groupTokenMenu + "-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value=TOKEN_PARAM, required=true) String code,
                               @RequestParam(value="days", required=false) Integer daysValid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Group sessionGroup = groupManager.loadGroup(groupId);
        USSDMenu promptMenu = new USSDMenu();
        String thisUri = GROUP_MENUS + groupTokenMenu + "_extend" + GROUPID_URL + groupId;

        if (daysValid == null) {
            promptMenu.setPromptMessage(getMessage(GROUP_KEY, groupTokenMenu, PROMPT + ".extend", sessionUser));
            promptMenu.addMenuOption(GROUP_MENUS + existingGroupMenu + GROUPID_URL + groupId,
                                     getMessage(GROUP_KEY, groupTokenMenu, OPTION + "extend.none", sessionUser));
            String daySuffix = getMessage(GROUP_KEY, groupTokenMenu, OPTION + "days", sessionUser);
            for (int i = 1; i <= 3; i++)
                promptMenu.addMenuOption(thisUri + "&days=" + i, i + daySuffix);
        } else {
            sessionGroup = groupManager.extendGroupToken(sessionGroup, daysValid);
            String date = sessionGroup.getTokenExpiryDateTime().toLocalDateTime().toString();
            promptMenu = new USSDMenu(getMessage(GROUP_KEY, groupTokenMenu, PROMPT + ".extend.done", date, sessionUser),
                                        optionsHomeExit(sessionUser));
        }

        return menuBuilder(promptMenu);

    }


    @RequestMapping(value = groupPath + groupTokenMenu + "-close")
    @ResponseBody
    public Request extendToken(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value=TOKEN_PARAM, required=true) String code,
                               @RequestParam(value=YESNO_FIELD, required=false) String confirmed) throws URISyntaxException {

        // todo: check the token and group match, and that the user has token admin rights
        User sessionUser = userManager.findByInputNumber(inputNumber);
        USSDMenu thisMenu;

        if (confirmed == null) {
            String beginUri = GROUP_MENUS + groupTokenMenu, endUri = GROUPID_URL + groupId + "&" + TOKEN_PARAM + "=" + code;
            thisMenu = new USSDMenu(getMessage(GROUP_KEY, groupTokenMenu, PROMPT + ".close", sessionUser),
                                    optionsYesNo(sessionUser, beginUri + "-close" + endUri));
        } else if (confirmed.equals("yes")) {
            // todo: error handling here (bad token, etc., also, security)
            groupManager.invalidateGroupToken(groupId);
            thisMenu = new USSDMenu(getMessage(GROUP_KEY, groupTokenMenu, PROMPT + ".close-done", sessionUser), optionsHomeExit(sessionUser));
        } else {
            thisMenu = new USSDMenu("Okay, cancelled.", optionsHomeExit(sessionUser));
        }

        return menuBuilder(thisMenu);

    }


    @RequestMapping(value = groupPath + listGroupMembers)
    @ResponseBody
    public Request listGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                             @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

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
        String returnMessage = getMessage(GROUP_KEY, listGroupMembers, PROMPT,
                                          String.join(", ", usersList), sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));
    }


    @RequestMapping(value = groupPath + addMemberPrompt)
    @ResponseBody
    public Request addNumberInput(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String promptMessage = getMessage(GROUP_KEY, addMemberPrompt, PROMPT, sessionUser);
        return menuBuilder(new USSDMenu(promptMessage, GROUP_MENUS + addMemberPrompt + DO_SUFFIX + GROUPID_URL + groupId));

    }

    @RequestMapping(value = groupPath + addMemberPrompt + DO_SUFFIX)
    @ResponseBody
    public Request addNumberToGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                     @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                                     @RequestParam(value=TEXT_PARAM, required=true) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it
        // todo: check the user-to-add isn't already part of the group, and, if so, notify the user who is adding
        // todo: build logic to handle it if the number submitted is badly formatted/doesn't work/etc

        User sessionUser = userManager.findByInputNumber(inputNumber);

        Group sessionGroup;
        try { sessionGroup = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        List<User> groupMembers = sessionGroup.getGroupMembers();
        groupMembers.add(userManager.loadOrSaveUser(numberToAdd));
        sessionGroup.setGroupMembers(groupMembers);
        sessionGroup = groupManager.saveGroup(sessionGroup);

        return menuBuilder(new USSDMenu(getMessage(GROUP_KEY, addMemberPrompt + DO_SUFFIX, PROMPT, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = groupPath + unsubscribePrompt)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                      @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String menuKey = GROUP_KEY + "." + unsubscribePrompt + ".";

        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + PROMPT, sessionUser));
        promptMenu.addMenuOption(GROUP_MENUS + unsubscribePrompt + DO_SUFFIX + GROUPID_URL + groupId,
                                 getMessage(menuKey + OPTION + "confirm", sessionUser));
        promptMenu.addMenuOption(GROUP_MENUS + existingGroupMenu + GROUPID_URL + groupId,
                                 getMessage(menuKey + OPTION + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value = groupPath + unsubscribePrompt + DO_SUFFIX)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                 @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        Group sessionGroup = new Group();
        try { sessionGroup = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        // todo: add error and exception handling, as well as validation and checking (e.g., if user in group, etc)
        // todo: check if the list in the user is updated too ...

        groupManager.removeGroupMember(sessionGroup, sessionUser);

        String returnMessage = getMessage(GROUP_KEY, unsubscribePrompt + DO_SUFFIX, PROMPT, sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));

    }

    /*
    Menus to merge groups
     */
    @RequestMapping(value = groupPath + mergeGroupMenu)
    @ResponseBody
    public Request selectMergeGroups(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                     @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber);
        List<Group> mergeCandidates = groupManager.getMergeCandidates(user, groupId);

        if (mergeCandidates == null || mergeCandidates.size() == 0) {
            menu = new USSDMenu(getMessage(GROUP_KEY, mergeGroupMenu, PROMPT + ".error", user));
            menu.addMenuOption(GROUP_MENUS + existingGroupMenu + GROUPID_URL + groupId, getMessage(GROUP_KEY, mergeGroupMenu, OPTION + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(GROUP_KEY, mergeGroupMenu, PROMPT, user));
            menu = addListOfGroupsToMenu(menu, GROUP_MENUS + mergeGroupMenu + "-confirm?groupIdFirst=" + groupId,
                                         mergeCandidates, user);

        }

        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-confirm")
    @ResponseBody
    public Request confirmMerge(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                @RequestParam(value=GROUP_PARAM, required=true) Long groupId1,
                                @RequestParam(value=GROUP_PARAM+"First", required=true) Long groupIdFirst) throws URISyntaxException {

        // todo: specify which one is smaller
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu(getMessage(GROUP_KEY, mergeGroupMenu + "-confirm", PROMPT, user));

        menu.addMenuOption(GROUP_MENUS + mergeGroupMenu + DO_SUFFIX + GROUPID_URL + groupId1 + "&groupIdFirst=" + groupIdFirst,
                           getMessage(GROUP_KEY, mergeGroupMenu + "-confirm", OPTION + "yes", user));
        menu.addMenuOption(GROUP_MENUS + mergeGroupMenu + GROUPID_URL + groupIdFirst,
                           getMessage(GROUP_KEY, mergeGroupMenu + "-confirm", OPTION + "no1", user));
        menu.addMenuOption(GROUP_MENUS + START_KEY,
                           getMessage(GROUP_KEY, mergeGroupMenu + "-confirm", OPTION + "no2", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + DO_SUFFIX)
    @ResponseBody
    public Request mergeDo(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                           @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                           @RequestParam(value=GROUP_PARAM+"First", required=true) Long groupIdFirst) throws URISyntaxException {


        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu();
        Long remainingGroupId;

        try {
            remainingGroupId = groupManager.mergeGroups(groupIdFirst, groupId).getId();
            menu.setPromptMessage(getMessage(GROUP_KEY, mergeGroupMenu + DO_SUFFIX, PROMPT, user));
        } catch (Exception e) {
            remainingGroupId = groupIdFirst; // this will actually be the group the user started with
            menu.setPromptMessage(getMessage(GROUP_KEY, mergeGroupMenu + DO_SUFFIX, PROMPT_ERROR, user));
        }

        menu.addMenuOption(GROUP_MENUS + existingGroupMenu + GROUPID_URL + remainingGroupId,
                           getMessage(GROUP_KEY, mergeGroupMenu + DO_SUFFIX, OPTION + "group", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + inactiveMenu)
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                   @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: add exception handling
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu(getMessage(GROUP_KEY, inactiveMenu, PROMPT, user));
        menu.addMenuOption(GROUP_MENUS + inactiveMenu + DO_SUFFIX + GROUPID_URL + groupId,
                           getMessage(GROUP_KEY, inactiveMenu, OPTION + "confirm", user));
        menu.addMenuOption(GROUP_MENUS + existingGroupMenu + GROUPID_URL + groupId,
                           getMessage(GROUP_KEY, inactiveMenu, OPTION + "cancel", user));
        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + inactiveMenu + DO_SUFFIX)
    @ResponseBody
    public Request inactiveDo(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                              @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: check for user role & permissions (must have all of them)

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;

        // todo: rather make this rely on an exception from services layer and move logic there
        if (groupManager.canUserMakeGroupInactive(user, groupId)) {
            groupManager.setGroupInactive(groupId);
            menu = new USSDMenu(getMessage(GROUP_KEY, inactiveMenu + DO_SUFFIX, PROMPT + ".success", user), optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(GROUP_KEY, inactiveMenu + DO_SUFFIX, PROMPT_ERROR, user), optionsHomeExit(user));
        }

        return menuBuilder(menu);

    }

}
