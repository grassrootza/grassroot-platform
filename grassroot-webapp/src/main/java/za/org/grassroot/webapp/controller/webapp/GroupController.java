package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class GroupController extends BaseController {

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @RequestMapping("/group/view")
    public String viewGroupIndex(Model model, @RequestParam("groupId") Long groupId) {
        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        model.addAttribute("groupEvents", eventManagementService.getUpcomingEvents(group));
        model.addAttribute("subGroups", groupManagementService.getSubGroups(group));
        return "group/view";
    }

    @RequestMapping("/group/create")
    public String startGroupIndex(Model model) {
        Group newGroup = new Group();
        model.addAttribute("group", newGroup);
        return "group/create";
    }

    @RequestMapping(value = "/group/create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult,
                              HttpServletRequest request, RedirectAttributes redirectAttributes )
    {
        try {

            if (bindingResult.hasErrors()) {
                model.addAttribute("group", group);
                addMessage(model, MessageType.ERROR, "group.creation.error", request);
                return "group/create";
            }

            User groupCreator = getUserProfile();
            group.setCreatedByUser(groupCreator);
            group.addMember(groupCreator);

            for (User addedUser : group.getGroupMembers()) {
                if (addedUser != null) {
                    userManagementService.reformatPhoneNumber(addedUser);
                } else {
                    group.getGroupMembers().remove(addedUser);
                }
            }

            Group saveGroup = groupManagementService.saveGroup(group);

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success",new Object[]{saveGroup.getGroupName()},request);
            return "redirect:/home";

        } catch (Exception e)  {
            addMessage(model,MessageType.ERROR,"group.creation.error",request);
            return "group/create";
        }

    }

    @RequestMapping(value = "/group/create", params={"addMember"})
    public String addMember(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult) {
        List<User> groupMembers = group.getGroupMembers();
        groupMembers.add(new User());
        System.out.println("Number of users should be: " + groupMembers.size());
        group.setGroupMembers(groupMembers);
        return "group/create";
    }

    /*
    @RequestMapping(value = "/group/create", params={"removeMember"})
    public String removeMember(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult) {
        List<User> groupMembers = group.getGroupMembers();
        groupMembers.add(new User());
        System.out.println("Number of users should be: " + groupMembers.size());
        group.setGroupMembers(groupMembers);
        return "group/create";
    } */

}
