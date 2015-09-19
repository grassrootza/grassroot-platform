package za.org.grassroot.webapp.controller.webapp;

import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.list.LazyList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;
import za.org.grassroot.webapp.validation.GroupWrapperValidator;
import za.org.grassroot.webapp.validation.UserValidator;

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

    @Autowired
    @Qualifier("groupWrapperValidator")
    private Validator groupWrapperValidator;

    /*
    Binding validators to model attributes. We could just user groupWrapper for both Creator and Modifier, but in the
    future we may need to handle differently, and the redundant code is minimal, so am making two calls
     */
    @InitBinder("groupCreator")
    private void initCreatorBinder(WebDataBinder binder) { binder.setValidator(groupWrapperValidator); }

    @InitBinder("groupModifier")
    private void initModifierBinder(WebDataBinder binder) { binder.setValidator(groupWrapperValidator); }

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

        GroupWrapper groupCreator;

        if (parentId != null && groupManagementService.loadGroup(parentId) != null) {
            Group parent = groupManagementService.loadGroup(parentId);
            groupCreator = new GroupWrapper(parent);
        } else {
            groupCreator = new GroupWrapper();
        }

        groupCreator.addMember(getUserProfile()); // to remove ambiguity about group creator being part of group

        model.addAttribute("groupCreator", groupCreator);
        return "group/create";
    }

    @RequestMapping(value = "/group/create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                              BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes)
    {
        try {

            User userCreator = getUserProfile();
            Group groupToSave = new Group("", userCreator);
            groupToSave.setGroupName(groupCreator.getGroupName());

            // groupToSave.addMember(groupCreator); // not doing this - so if creator removes themselves, they don't get messages

            if (bindingResult.hasErrors()) {
                model.addAttribute("groupCreator", groupCreator);
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
    public String addMember(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                            BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            // print out the error
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        } else {
            groupCreator.setAddedMembers(addMember(groupCreator));
        }
        return "group/create";
    }


    @RequestMapping(value = "/group/create", params={"removeMember"})
    public String removeMember(Model model, @ModelAttribute("groupCreator") GroupWrapper groupCreator,
                               @RequestParam("removeMember") Integer memberId) {

        groupCreator.setAddedMembers(removeMember(groupCreator, memberId));
        return "group/create";
    }

    /*
    Methods for handling group modification
    Major todo: permissions, throughout
     */

    @RequestMapping(value = "/group/modify", method = RequestMethod.POST, params={"group_modify"})
    public String modifyGroup(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        log.info("Okay, modifying this group: " + group.toString());

        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(group);

        log.info("The GroupWrapper now contains: " + groupModifier.getGroup().toString());

        model.addAttribute("groupModifier", groupModifier);
        return "group/modify";

    }

    @RequestMapping(value = "/group/modify", params={"addMember"})
    public String addMemberModify(Model model, @ModelAttribute("groupModifier") @Validated GroupWrapper groupModifier,
                                  BindingResult bindingResult, HttpServletRequest request) {
        // todo: check permissions
        if (bindingResult.hasErrors()) {
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        } else {
            groupModifier.setAddedMembers(addMember(groupModifier));
        }
        return "group/modify";
    }


    @RequestMapping(value = "/group/modify", params={"removeMember"})
    public String removeMemberModify(Model model, @ModelAttribute("groupModifier") GroupWrapper groupModifier,
                               @RequestParam("removeMember") Integer memberId) {

        // todo: check permissions (either in validator or in service layer)
        groupModifier.setAddedMembers(removeMember(groupModifier, memberId));
        return "group/modify";
    }


    @RequestMapping(value = "/group/modify", method = RequestMethod.POST)
    public String modifyGroupDo(Model model, @ModelAttribute("groupModifier") @Validated GroupWrapper groupModifier,
                                BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        try {

            if (bindingResult.hasErrors()) {
                model.addAttribute("groupModifier", groupModifier);
                addMessage(model, MessageType.ERROR, "group.modification.error", request);
                return "group/modify";
            }

            // todo: put in various different kinds of error handling
            // todo: work out a low-cost way to check which, if any, of the fields have changed

            log.info("We've been passed this group to modify: " + groupModifier.getGroup());

            Group groupToUpdate = groupManagementService.loadGroup(groupModifier.getGroup().getId());
            groupToUpdate.setGroupName(groupModifier.getGroupName());

            log.info("About to alter users on this group: " + groupToUpdate.toString());

            // this is a bit brute force, to remove members we wipe the slate clean, but alternate is to do this
            // when the user clicks 'remove', and then it's going to get messy with persistence & repo calls
            // still, there may be a better way to do this -- a quick & fast way to check if a user in groupToUpdate's
            // list is not in the new list (but, careful about changed objects, what equals does, excess database calls)

            groupToUpdate.setGroupMembers(LazyList.lazyList(new ArrayList<>(), FactoryUtils.instantiateFactory(User.class)));

            for (User userToUpdate : groupModifier.getAddedMembers()) {
                groupToUpdate = addMemberGroup(groupToUpdate, userToUpdate, true);
            }

            Group savedGroup = groupManagementService.saveGroup(groupToUpdate);

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.update.success",new Object[]{savedGroup.getGroupName()},request);
            redirectAttributes.addAttribute("groupId", savedGroup.getId());
            return "redirect:view";

        } catch (Exception e)  {
            log.error("Exception thrown: " + e.toString());
            addMessage(model,MessageType.ERROR,"group.creation.error",request);
            return "group/modify";
        }
    }

    /*
    Helper methods for handling user addition and updating
     */

    private Group addMemberGroup(Group group, User member, boolean overwrite) {

        if (member.getPhoneNumber() != null && !member.getPhoneNumber().trim().equals("")) {
            User memberToAdd = userManagementService.loadOrSaveUser(member.getPhoneNumber());
            if (member.getDisplayName() != null && (overwrite || !memberToAdd.hasName())) {
                memberToAdd.setDisplayName(member.getDisplayName());
                memberToAdd = userManagementService.save(memberToAdd);
            }

            // todo: allow phone number change, so mistakes can be corrected, but close this off soon (find a UX solution)
            if (overwrite) {
                memberToAdd.setPhoneNumber(PhoneNumberUtil.convertPhoneNumber(member.getPhoneNumber()));
            }

            group.addMember(memberToAdd);

        }

        return group;

    }

    private List<User> addMember(GroupWrapper groupWrapper) {

        List<User> groupMembers = groupWrapper.getAddedMembers();
        groupMembers.add(new User());
        return groupMembers;

    }

    private List<User> removeMember(GroupWrapper groupWrapper, Integer memberId) {

        List<User> groupMembers = groupWrapper.getAddedMembers();
        groupMembers.remove(memberId.intValue());
        System.out.println("Number of users should be: " + groupMembers.size());
        return groupMembers;

    }

    /*
    Methods for handling join tokens
     */

    @RequestMapping(value = "/group/modify", method = RequestMethod.POST, params={"token_create"})
    public String newToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/new_token";
    }

    @RequestMapping(value = "/group/modify", method = RequestMethod.POST, params={"token_extend"})
    public String extendToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/extend_token";
    }

    @RequestMapping(value = "/group/modify", method = RequestMethod.POST, params={"token_cancel"})
    public String cancelToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/close_token";
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
