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
import za.org.grassroot.webapp.model.web.GroupCreator;

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
        model.addAttribute("openToken", groupManagementService.groupHasValidToken(group));

        return "group/view";
    }

    @RequestMapping("/group/create")
    public String startGroupIndex(Model model, @RequestParam(value="parent", required=false) Long parentId) {

        GroupCreator groupCreator;

        if (parentId != null && groupManagementService.loadGroup(parentId) != null) {
            Group parent = groupManagementService.loadGroup(parentId);
            groupCreator = new GroupCreator(parent);
        } else {
            groupCreator = new GroupCreator();
        }

        groupCreator.addMember(getUserProfile()); // to remove ambiguity about group creator being part of group

        model.addAttribute("groupCreator", groupCreator);
        return "group/create";
    }

    @RequestMapping(value = "/group/create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("groupCreator") GroupCreator groupCreator, BindingResult bindingResult,
                              HttpServletRequest request, RedirectAttributes redirectAttributes )
    {
        try {

            User userCreator = getUserProfile();
            Group groupToSave = new Group("", userCreator);
            groupToSave.setGroupName(groupCreator.getGroupName());

            // groupToSave.addMember(groupCreator); // not doing this - so if creator removes themselves, they don't get messages

            if (bindingResult.hasErrors()) {
                model.addAttribute("group", groupCreator);
                addMessage(model, MessageType.ERROR, "group.creation.error", request);
                return "group/create";
            }

            for (User addedUser : groupCreator.getAddedMembers()) {
                if (addedUser.getPhoneNumber() != null && !addedUser.getPhoneNumber().trim().equals("")) {
                    User memberToAdd = userManagementService.loadOrSaveUser(addedUser.getPhoneNumber());
                    if (!memberToAdd.hasName() && addedUser.getDisplayName() != null) {
                        memberToAdd.setDisplayName(addedUser.getDisplayName());
                        memberToAdd = userManagementService.save(memberToAdd);
                    }
                    groupToSave.addMember(memberToAdd);
                }
            }

            // Getting the parent from the HttpRequest, since putting it in signature creates a mess, and not a group attr.
            if (groupCreator.getHasParent()) {
                Group parentGroup = groupManagementService.loadGroup(groupCreator.getParentId());
                log.info("This is a sub-group! Of: " + parentGroup.getGroupName());
                groupToSave.setParent(parentGroup);
            }

            log.info("About to try saving the new group ..." + groupToSave.toString());
            Group savedGroup = groupManagementService.saveGroup(groupToSave);

            // Seeing if we should create a token -- to switch this to using property-editor
            if (groupCreator.getGenerateToken()) {
                log.info("Generating a join code for the newly created group");
                savedGroup = groupManagementService.generateGroupToken(savedGroup, groupCreator.getTokenDaysValid());
            }

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success",new Object[]{savedGroup.getGroupName()},request);
            redirectAttributes.addAttribute("groupId", savedGroup.getId());
            return "redirect:view";

        } catch (Exception e)  {
            log.error("Exception thrown: " + e.toString());
            addMessage(model,MessageType.ERROR,"group.creation.error",request);
            return "group/create";
        }
    }

    @RequestMapping(value = "/group/create", params={"addMember"})
    public String addMember(Model model, @ModelAttribute("groupCreator") GroupCreator groupCreator) {
        List<User> groupMembers = groupCreator.getAddedMembers();
        groupMembers.add(new User());
        groupCreator.setAddedMembers(groupMembers);
        return "group/create";
    }


    @RequestMapping(value = "/group/create", params={"removeMember"})
    public String removeMember(Model model, @ModelAttribute("groupCreator") GroupCreator groupCreator,
                               @RequestParam("removeMember") Integer memberId) {

        log.info("Member ID obtained: " + memberId);
        List<User> groupMembers = groupCreator.getAddedMembers();
        groupMembers.remove(memberId.intValue());
        System.out.println("Number of users should be: " + groupMembers.size());
        groupCreator.setAddedMembers(groupMembers);
        return "group/create";
    }

    @RequestMapping(value = "/group/token")
    public String groupToken(Model model, @RequestParam("groupId") Long groupId,
                             @RequestParam(value="action", required=false) String action) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);

        if (groupManagementService.groupHasValidToken(group)) {
            if (action.equals("close")) {
                return "group/close_token";
            } else {
                return "group/extend_token";
            }
        } else {
            return "group/new_token";
        }
    }

    @RequestMapping(value = "/group/token", method = RequestMethod.POST)
    public String createGroupToken(Model model, @RequestParam("groupId") Long groupId, @RequestParam("action") String action,
                                   @RequestParam(value="days", required=false) Integer days,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId);

        switch (action) {
            case "create":
                group = groupManagementService.generateGroupToken(group, days);
                log.info("New token created with value: " + group.getGroupTokenCode());
                addMessage(redirectAttributes, MessageType.SUCCESS, "group.token.creation.success",
                           new Object[]{group.getGroupTokenCode()}, request);
                break;
            case "extend":
                group = groupManagementService.extendGroupToken(group, days);
                log.info("Token extended until: " + group.getTokenExpiryDateTime().toString());
                break;
            case "close":
                group = groupManagementService.invalidateGroupToken(group);
                log.info("Token closed!");
                break;
        }

        redirectAttributes.addAttribute("groupId", group.getId());
        return "redirect:/group/view";

    }

    @RequestMapping(value = "group/token_modify", method = RequestMethod.POST)
    public String modifyGroupToken(Model model, @RequestParam("groupId") Long groupId,
                                   @RequestParam("actionToTake") String actionToTake, BindingResult bindingResult) {


        return "redirect:/group/view";
    }

}
