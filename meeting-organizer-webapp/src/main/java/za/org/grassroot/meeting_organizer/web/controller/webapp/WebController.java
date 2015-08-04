package za.org.grassroot.meeting_organizer.web.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.meeting_organizer.domain.Group;
import za.org.grassroot.meeting_organizer.domain.User;
import za.org.grassroot.meeting_organizer.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.repository.UserRepository;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        if (displayName != null) {
            sessionUser.setDisplayName(displayName);
            sessionUser = userRepository.save(sessionUser);
            model.addAttribute("display_name", sessionUser.getDisplayName());
            return "start";
        } else if (sessionUser.getDisplayName() == null || sessionUser.getDisplayName().trim().length() == 0) {
            model.addAttribute("input_number", inputNumber);
            return "user_name";
        } else {
            model.addAttribute("display_name", sessionUser.getDisplayName());
            return "start";
        }
    }

    @RequestMapping(value="/web/find_group")
    public String listGroups(@RequestParam(value="user_id", required=true) Integer userId,
                             @RequestParam(value="group_search", required=false) String searchTerm, Model model) {

        User sessionUser = userRepository.findOne(userId);
        List<Group> groupstoList = sessionUser.getGroupsPartOf();
        List<HashMap<String,String>> groupList = new ArrayList<>();

        for (Group groupToList : groupstoList) {
            HashMap<String,String> groupAttributes = new HashMap<>();
            groupAttributes.put("group_id", "" + groupToList.getId());
            groupAttributes.put("group_name", groupToList.getGroupName());
            groupAttributes.put("created_by", groupToList.getCreatedByUser().getName("Unnamed user"));
            groupAttributes.put("number_users", "" + groupToList.getGroupMembers().size());
            groupList.add(groupAttributes);
        }

        model.addAttribute("groups", groupList);
        model.addAttribute("next_link", "group_details?user_id=" + userId + "&group_id=");

        return "find_group";

    }

    @RequestMapping(value="/web/group_details")
    public String detailGroup(@RequestParam(value="user_id", required=true) Integer userId,
                              @RequestParam(value="group_id", required=true) Integer groupId, Model model) {

        // todo: add in the authentication and group logic to check what rights this user has on this group

        Group groupToDisplay = groupRepository.findOne(groupId);
        List<User> usersToList = groupToDisplay.getGroupMembers();
        List<HashMap<String,String>> userList =new ArrayList<>();

        for (User userToList : usersToList) {
            HashMap<String,String> userAttributes = new HashMap<>();
            userAttributes.put("user_id", "" + userToList.getId());
            userAttributes.put("phone_number", User.invertPhoneNumber(userToList.getPhoneNumber()));
            userAttributes.put("display_name", userToList.getName("Unnamed user"));
            userList.add(userAttributes);
        }

        model.addAttribute("users", userList);
        model.addAttribute("group_name", groupToDisplay.getGroupName());
        model.addAttribute("created_date", groupToDisplay.getCreatedDateTime().toLocalDateTime().toString());
        model.addAttribute("created_by", groupToDisplay.getCreatedByUser().getDisplayName());

        return "group_details";

    }

    @RequestMapping(value="/web/user_rename") // to handle multiple renames at once (key prototype feature)
    public String userRename(@RequestParam(value="user_id", required=true) Integer userId) {

        User sessionUser = userRepository.findOne(userId);
        return "404";

    }

    @RequestMapping(value="/web/group/new") // to create a new group (with forms & drop-down boxes)
    public String newGroupForm(@RequestParam(value="user_id", required=true) Integer userId) {

        return "404";

    }

    @RequestMapping(value="/web/group/new2") // to process the input from the last one
    public String newGroupAction(@RequestParam(value="user_id", required=true) Integer userId) {

        return "404";

    }

}