package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManager;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URI;
import java.net.URISyntaxException;
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
     * todo: Add in validation and checking that group is valid, and user can call a meeting on it
     * todo: Add in extracting names and numbers from groups without names so users know what group it is
     * todo: Stub out remaining menus
     */

    private static final String keyMenu = "menu", keyListGroup = "list", keyRenameGroup = "rename",
            keyAddNumber = "addnumber", keyUnsubscribe = "unsubscribe", keySecondMenu = "menu2", keyDelGroup = "delete";

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + START_KEY)
    @ResponseBody
    public Request groupList(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        String returnMessage = getMessage(GROUP_KEY, START_KEY, PROMPT, sessionUser);

        return menuBuilder(userGroupMenu(sessionUser, returnMessage, GROUP_MENUS + keyMenu, true));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyMenu)
    @ResponseBody
    public Request groupMenu(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                             @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: check what permissions the user has and only display options that they can do

        User sessionUser = userManager.findByInputNumber(inputNumber);

        String returnMessage = getMessage(GROUP_KEY, keyMenu, PROMPT, sessionUser);
        USSDMenu listMenu = new USSDMenu(returnMessage);

        String groupParam = GROUPID_URL + groupId;
        String menuKey = GROUP_KEY + "." + keyMenu + "." + OPTION;

        listMenu.addMenuOption(GROUP_MENUS + keyListGroup + groupParam, getMessage(menuKey + keyListGroup, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + keyRenameGroup + groupParam, getMessage(menuKey + keyRenameGroup, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + keyAddNumber + groupParam, getMessage(menuKey + keyAddNumber, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + keyUnsubscribe + groupParam, getMessage(menuKey + keyUnsubscribe, sessionUser));
        listMenu.addMenuOption(GROUP_MENUS + keySecondMenu , getMessage(menuKey + MORE, sessionUser));

        System.out.println("Menu length: " + listMenu.getMenuCharLength());

        return menuBuilder(listMenu);

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyListGroup)
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
            usersList.add(UserManager.invertPhoneNumber(userToList.getPhoneNumber()));
        }

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String returnMessage = getMessage(GROUP_KEY, keyListGroup, PROMPT,
                                          String.join(", ", usersList), sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));
    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyRenameGroup)
    @ResponseBody
    public Request renamePrompt(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        Group groupToRename = new Group();
        User sessionUser = userManager.findByInputNumber(inputNumber);
        String promptMessage;

        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        if (groupToRename.getGroupName().trim().length() == 0)
            promptMessage = getMessage(GROUP_KEY, keyRenameGroup, PROMPT + "1", sessionUser);
        else
            promptMessage = getMessage(GROUP_KEY, keyRenameGroup, PROMPT + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, GROUP_MENUS + keyRenameGroup + DO_SUFFIX + GROUPID_URL + groupId));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyRenameGroup + DO_SUFFIX)
    @ResponseBody
    public Request renameGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=GROUP_PARAM, required=true) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=true) String newName) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Group groupToRename;
        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        groupToRename.setGroupName(newName);
        groupToRename = groupManager.saveGroup(groupToRename);

        return menuBuilder(new USSDMenu(getMessage(GROUP_KEY, keyRenameGroup + DO_SUFFIX, PROMPT, newName, sessionUser),
                                        optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyAddNumber)
    @ResponseBody
    public Request addNumberInput(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String promptMessage = getMessage(GROUP_KEY, keyAddNumber, PROMPT, sessionUser);
        return menuBuilder(new USSDMenu(promptMessage, GROUP_MENUS + keyAddNumber + DO_SUFFIX + GROUPID_URL + groupId));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyAddNumber + DO_SUFFIX)
    @ResponseBody
    public Request addNummberToGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
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
        groupMembers.add(userManager.loadOrSaveUser(UserManager.convertPhoneNumber(numberToAdd)));
        sessionGroup.setGroupMembers(groupMembers);
        sessionGroup = groupManager.saveGroup(sessionGroup);

        return menuBuilder(new USSDMenu(getMessage(GROUP_KEY, keyAddNumber + DO_SUFFIX, PROMPT, sessionUser), optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyUnsubscribe)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                      @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        String menuKey = GROUP_KEY + "." + keyUnsubscribe + ".";

        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + PROMPT, sessionUser));
        promptMenu.addMenuOption(GROUP_MENUS + keyUnsubscribe + DO_SUFFIX + GROUPID_URL + groupId,
                                 getMessage(menuKey + OPTION + "confirm", sessionUser));
        promptMenu.addMenuOption(GROUP_MENUS + keyMenu + GROUPID_URL + groupId,
                                 getMessage(menuKey + OPTION + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyUnsubscribe + DO_SUFFIX)
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

        sessionGroup.getGroupMembers().remove(sessionUser);
        sessionGroup = groupManager.saveGroup(sessionGroup);

        String returnMessage = getMessage(GROUP_KEY, keyUnsubscribe + DO_SUFFIX, PROMPT, sessionUser);

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));

    }

    @RequestMapping(value = USSD_BASE + GROUP_MENUS + keyDelGroup)
    @ResponseBody
    public Request deleteConfirm(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                 @RequestParam(value=GROUP_PARAM, required=true) Long groupId) throws URISyntaxException {

        // todo: decide if this function will exist through USSD
        // todo: add confirmation screen
        // todo: check for user permissions
        // todo: generally make this more than a quick-and-dirty to clean up prototype database

        String returnMessage;
        User sessionUser = userManager.findByInputNumber(inputNumber);
        Group sessionGroup = new Group();
        try { sessionGroup = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        try {
            sessionGroup.setGroupMembers(new ArrayList<User>());
            sessionGroup = groupManager.saveGroup(sessionGroup);
            groupManager.deleteGroup(sessionGroup);
            returnMessage = getMessage(GROUP_KEY, keyDelGroup, PROMPT + ".success", sessionUser);
        } catch (Exception e) {
            returnMessage = getMessage(GROUP_KEY, keyDelGroup, PROMPT + ".error", sessionUser);
        }

        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser)));

    }

}
