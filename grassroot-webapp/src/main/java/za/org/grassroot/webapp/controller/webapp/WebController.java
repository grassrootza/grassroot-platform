package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;


import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/07/28.
 * Stubbed out set of web functions just to enable demo / prototype of group creation etc that is easier than USSD
 * to do: most obvious is security and user persistence, in a way that is secure but simple to use
 * to do: should probably abstract out some idea of menus
 */

@Controller
public class WebController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @RequestMapping("/web/login")
    public String startSession(Model model) {
        return "login";
    }

    @RequestMapping("/web/start")
    public String userHome(@RequestParam(value="input_number") String inputNumber,
                           @RequestParam(value="display_name", required=false) String displayName, Model model) {

        User sessionUser = new User();
        String phoneNumber = User.convertPhoneNumber(inputNumber);

        try {
            sessionUser = userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        } catch (Exception e) {
            model.addAttribute("input_number", inputNumber);
            return "user_error";
        }

        model.addAttribute("user_id", sessionUser.getId());
        model.addAttribute("user_id", sessionUser.getId());
        model.addAttribute("groups", getGroupListFromUser(sessionUser));
        model.addAttribute("next_link", "group/details?user_id=" + sessionUser.getId() + "&group_id=");

        if (displayName != null) {
            sessionUser.setDisplayName(displayName);
            sessionUser = userRepository.save(sessionUser);
            model.addAttribute("display_name", sessionUser.getDisplayName());
            return "start";
        } else if (!sessionUser.hasName()) {
            model.addAttribute("input_number", inputNumber);
            return "user_name";
        } else {
            model.addAttribute("display_name", sessionUser.getDisplayName());
            return "start";
        }
    }


    @RequestMapping("/web/create_user")
    public String createUser(@RequestParam(value = "input_number") String inputNumber, Model model) {
        ///web/find_group

        User user = new User();
        user.setPhoneNumber(inputNumber);
        user = userRepository.save(user);

        model.addAttribute("phoneNumber", user.getPhoneNumber());
        return "user";

    }

    @RequestMapping(value="/web/find_group")
    public String listGroups(@RequestParam(value="user_id", required=true) Long userId,
                             @RequestParam(value="group_search", required=false) String searchTerm, Model model) {

        User sessionUser = userRepository.findOne(userId);

        model.addAttribute("user_id", userId);
        model.addAttribute("groups", getGroupListFromUser(sessionUser));
        model.addAttribute("next_link", "group/details?user_id=" + userId + "&group_id=");

        return "find_group";

    }

    @RequestMapping(value="/web/user/rename_form")
    public String userRenameForm(@RequestParam(value="user_id", required=true) Long userId,
                                 @RequestParam(value="group_id", required=true) Long groupId, Model model) {

        // todo: as elsewhere, check if user has permission to do this for these group members

        List<HashMap<String, String>> userList = getUserListFromGroup(groupRepository.findOne(groupId));

        model.addAttribute("users", userList);
        model.addAttribute("group_id", groupId);
        model.addAttribute("user_id", userId);

        return "user/rename_form";

    }

    @RequestMapping(value="/web/user/rename_do") // to handle multiple renames at once (key prototype feature)
    public String userRenameAction(@RequestParam(value="user_id", required=true) Long userId,
                                   @RequestParam(value="group_id", required=true) Long groupId,
                                   @RequestParam(value="users_selected", required=true) Long[] usersSelected,
                                   HttpServletRequest request, Model model) {

        User sessionUser = userRepository.findOne(userId);

        User userToRename;
        List<HashMap<String, String>> detailsToDisplay = new ArrayList<>();

        for (int i = 0; i < usersSelected.length; i++) {
            userToRename = userRepository.findOne(usersSelected[i]);
            userToRename.setDisplayName(request.getParameter("name_" + usersSelected[i]));
            userRepository.save(userToRename);
        }

        model.addAttribute("users", getUserListFromGroup(groupRepository.findOne(groupId)));
        model.addAttribute("user_id", userId);

        return "user/rename_do";

    }

    @RequestMapping(value="/web/group/details")
    public String detailGroup(@RequestParam(value="user_id", required=true) Long userId,
                              @RequestParam(value="group_id", required=true) Long groupId, Model model) {

        // todo: add in the authentication and group logic to check what rights this user has on this group

        Group groupToDisplay = groupRepository.findOne(groupId);

        model.addAttribute("users", getUserListFromGroup(groupToDisplay));
        model.addAttribute("user_id", userId);
        model.addAttribute("group_id", groupId);
        model.addAttribute("group_name", groupToDisplay.getName(""));
        model.addAttribute("created_date", groupToDisplay.getCreatedDateTime().toLocalDateTime().toString());
        model.addAttribute("created_by", groupToDisplay.getCreatedByUser().getDisplayName());

        return "group/details";

    }

    @RequestMapping(value="/web/group/new") // to create a new group (with forms & drop-down boxes)
    public String newGroupForm(@RequestParam(value="user_id", required=true) Long userId, Model model) {

        User sessionUser = new User();
        try { sessionUser = userRepository.findOne(userId); }
        catch (Exception e) { return "user_error"; }

        model.addAttribute("user_id", userId);
        model.addAttribute("user_name", sessionUser.getName(""));

        return "group/new_form";

    }

    @RequestMapping(value="/web/group/new2") // to process the input from the last one
    public String newGroupAction(@RequestParam(value="user_id", required=true) Long userId,
                                 @RequestParam(value="group_name", required=true) String groupName,
                                 HttpServletRequest request, Model model) {

        User sessionUser, userToCreate = new User();
        try { sessionUser = userRepository.findOne(userId); }
        catch (Exception e) { return "user_error"; }

        Group groupToCreate = new Group();
        groupToCreate.setCreatedByUser(sessionUser);
        groupToCreate.setGroupName(groupName);

        Map<String, Object> attributeMap = new HashMap<>();

        // todo: once have proper service layer, with common create group method, use for both this & ussd version
        // todo: reconsider how to handle the parameters, whether as array from param, or pass a counter in form, or ...

        List<User> groupMembers = new ArrayList<>();
        String phoneBase = "member_phone_";
        String nameBase = "member_name_";
        Integer counter = 1;

        while (request.getParameter(phoneBase + counter) != null) {
            String inputNumber = request.getParameter(phoneBase + counter);
            String inputName = request.getParameter(nameBase + counter);
            userToCreate = loadOrSaveUser(inputNumber);
            if (!userToCreate.hasName()) {
                userToCreate.setDisplayName(inputName);
                userToCreate = userRepository.save(userToCreate);
            }
            groupMembers.add(userToCreate);
            counter++;
        }

        if (!groupMembers.contains(sessionUser)) { groupMembers.add(sessionUser); }
        groupToCreate.setGroupMembers(groupMembers);
        groupToCreate = groupRepository.save(groupToCreate);

        attributeMap.put("user_id", userId);
        attributeMap.put("group_name", groupName);
        attributeMap.put("group_size", groupToCreate.getGroupMembers().size());
        model.addAllAttributes(attributeMap);
        return "group/new_action";

    }

    /**
     * Start auxilliary functions here. All should be moved to service layer.
     */

    public List<HashMap<String,String>> getUserListFromGroup(Group groupToDisplay) {

        // todo: work out best level of abstraction here (i.e., what to pass, what to return)

        List<User> usersToList = groupToDisplay.getGroupMembers();
        List<HashMap<String,String>> userList =new ArrayList<>();

        for (User userToList : usersToList) {
            HashMap<String,String> userAttributes = new HashMap<>();
            userAttributes.put("user_id", "" + userToList.getId());
            userAttributes.put("phone_number", User.invertPhoneNumber(userToList.getPhoneNumber()));
            userAttributes.put("display_name", userToList.getName("Unnamed user"));
            userList.add(userAttributes);
        }

        return userList;
    }

    public List<HashMap<String,String>> getGroupListFromUser(User userToDisplay) {

        List<Group> groupstoList = userToDisplay.getGroupsPartOf();
        List<HashMap<String,String>> groupList = new ArrayList<>();

        for (Group groupToList : groupstoList) {
            HashMap<String,String> groupAttributes = new HashMap<>();
            groupAttributes.put("group_id", "" + groupToList.getId());
            groupAttributes.put("group_name", groupToList.getGroupName());
            groupAttributes.put("created_by", groupToList.getCreatedByUser().getName("Unnamed user"));
            groupAttributes.put("number_users", "" + groupToList.getGroupMembers().size());
            groupList.add(groupAttributes);
        }

        return groupList;
    }

    // todo: NB: move this to service layer (copy and pasted code here from USSD controller, really need to not do that)

    public User loadOrSaveUser(String inputNumber) {
        String phoneNumber = User.convertPhoneNumber(inputNumber);
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
            User sessionUser = new User();
            sessionUser.setPhoneNumber(phoneNumber);
            return userRepository.save(sessionUser);
        } else {
            return userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        }
    }


}