package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class GroupController extends BaseController {

    Logger log = LoggerFactory.getLogger(GroupController.class);

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
    public String startGroupIndex(Model model, @RequestParam(value="parent", required=false) Long parentId) {

        Group newGroup = new Group();
        model.addAttribute("hasParent", false);

        if (parentId != null && groupManagementService.loadGroup(parentId) != null) {
            Group parent = groupManagementService.loadGroup(parentId);
            model.addAttribute("parentGroup", groupManagementService.loadGroup(parentId));
            model.addAttribute("hasParent", true);
        }

        model.addAttribute("group", newGroup);
        return "group/create";
    }

    @RequestMapping(value = "/group/create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("group") Group group, BindingResult bindingResult,
                              HttpServletRequest request, RedirectAttributes redirectAttributes )
    {
        try {

            User groupCreator = getUserProfile();
            Group groupToSave = new Group(group.getGroupName(), groupCreator);
            groupToSave.addMember(groupCreator);

            if (bindingResult.hasErrors()) {
                model.addAttribute("group", group);
                addMessage(model, MessageType.ERROR, "group.creation.error", request);
                return "group/create";
            }

            for (User addedUser : group.getGroupMembers()) {
                if (addedUser.getPhoneNumber() != null && !addedUser.getPhoneNumber().trim().equals("")) {
                    User memberToAdd = userManagementService.loadOrSaveUser(addedUser.getPhoneNumber());
                    if (!memberToAdd.hasName() && addedUser.getDisplayName() != null) {
                        memberToAdd.setDisplayName(addedUser.getDisplayName());
                        memberToAdd = userManagementService.save(memberToAdd);
                    }
                    groupToSave.addMember(memberToAdd);
                }
            }

            // Getting the parent from the HttpRequest, since using it
            String parentId = request.getParameter("parentId");
            if (parentId != null && !parentId.equals("0")) {
                Group parentGroup = groupManagementService.loadGroup(Long.parseLong(parentId));
                log.info("This is a sub-group! Of: " + parentGroup.getGroupName());
                groupToSave.setParent(parentGroup);
            }

            log.info("About to try saving the new group ..." + groupToSave.toString());
            Group savedGroup = groupManagementService.saveGroup(groupToSave);

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success",new Object[]{savedGroup.getGroupName()},request);
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

    @RequestMapping(value = "/group/token")
    public String groupToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);

        if (groupManagementService.groupHasValidToken(group)) {
            return "group/existing_token";
        } else {
            return "group/new_token";
        }

    }

    @RequestMapping(value = "/group/token", method = RequestMethod.POST)
    public String createGroupToken(Model model, @RequestParam("daysValid") Integer daysValid, @RequestParam("groupId") Long groupId,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId);
        group = groupManagementService.generateGroupToken(group, daysValid);

        System.out.println("New token created with value: " + group.getGroupTokenCode());

        addMessage(redirectAttributes,MessageType.SUCCESS,"group.token.creation.success",
                   new Object[]{group.getGroupTokenCode()},request);
        return "redirect:/group/view";

    }

    @RequestMapping(value = "group/token_modify", method = RequestMethod.POST)
    public String modifyGroupToken(Model model, @RequestParam("groupId") Long groupId,
                                   @RequestParam("actionToTake") String actionToTake, BindingResult bindingResult) {

        return "redirect:/group/view";
    }

}
