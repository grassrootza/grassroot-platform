package za.org.grassroot.meeting_organizer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.meeting_organizer.model.Group;
import za.org.grassroot.meeting_organizer.model.User;
import za.org.grassroot.meeting_organizer.service.repository.GroupRepository;
import za.org.grassroot.meeting_organizer.service.repository.UserRepository;

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
            groupAttributes.put("groupId", "" + groupToList.getId());
            groupAttributes.put("groupName", groupToList.getGroupName());
            groupAttributes.put("groupCreatedBy", groupToList.getCreatedByUser().getDisplayName());
            groupAttributes.put("numberUsers", "" + groupToList.getGroupMembers().size());
            groupList.add(groupAttributes);
        }

        model.addAttribute("groups", groupList);
        model.addAttribute("next_link", "group_details?user_id=" + userId + "&group_id=");

        return "find_group";

    }

    @RequestMapping(value="/web/group_details")
    public String detailGroup(@RequestParam(value="user_id", required=true) Integer userId,
                              @RequestParam(value="group_id", required=true) Integer groupId, Model model) {

        // todo: really need an auxilliary function in User class to return something intelligible if no display name

        Group groupToDisplay = groupRepository.findOne(groupId);
        List<User> usersToList = groupToDisplay.getGroupMembers();
        List<HashMap<String,String>> userList =new ArrayList<>();

        for (User userToList : usersToList) {
            HashMap<String,String> userAttributes = new HashMap<>();
            userAttributes.put("user_id", "" + userToList.getId());
            userAttributes.put("phone_number", User.invertPhoneNumber(userToList.getPhoneNumber()));
            String storedName = userToList.getDisplayName();
            String displayName = (storedName == null || storedName.trim().length() == 0) ? "Unnamed user" : storedName;
            userAttributes.put("display_name", displayName);
            userList.add(userAttributes);
        }

        model.addAttribute("users", userList);
        model.addAttribute("group_name", groupToDisplay.getGroupName());
        model.addAttribute("created_date", groupToDisplay.getCreatedDateTime().toLocalDateTime().toString());
        model.addAttribute("created_by", groupToDisplay.getCreatedByUser().getDisplayName());

        return "group_details";

    }

}