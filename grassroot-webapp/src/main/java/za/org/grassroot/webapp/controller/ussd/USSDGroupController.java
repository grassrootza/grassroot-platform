package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManager;
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
     * To do: Add in validation and checking that group is valid, and user can call a meeting on it
     * To do: Add in extracting names and numbers from groups without names so users know what group it is
     * To do: Stub out remaining menus
     */

    @RequestMapping(value = "ussd/group")
    @ResponseBody
    public Request groupList(@RequestParam(value="msisdn", required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;

        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        String returnMessage = "Okay! Please pick one of the groups you belong to:";
        return new Request(returnMessage, userGroupMenu(sessionUser, "group/menu", true));

    }

    @RequestMapping(value = "ussd/group/menu")
    @ResponseBody
    public Request groupMenu(@RequestParam(value="msisdn", required=true) String inputNumber,
                             @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: check what permissions the user has and only display options that they can do
        // todo: think about slimming down the menu size

        String returnMessage = "Group selected. What would you like to do?";
        String groupIdP = "?groupId=" + groupId;
        Map<String, String> groupMenu = new LinkedHashMap<>();

        groupMenu.put("group/list" + groupIdP, "List group members");
        groupMenu.put("group/rename" + groupIdP, "Rename the group");
        groupMenu.put("group/addnumber" + groupIdP, "Add a phone number to the group");
        groupMenu.put("group/unsubscribe" + groupIdP, "Remove me from the group");
        groupMenu.put("group/delnumber" + groupIdP, "Remove a number from the group");
        groupMenu.put("group/delgroup" + groupIdP, "Delete this group (beta only)");

        return new Request(returnMessage, createMenu(groupMenu));

    }

    @RequestMapping(value = "ussd/group/list")
    @ResponseBody
    public Request listGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                             @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: only list users who are not the same as the user calling the function
        // todo: check if user has a display name, and, if so, just print the display name

        Group groupToList = new Group();
        try { groupToList = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        List<String> usersList = new ArrayList<>();
        for (User userToList : groupToList.getGroupMembers()) {
            usersList.add(UserManager.invertPhoneNumber(userToList.getPhoneNumber()));
        }

        String returnMessage = "Users in this group are: " + String.join(", ", usersList);

        return new Request(returnMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = "ussd/group/rename")
    @ResponseBody
    public Request renamePrompt(@RequestParam(value="msisdn", required=true) String inputNumber,
                                @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        Group groupToRename = new Group();
        String promptMessage;

        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        if (groupToRename.getGroupName().trim().length() == 0)
            promptMessage = "This group doesn't have a name yet. Please enter a name.";
        else
            promptMessage = "This group's current name is " + groupToRename.getGroupName() + ". What do you want to rename it?";

        return new Request(promptMessage, freeText("group/rename2?groupId=" + groupId));

    }

    @RequestMapping(value = "ussd/group/rename2")
    @ResponseBody
    public Request renameGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                               @RequestParam(value="groupId", required=true) Long groupId,
                               @RequestParam(value="request", required=true) String newName) throws URISyntaxException {

        // todo: make sure to check if user calling this is part of group (later: permissions logic)

        Group groupToRename = new Group();
        try { groupToRename = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        groupToRename.setGroupName(newName);
        groupToRename = groupManager.saveGroup(groupToRename);

        return new Request("Group successfully renamed to " + groupToRename.getGroupName(), new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/addnumber")
    @ResponseBody
    public Request addNumberInput(@RequestParam(value="msisdn", required=true) String inputNumber,
                                  @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: have a way to flag if returned here from next menu because number wasn't right
        // todo: load and display some brief descriptive text about the group, e.g., name and who created it
        // todo: add a lot of validation logic (user is part of group, has permission to adjust, etc etc).

        String promptMessage = "Okay, we'll add a number to this group. Please enter it below.";
        return new Request(promptMessage, freeText("group/addnumber2?groupId=" + groupId));

    }

    @RequestMapping(value = "ussd/group/addnumber2")
    @ResponseBody
    public Request addNummberToGroup(@RequestParam(value="msisdn", required=true) String inputNumber,
                                     @RequestParam(value="groupId", required=true) Long groupId,
                                     @RequestParam(value="request", required=true) String numberToAdd) throws URISyntaxException {

        // todo: make sure this user is part of the group and has permission to add people to it
        // todo: check the user-to-add isn't already part of the group, and, if so, notify the user who is adding
        // todo: build logic to handle it if the number submitted is badly formatted/doesn't work/etc

        Group sessionGroup = new Group();
        try { sessionGroup = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        List<User> groupMembers = sessionGroup.getGroupMembers();
        groupMembers.add(userManager.loadOrSaveUser(UserManager.convertPhoneNumber(numberToAdd)));
        sessionGroup.setGroupMembers(groupMembers);
        sessionGroup = groupManager.saveGroup(sessionGroup);

        return new Request("Done! The group has been updated.", new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/unsubscribe")
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value="msisdn", required=true) String inputNumber,
                                      @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: add in a brief description of group, e.g., who created it

        String promptMessage = "Are you sure you want to remove yourself from this group?";
        Option yesOption = new Option("Yes, take me off.", 1, 1, new URI(baseURI + "group/unsubscribe2?groupId=" + groupId), true);
        Option noOption = new Option("No, return to the last menu", 2, 2, new URI(baseURI + "group/menu?groupId=" + groupId), true);

        return new Request(promptMessage, Arrays.asList(yesOption, noOption));

    }

    @RequestMapping(value = "ussd/group/unsubscribe2")
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value="msisdn", required=true) String inputNumber,
                                 @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

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

        String returnMessage = "Done! You won't receive messages from that group anymore.";

        return new Request(returnMessage, new ArrayList<Option>());

    }

    @RequestMapping(value = "ussd/group/delgroup")
    @ResponseBody
    public Request deleteConfirm(@RequestParam(value="msisdn", required=true) String inputNumber,
                                 @RequestParam(value="groupId", required=true) Long groupId) throws URISyntaxException {

        // todo: add confirmation screen
        // todo: check for user permissions
        // todo: generally make this more than a quick-and-dirty to clean up prototype database

        String returnMessage;
        Group sessionGroup = new Group();
        try { sessionGroup = groupManager.loadGroup(groupId); }
        catch (Exception e) { return noGroupError; }

        try {
            sessionGroup.setGroupMembers(new ArrayList<User>());
            sessionGroup = groupManager.saveGroup(sessionGroup);
            groupManager.deleteGroup(sessionGroup);
            returnMessage = "Success! The group is gone.";
        } catch (Exception e) {
            returnMessage = "Nope, something went wrong with deleting that group.";
        }

        return new Request(returnMessage, new ArrayList<Option>());

    }

}
