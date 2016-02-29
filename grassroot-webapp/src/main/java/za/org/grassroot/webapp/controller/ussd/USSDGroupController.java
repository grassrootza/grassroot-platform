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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * @author luke on 2015/08/14.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDGroupController extends USSDController {

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
            inactiveMenu = "inactive",
            validity ="validity.";

    private static final String groupPath = homePath + groupMenus;
    private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

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
                                                                   existingGroupMenu, createGroupMenu+doSuffix,null));
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
        USSDMenu listMenu = new USSDMenu(getMessage(thisSection, existingGroupMenu, promptKey, sessionUser));

        String menuKey = thisSection.toKey() + existingGroupMenu + "." + optionsKey;

        listMenu.addMenuOption(groupMenuWithId(groupTokenMenu, groupId), getMessage(menuKey + groupTokenMenu, sessionUser));
        listMenu.addMenuOption(groupMenuWithId(addMemberPrompt, groupId), getMessage(menuKey + addMemberPrompt, sessionUser));
        listMenu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupId), getMessage(menuKey + unsubscribePrompt, sessionUser));
        listMenu.addMenuOption(groupMenuWithId(renameGroupPrompt, groupId), getMessage(menuKey + renameGroupPrompt, sessionUser));

        if (groupManager.isGroupCreatedByUser(groupId, sessionUser))
            listMenu.addMenuOption(groupMenuWithId(mergeGroupMenu, groupId), getMessage(menuKey + mergeGroupMenu, sessionUser));

        if (groupManager.canUserMakeGroupInactive(sessionUser, groupId))
            listMenu.addMenuOption(groupMenuWithId(inactiveMenu, groupId), getMessage(menuKey + inactiveMenu, sessionUser));

        return menuBuilder(listMenu);
    }

    /*
    The user is creating a group. First, ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupMenu)
    @ResponseBody
    public Request createPrompt(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + createGroupMenu);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(sessionUser, thisSection, createGroupMenu + doSuffix));
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

      //  log.info("Inside createGroupMenu ... going to name it ... " + groupName);
       User user = userManager.findByInputNumber(inputNumber);

        Group createdGroup = (interrupted) ?    groupManager.loadGroup(groupId) :
                                                groupManager.createNewGroupWithCreatorAsMember(user, groupName, true);

        userManager.setLastUssdMenu(user, saveGroupMenuWithInput(createGroupMenu + doSuffix, createdGroup.getId(), groupName));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey, createdGroup.getGroupName(), user));

        menu.addMenuOption(groupMenuWithId(createGroupAddToken, createdGroup.getId()),
                           getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "token", user));
        menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, createdGroup.getId()),
                           getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "numbers", user));

        return menuBuilder(menu);
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
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddToken, groupId));
        Group group = groupManager.loadGroup(groupId);

        /*  the only case of coming here and the group has a code is after interruption or after 'add numbers' via create
            hence there is no need to check if the code expiry date has passed (by definition, the code is valid) */

        String token = (interrupted || (group.getGroupTokenCode() != null && !group.getGroupTokenCode().equals(""))) ?
            groupManager.loadGroup(groupId).getGroupTokenCode() :
            groupManager.generateGroupToken(groupId, user).getGroupTokenCode();

        USSDMenu menu = new USSDMenu(getMessage(thisSection, createGroupAddToken, promptKey, token, user));

        menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupId),
                           getMessage(thisSection, createGroupAddToken, optionsKey + "add", user));
        menu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                           getMessage(thisSection, createGroupAddToken, optionsKey + "home", user));
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
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddNumbers, groupId));
        return menuBuilder(new USSDMenu(getMessage(thisSection, createGroupAddNumbers, promptKey, user),
                                     groupMenuWithId(createGroupAddNumbers + doSuffix, groupId)));
    }

    @RequestMapping(value = groupPath + createGroupAddNumbers + doSuffix)
    @ResponseBody
    public Request addNumbersToNewlyCreatedGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                                 @RequestParam(value= groupIdParam, required=true) Long groupId,
                                                 @RequestParam(value= userInputParam, required=true) String userInput,
                                                 @RequestParam(value="prior_input", required=false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        USSDMenu thisMenu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                                                    saveGroupMenuWithInput(createGroupAddNumbers + doSuffix, groupId, userResponse));

        if (!userResponse.trim().equals("0")) {
            thisMenu = ussdGroupUtil.addNumbersToExistingGroup(user, groupId, thisSection, userResponse, createGroupAddNumbers + doSuffix);
        } else { // stop asking for numbers, reset interrupt prompt and give options to go back
            thisMenu = new USSDMenu(getMessage(thisSection, createGroupAddNumbers, promptKey + ".done", user));
            thisMenu.addMenuOption(groupMenus + createGroupAddToken + groupIdUrlSuffix + groupId,
                                   getMessage(thisSection, createGroupAddNumbers, optionsKey + "token", user));
            thisMenu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                                   getMessage(thisSection, createGroupAddNumbers, optionsKey + "home", user));
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
    public Request renamePrompt(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                @RequestParam(value= groupIdParam, required=true) Long groupId,
                                @RequestParam(value="newgroup", required=false) boolean newGroup) throws URISyntaxException {

        String newGroupPassed = newGroup ? ("&newgroup=" + newGroup) : "";
        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenuWithParams(renameGroupPrompt, groupId, newGroupPassed));
        Group groupToRename = groupManager.loadGroup(groupId);

        String promptMessage = (groupToRename.getGroupName().trim().length() == 0) ?
            getMessage(thisSection, renameGroupPrompt, promptKey + "1", sessionUser) :
            getMessage(thisSection, renameGroupPrompt, promptKey + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, groupMenuWithId(renameGroupPrompt + doSuffix, groupId) + newGroupPassed));

    }

    @RequestMapping(value = groupPath + renameGroupPrompt + doSuffix)
    @ResponseBody
    public Request renameGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value= userInputParam, required=true) String newName,
                               @RequestParam(value= interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value="newgroup", required=false) boolean newGroup) throws URISyntaxException {

        // todo: consolidate into one service call

        User user;
        USSDMenu thisMenu;
        if (!interrupted) groupManager.renameGroup(groupId, newName);

        if (!newGroup) {
            user = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu(getMessage(thisSection, renameGroupPrompt + doSuffix, promptKey, newName, user),
                                    optionsHomeExit(user));
        } else {
            user = userManager.findByInputNumber(inputNumber,
                                                        saveGroupMenuWithParams(renameGroupPrompt + doSuffix, groupId, "&newgroup=1"));
            String messageKey = groupKey + "." + renameGroupPrompt + doSuffix + ".";
            thisMenu = new USSDMenu(getMessage(messageKey + promptKey + "-new", user));
            thisMenu.addMenuOption(groupMenuWithId(groupTokenMenu, groupId),
                                   getMessage(messageKey + optionsKey + "token", user));
            thisMenu.addMenuOption(startMenu, getMessage(messageKey + optionsKey + "start", user));
        }
        return menuBuilder(thisMenu);
    }

    /*
    SECTION: MENUS FOR GROUP TOKENS
     */

    @RequestMapping(value = groupPath + groupTokenMenu)
    @ResponseBody
    public Request groupToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                              @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: validate that this user has permission to create a token for this group

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu, groupId));
        USSDMenu tokenMenu;
        Group sessionGroup = groupManager.loadGroup(groupId);

        if (groupManager.groupHasValidToken(sessionGroup)) {
            String tokenCode = sessionGroup.getGroupTokenCode();
            boolean indefiniteToken = sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongTimestamp());
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".exists", tokenCode, sessionUser));
            if (!indefiniteToken) tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupId),
                                                          getMessage(thisSection, groupTokenMenu, optionsKey + "extend", sessionUser));
            tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-close", groupId),
                                    getMessage(thisSection, groupTokenMenu, optionsKey + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey, sessionUser));
            String daysUrl = groupMenus + groupTokenMenu + doSuffix + groupIdUrlSuffix + groupId + "&days=";
            tokenMenu.addMenuOption(daysUrl + "0", getMessage(thisSection,groupTokenMenu,validity+"permanent",sessionUser));
            tokenMenu.addMenuOption(daysUrl + "1", getMessage(thisSection,groupTokenMenu,validity+"day",sessionUser));
            tokenMenu.addMenuOption(daysUrl + "7", getMessage(thisSection,groupTokenMenu,validity+"week",sessionUser));
        }
        return menuBuilder(tokenMenu);
    }

    @RequestMapping(value = groupPath + groupTokenMenu + doSuffix)
    @ResponseBody
    public Request createToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value="days", required=true) Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */
        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        Group group = (daysValid == 0) ? groupManager.generateGroupToken(groupId, sessionUser) :
                groupManager.generateGroupToken(groupId, daysValid, sessionUser);
        return menuBuilder(new USSDMenu(getMessage(thisSection, groupTokenMenu, "created", group.getGroupTokenCode(), sessionUser),
                                              optionsHomeExit(sessionUser)));
    }

    @RequestMapping(value = groupPath + groupTokenMenu + "-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                               @RequestParam(value= groupIdParam, required=true) Long groupId,
                               @RequestParam(value="days", required=false) Integer daysValid) throws URISyntaxException {

        String urlToSave = (daysValid == null) ? saveGroupMenu(groupTokenMenu + "-extend", groupId) : null;
        User sessionUser = userManager.findByInputNumber(inputNumber, urlToSave);
        Group sessionGroup = groupManager.loadGroup(groupId);
        USSDMenu promptMenu;

        if (daysValid == null) {
            // means we are still asking for the number of days to extend
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend", sessionUser));
            promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupIdUrlSuffix + groupId,
                                     getMessage(thisSection, groupTokenMenu, optionsKey + "extend.none", sessionUser));
            String daySuffix = getMessage(thisSection, groupTokenMenu, optionsKey + "days", sessionUser);
            for (int i = 1; i <= 3; i++)
                promptMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupId)+ "&days=" + i, i + daySuffix);
        } else {
            // we have been passed a number of days to extend
            sessionGroup = groupManager.extendGroupToken(sessionGroup, daysValid, sessionUser);
            String date = sessionGroup.getTokenExpiryDateTime().toLocalDateTime().format(dateFormat);
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend.done", date, sessionUser),
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
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close", sessionUser),
                                    optionsYesNo(sessionUser, beginUri + "-close" + endUri));
        } else if ("yes".equals(confirmed)) {
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            // todo: error handling here (bad token, etc., also, security)
            groupManager.invalidateGroupToken(groupId, sessionUser);
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close-done", sessionUser),
                                    optionsHomeExit(sessionUser));
        } else {
            sessionUser = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu("Okay, cancelled", optionsHomeExit(sessionUser));
        }

        return menuBuilder(thisMenu);

    }

    /**
     * SECTION: MENUS TO ADD MEMBERS, UNSUBSCRIBE, AND LIST MEMBERS
     * */

    // todo: decide if we want to even keep this
    @RequestMapping(value = groupPath + listGroupMembers)
    @ResponseBody
    public Request listGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                             @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: only list users who are not the same as the user calling the function
        // todo: check if user has a display name, and, if so, just print the display name
        // todo: sorting and pagination

        Group groupToList = groupManager.loadGroup(groupId);

        List<String> usersList = new ArrayList<>();
        for (User userToList : groupToList.getGroupMembers()) {
            usersList.add(PhoneNumberUtil.invertPhoneNumber(userToList.getPhoneNumber()));
        }

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String returnMessage = getMessage(thisSection, listGroupMembers, promptKey, String.join(", ", usersList), sessionUser);

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
        String promptMessage = getMessage(thisSection, addMemberPrompt, promptKey, sessionUser);
        return menuBuilder(new USSDMenu(promptMessage, groupMenus + addMemberPrompt + doSuffix + groupIdUrlSuffix + groupId));

    }

    @RequestMapping(value = groupPath + addMemberPrompt + doSuffix)
    @ResponseBody
    public Request addNumberToGroup(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                     @RequestParam(value= groupIdParam, required=true) Long groupId,
                                     @RequestParam(value= userInputParam, required=true) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        USSDMenu thisMenu = (numberToAdd.trim().equals("0")) ?
                new USSDMenu(getMessage(thisSection, addMemberPrompt + doSuffix, promptKey, sessionUser),
                                        optionsHomeExit(sessionUser)) :
                ussdGroupUtil.addNumbersToExistingGroup(sessionUser, groupId, thisSection, numberToAdd, addMemberPrompt + doSuffix);

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = groupPath + unsubscribePrompt)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                      @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(unsubscribePrompt, groupId));
        String menuKey = groupKey + "." + unsubscribePrompt + ".";

        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + promptKey, sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(unsubscribePrompt + doSuffix, groupId),
                                 getMessage(menuKey + optionsKey + "confirm", sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(existingGroupMenu, groupId),
                                 getMessage(menuKey + optionsKey + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    // todo: security & permissions so we make sure it is the user themselves doing this
    @RequestMapping(value = groupPath + unsubscribePrompt + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                 @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        // todo: add error and exception handling, as well as validation and checking (e.g., if user in group, etc)
        // todo: thorough integration testing
        groupManager.removeGroupMember(groupId, sessionUser, sessionUser);
        String returnMessage = getMessage(thisSection, unsubscribePrompt + doSuffix, promptKey, sessionUser);
        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));
    }

    /**
     * SECTION: MERGING GROUP MENUS (AND DEACTIVATE)
     * */

    @RequestMapping(value = groupPath + mergeGroupMenu)
    @ResponseBody
    public Request selectMergeGroups(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                     @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(mergeGroupMenu, groupId));
        // todo: debug why this is returning inactive groups (service method has active flag)
        List<Group> mergeCandidates = groupManager.getMergeCandidates(user, groupId);

        if (mergeCandidates == null || mergeCandidates.size() == 0) {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey + ".error", user));
            menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupId),
                               getMessage(thisSection, mergeGroupMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey, user));
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
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenuWithParams(mergeGroupMenu + "-confirm", groupId1,
                                                                                       "&firstGroupSelected=" + firstGroupSelected));
        String[] groupNames = new String[] { groupManager.loadGroup(groupId1).getName(""),
                groupManager.loadGroup(firstGroupSelected).getName("") };

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-confirm", promptKey, groupNames, user));
        String urlRoot = groupMenus + mergeGroupMenu + doSuffix + "&groupId1=" + groupId1 + "&groupId2=" + firstGroupSelected + "&action=";
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
                                      @RequestParam(value = "groupId1") Long groupId1,
                                      @RequestParam(value = "groupId2") Long groupId2) throws URISyntaxException {

        String groupsSuffix = "?groupId1=" + groupId1 + "&groupId2=" + groupId2;
        User user = userManager.findByInputNumber(inputNumber, groupMenus + mergeGroupMenu + "-newname" + groupsSuffix);
        return menuBuilder(new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-newname", promptKey, user),
                                         groupMenus + mergeGroupMenu + doSuffix + groupsSuffix));
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + doSuffix)
    @ResponseBody
    public Request mergeDo(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                           @RequestParam(value= groupIdParam + "1", required=true) Long firstGroupId,
                           @RequestParam(value= groupIdParam + "2", required=true) Long secondGroupId,
                           @RequestParam(value ="action", required=false) String action,
                           @RequestParam(value=userInputParam, required = false) String userInput) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null); // resetting return flag
        Long returnGroupId;

        switch (action) {
            case "inactive":
                returnGroupId = groupManager.mergeGroups(firstGroupId, secondGroupId, user.getId()).getId();
                break;
            case "active":
                returnGroupId = groupManager.mergeGroupsLeaveActive(firstGroupId, secondGroupId, user.getId()).getId();
                break;
            case "new":
                returnGroupId = groupManager.mergeGroupsIntoNew(firstGroupId, secondGroupId, userInput, user).getId();
                break;
            default:
                returnGroupId = groupManager.mergeGroups(firstGroupId, secondGroupId, user.getId()).getId();
                break;
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + doSuffix, promptKey, user));

        menu.addMenuOption(groupMenuWithId(existingGroupMenu, returnGroupId),
                           getMessage(thisSection, mergeGroupMenu + doSuffix, optionsKey + "group", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + inactiveMenu)
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                   @RequestParam(value= groupIdParam, required=true) Long groupId) throws URISyntaxException {

        // todo: check permissions to do this and add exception handling
        User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
        USSDMenu menu = new USSDMenu(getMessage(thisSection, inactiveMenu, promptKey, user));
        menu.addMenuOption(groupMenuWithId(inactiveMenu + doSuffix, groupId),
                           getMessage(thisSection, inactiveMenu, optionsKey + "confirm", user));
        menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupId),
                           getMessage(thisSection, inactiveMenu, optionsKey + "cancel", user));
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
            groupManager.setGroupInactive(groupId, user);
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, promptKey + ".success", user), optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, errorPromptKey, user), optionsHomeExit(user));
        }

        return menuBuilder(menu);

    }

}