package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class GroupController extends BaseController {

    @Autowired
    GroupManagementService groupManagementService;

    @RequestMapping("/groups/start-group")
    public String startGroupIndex(Model model) {
        model.addAttribute("group", new Group());
        return "group-create";
    }

    @RequestMapping(value = "/groups/start-group", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes )
    {
        try {

            if(bindingResult.hasErrors())
            {
                model.addAttribute("group", group);
                addMessage(model, MessageType.ERROR, "group.creation.error.message", request);
                return "group-create";
            }

            User groupCreator = getUserProfile();
            group.setCreatedByUser(groupCreator);
            group.addMember(groupCreator);
            Group saveGroup = groupManagementService.saveGroup(group);

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success.message",new Object[]{saveGroup.getGroupName()},request);
            return "redirect:/home";

        }catch (Exception e)
        {
            addMessage(model,MessageType.ERROR,"group.creation.error.message",request);
            return "group-create";
        }

    }

}
