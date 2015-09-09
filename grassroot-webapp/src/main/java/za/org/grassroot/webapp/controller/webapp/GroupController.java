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
import za.org.grassroot.core.domain.Group;

import za.org.grassroot.core.domain.User;
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

    @RequestMapping("/groups/view-group")
    public String viewGroupIndex(Model model, @RequestParam("groupId") Long groupId) {
        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        // model.addAttribute("groupMembers", group.getGroupMembers());
        return "group/view";
    }

    @RequestMapping("/groups/start-group")
    public String startGroupIndex(Model model) {
        Group newGroup = new Group();
        // newGroup.getGroupMembers().add(new User());
        model.addAttribute("group", newGroup);
        // model.addAttribute("groupMembers", newGroup.getGroupMembers());
        return "group/create";
    }

    @RequestMapping(value = "/groups/start-group", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult,
                              HttpServletRequest request, RedirectAttributes redirectAttributes )
    {
        try {

            if (bindingResult.hasErrors()) {
                model.addAttribute("group", group);
                addMessage(model, MessageType.ERROR, "group.creation.error.message", request);
                return "group/create";
            }

            User groupCreator = getUserProfile();
            group.setCreatedByUser(groupCreator);
            group.addMember(groupCreator);

            group.getGroupMembers().forEach(userManagementService::reformatPhoneNumber); // this should do saves on all

            Group saveGroup = groupManagementService.saveGroup(group);

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success.message",new Object[]{saveGroup.getGroupName()},request);
            return "redirect:/home";

        } catch (Exception e)  {
            addMessage(model,MessageType.ERROR,"group.creation.error.message",request);
            return "group/create";
        }

    }

    @RequestMapping(value = "/groups/start-group", params={"addMember"})
    public String addMember(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult) {
        List<User> groupMembers = group.getGroupMembers();
        groupMembers.add(new User());
        System.out.println("Number of users should be: " + groupMembers.size());
        group.setGroupMembers(groupMembers);
        return "group/create";
    }

}
