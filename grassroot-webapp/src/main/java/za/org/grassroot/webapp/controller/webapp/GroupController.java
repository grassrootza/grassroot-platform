package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Lesetse Kimwaga
 */
@Controller
<<<<<<< HEAD
@SessionAttributes({"groupCreator", "groupModifier"})

=======
@RequestMapping("/group/")
@SessionAttributes({"groupCreator", "groupModifier"})
>>>>>>> upstream/master
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

    private boolean isUserPartOfGroup(User sessionUser, Group group) {
        // todo: do this from cache so it's not slow ...
        User userFromDb = userManagementService.getUserById(sessionUser.getId());
        return userFromDb.getGroupsPartOf().contains(group);
    }

    @PreAuthorize("hasAnyRole('ROLE_DOESNT_EXIST')")
    private Group secureLoadGroup(Long id) {
        log.info("Inside secureLoadGroup ...");
        return groupManagementService.loadGroup(id);
    }

    /*
    First method is for users brand new and without any group membership, and/or later for any user, to find & join group
     */

    // todo: work out how to prevent a computer just cycling through all possible numbers on the token code

    @RequestMapping(value = "search", method = RequestMethod.POST)
    public String searchForGroup(Model model, @RequestParam String searchTerm) {
        Group groupByToken = groupManagementService.getGroupByToken(searchTerm);
        if (groupByToken != null) {
            model.addAttribute("group", groupByToken);
        } else {
            // todo: deal with case sensitivity
            List<Group> possibleGroups = groupManagementService.findDiscoverableGroups(searchTerm);
            if (!possibleGroups.isEmpty())
                model.addAttribute("groupCandidates", possibleGroups);
            else
                model.addAttribute("noCandidates", true);
        }
        return "group/results";
    }

    @RequestMapping(value = "join", method = RequestMethod.POST)
    public String joinGroup(Model model, @RequestParam Long groupId, HttpServletRequest request) {
        // todo: think through security, privacy, etc
        groupManagementService.addGroupMember(groupId, getUserProfile().getId());
        addMessage(model, MessageType.SUCCESS, "group.join.success", request);
        return viewGroupIndex(model, groupId);
    }

    /*
    Next methods are to view a group, core part of interface
     */


    // @PreAuthorize("hasPermission(#groupId, ' za.org.grassroot.core.domain.Group', 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS')")
    @RequestMapping("view")
    public String viewGroupIndex(Model model, @RequestParam("groupId") Long groupId) {

        // todo: all sorts of user/group permission checking
        User user = getUserProfile();
        log.info("Loading group, user has this role ..." + user.getRoles());

        // at low user numbers, this is taking ~0 msec, but keeping the logs in so we have a record
        Long startTime = System.currentTimeMillis();
        // Group group = secureLoadGroup(groupId);
        Group group = groupManagementService.loadGroup(groupId); // change to secureLoadGroup once retro permissions done
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Checking group membership took ... %d msec", endTime - startTime));

        model.addAttribute("group", group);
        model.addAttribute("hasParent", groupManagementService.hasParent(group));
        model.addAttribute("groupMeetings", eventManagementService.getUpcomingMeetings(group));
        model.addAttribute("groupVotes", eventManagementService.getUpcomingVotes(group));
        model.addAttribute("subGroups", groupManagementService.getSubGroups(group));
        model.addAttribute("openToken", groupManagementService.groupHasValidToken(group));
        model.addAttribute("canDeleteGroup", groupManagementService.canUserMakeGroupInactive(user, group));
        model.addAttribute("canMergeWithOthers", groupManagementService.isGroupCreatedByUser(groupId, user));

        return "group/view";
    }

    /*
    Group creation methods
     */

    @RequestMapping("create")
    public String startGroupIndex(Model model, @RequestParam(value="parent", required=false) Long parentId) {

        GroupWrapper groupCreator;

        if (parentId != null) {
            Group parent = groupManagementService.loadGroup(parentId);
            if(parent!=null){
                groupCreator = new GroupWrapper(parent);
            }else {
                groupCreator = new GroupWrapper();
            }
        } else {
            groupCreator = new GroupWrapper();
        }

        groupCreator.addMember(getUserProfile()); // to remove ambiguity about group creator being part of group

        model.addAttribute("groupCreator", groupCreator);
        return "group/create";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                              BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes)
    {


            if (bindingResult.hasErrors()) {
                model.addAttribute("groupCreator", groupCreator);
                addMessage(model, MessageType.ERROR, "group.creation.error", request);
                return "group/create";
            }

            User userCreator = getUserProfile();
            Group groupToSave = groupManagementService.createNewGroup(userCreator, groupCreator.getGroupName());

            for (User addedUser : groupCreator.getAddedMembers()) {
                if (addedUser.getPhoneNumber() != null && !addedUser.getPhoneNumber().trim().equals("")) {
                    User memberToAdd = userManagementService.loadOrSaveUser(addedUser.getPhoneNumber());
                    if (!memberToAdd.hasName() && addedUser.getDisplayName() != null) {
                        memberToAdd.setDisplayName(addedUser.getDisplayName());
                        memberToAdd = userManagementService.save(memberToAdd);
                    }
                    groupManagementService.addGroupMember(groupToSave, memberToAdd);
                }
            }

            // Getting the parent from the HttpRequest, since putting it in signature creates a mess, and not a group attr.
            if (groupCreator.getHasParent()) {
                Group parentGroup = groupManagementService.loadGroup(groupCreator.getParentId());
                log.info("This is a sub-group! Of: " + parentGroup.getGroupName());
                groupToSave = groupManagementService.linkSubGroup(groupToSave, parentGroup);
            }

            // Seeing if we should create a token -- to switch this to using property-editor
            if (groupCreator.getGenerateToken()) {
                log.info("Generating a join code for the newly created group");
                groupToSave = groupManagementService.generateGroupToken(groupToSave, groupCreator.getTokenDaysValid());
            }

            log.info("Completed storing the new group ..." + groupToSave.toString());

            addMessage(redirectAttributes,MessageType.SUCCESS,"group.creation.success",new Object[]{groupToSave.getGroupName()},request);
            redirectAttributes.addAttribute("groupId", groupToSave.getId());
            return "redirect:view";


    }

    @RequestMapping(value = "create", params={"addMember"})
    public String addMember(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                            BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {

            log.debug("binding_error", "");
            // print out the error
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        } else {
            log.debug("binding_error", "");
            groupCreator.setAddedMembers(addMember(groupCreator));
        }
        return "group/create";
    }


    @RequestMapping(value = "create", params={"removeMember"})
    public String removeMember(Model model, @ModelAttribute("groupCreator") GroupWrapper groupCreator,
                               @RequestParam("removeMember") Integer memberId) {

        groupCreator.setAddedMembers(removeMember(groupCreator, memberId));
        return "group/create";
    }

    /*
    Methods for handling group modification
    Major todo: permissions, throughout
     */

    @RequestMapping(value = "modify", method = RequestMethod.POST, params={"group_modify"})
    public String modifyGroup(Model model, @RequestParam("groupId") Long groupId) {

        // todo: replace getGroupMembers with actual thing
        Group group = groupManagementService.loadGroup(groupId);
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");

        log.info("Okay, modifying this group: " + group.toString());

        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(group);

        log.info("The GroupWrapper now contains: " + groupModifier.getGroup().toString());

        model.addAttribute("groupModifier", groupModifier);
        return "group/modify";

    }

    @RequestMapping(value = "modify", params={"addMember"})
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


    @RequestMapping(value = "modify", params={"removeMember"})
    public String removeMemberModify(Model model, @ModelAttribute("groupModifier") GroupWrapper groupModifier,
                               @RequestParam("removeMember") Integer memberId) {

        // todo: check permissions (either in validator or in service layer)
        groupModifier.setAddedMembers(removeMember(groupModifier, memberId));
        return "group/modify";
    }


    @RequestMapping(value = "modify", method = RequestMethod.POST)
    public String modifyGroupDo(Model model, @ModelAttribute("groupModifier") @Validated GroupWrapper groupModifier,
                                BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // try {

            if (bindingResult.hasErrors()) {
                model.addAttribute("groupModifier", groupModifier);
                addMessage(model, MessageType.ERROR, "group.modification.error", request);
                return "group/modify";
            }

            // todo: put in various different kinds of error handling

            Group groupToUpdate = groupManagementService.loadGroup(groupModifier.getGroup().getId());
            groupToUpdate = groupManagementService.renameGroup(groupToUpdate, groupModifier.getGroupName());

            // todo: again, do proper permission check and / or getUserProfile() isn't causing inefficiency
            if (!isUserPartOfGroup(getUserProfile(), groupToUpdate)) throw new AccessDeniedException("");

            // we have to do a work around of thymeleaf here, which obliterates all the data that we don't create hidden
            // fields to store, so that the users we get back only have display names, id's and phone numbers
            // hence we need to construct a list of fleshed out user objects, and then pass that to service layer

            List<User> updatedUserList = new ArrayList<>();

            for (User userToAdd : groupModifier.getAddedMembers()) {
                User storedUser = userManagementService.loadOrSaveUser(userToAdd);
                updatedUserList.add(storedUser);
            }

            log.info("These are the users passed from the store: " + updatedUserList);

            Group savedGroup = groupManagementService.addRemoveGroupMembers(groupToUpdate, updatedUserList);

            addMessage(redirectAttributes, MessageType.SUCCESS, "group.update.success", new Object[]{savedGroup.getGroupName()}, request);
            redirectAttributes.addAttribute("groupId", savedGroup.getId());
            return "redirect:view";

       /* } catch (Exception e)  {
           log.error("Exception thrown: " + e.toString());
            addMessage(model,MessageType.ERROR,"group.creation.error",request);
            return "group/modify";
        }*/
    }

    /*
    Helper methods for handling user addition and updating
     */

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

    @RequestMapping(value = "modify", method = RequestMethod.POST, params={"token_create"})
    public String newToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");
        model.addAttribute("group", group);
        return "group/new_token";
    }

    @RequestMapping(value = "modify", method = RequestMethod.POST, params={"token_extend"})
    public String extendToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/extend_token";
    }

    @RequestMapping(value = "modify", method = RequestMethod.POST, params={"token_cancel"})
    public String cancelToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/close_token";
    }

    @RequestMapping(value = "token", method = RequestMethod.POST)
    public String createGroupToken(Model model, @RequestParam("groupId") Long groupId, @RequestParam("action") String action,
                                   @RequestParam(value="days", required=false) Integer days,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId);

        switch (action) {
            case "create":
                if (days == 0)
                    group = groupManagementService.generateGroupToken(group);
                else
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

    @RequestMapping(value = "modify", params={"group_language"})
    public String requestGroupLanguage(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);

        List<Map.Entry<String, String>> languages = new ArrayList<>(userManagementService.getImplementedLanguages().entrySet());

        model.addAttribute("group", group);
        model.addAttribute("languages", languages);

        return "group/language_pick";

    }

    @RequestMapping(value = "language", method = RequestMethod.POST)
    public String setGroupLanguage(Model model, @RequestParam("groupId") Long groupId, @RequestParam("locale") String locale,
                                   @RequestParam(value = "includeSubGroups", required = false) boolean includeSubGroups,
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: add permissions checking, exception handling, etc.

        log.info("Okay, setting the language to: " + locale);

        Group group = groupManagementService.loadGroup(groupId);

        if (!includeSubGroups) {
            group = groupManagementService.setGroupDefaultLanguage(group, locale);
        } else {
            group = groupManagementService.setGroupAndSubGroupDefaultLanguage(group, locale);
        }

        // todo: there is probably a more efficient way to do this than the redirect
        redirectAttributes.addAttribute("groupId", group.getId());
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.language.success", request);
        return "redirect:/group/view";
    }

    /*
    Methods for handling group linking to a parent (as observing that users often create group first, link later)
     */

    @RequestMapping(value="parent")
    public String listPossibleParents(Model model, @RequestParam("groupId") Long groupId,
                                      HttpServletRequest request, RedirectAttributes redirectAttributes) {
        // todo: check permissions, handle exceptions (in fact, on view group page), etc.
        log.info("Looking for possible parents of group with ID: " + groupId);

        Group groupToMakeChild = groupManagementService.loadGroup(groupId);

        List<Group> userGroups = groupManagementService.getGroupsFromUser(getUserProfile());
        userGroups.remove(groupToMakeChild);
        List<Group> possibleParents = new ArrayList<>(userGroups);

        for (Group possibleParent : userGroups) {
            log.info("Checking if this group can be a parent ..." + possibleParent.getGroupName());
            if (groupManagementService.isGroupAlsoParent(groupToMakeChild, possibleParent)) {
                log.info("Whoops, this group: " + groupToMakeChild.getGroupName() + " is in the parent tree of the group: " +
                        possibleParent.getGroupName());
                possibleParents.remove(possibleParent);
            } else {
                log.info("Safe ... this group: " + groupToMakeChild.getGroupName() + " is not in the parent tree of the group: " +
                        possibleParent.getGroupName());
            }
        }

        if (!possibleParents.isEmpty()) {
            log.info("The group (with ID " + groupId + ") has some possible parents, in fact this many: " + possibleParents.size());
            model.addAttribute("group", groupToMakeChild);
            model.addAttribute("possibleParents", possibleParents);
            return "group/parent";
        } else {
            // add an error message
            log.info("The group does not have possible parents");
            addMessage(redirectAttributes, MessageType.ERROR, "group.parents.none", request);
            redirectAttributes.addAttribute("groupId", groupId);
            return "redirect:view";
        }
    }

    @RequestMapping(value="link", method=RequestMethod.POST)
    public String linkToParent(Model model, @RequestParam("groupId") Long groupId, @RequestParam("parentId") Long parentId,
                               RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: permissions, exceptions, etc.
        // todo: check if need to send a request to parent for permission to bind

        Group group = groupManagementService.loadGroup(groupId);
        Group parent = groupManagementService.loadGroup(parentId);

        group = groupManagementService.linkSubGroup(group, parent);

        // addMessage(model, MessageType.SUCCESS, "group.parent.success", request);
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.parent.success", request);
        redirectAttributes.addAttribute("groupId", groupId);
        return "redirect:view";

    }

    /*
    Methods to consolidate groups
    todo: add role authorizations, etc
     */

    @RequestMapping(value = "consolidate/select")
    public String selectConsolidate(Model model, @RequestParam("groupId") Long groupId,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {

        User user = getUserProfile();

        List<Group> candidateGroups = groupManagementService.getMergeCandidates(user, groupId);
        if (candidateGroups == null || candidateGroups.size() == 0) {
            addMessage(redirectAttributes, MessageType.ERROR, "group.merge.no-candidates", request);
            redirectAttributes.addAttribute("groupId", groupId);
            return "redirect:view";
        } else {
            model.addAttribute("group1", groupManagementService.loadGroup(groupId));
            model.addAttribute("candidateGroups", candidateGroups);
            return "group/consolidate_select";
        }
    }


    @RequestMapping(value = "consolidate/confirm", method = RequestMethod.POST)
    public String consolidateGroupsConfirm(Model model, @RequestParam("groupId1") Long groupId1, @RequestParam("groupId2") Long groupId2,
                                           @RequestParam("order") String order, @RequestParam(value="leaveActive", required=false) boolean leaveActive,
                                           HttpServletRequest request) {

        Group groupInto;
        Group groupFrom;
        Long[] orderedIds;

        switch (order) {
            case "small_to_large":
                orderedIds = groupManagementService.orderPairByNumberMembers(groupId1, groupId2);
                groupInto = groupManagementService.loadGroup(orderedIds[0]);
                groupFrom = groupManagementService.loadGroup(orderedIds[1]);
                break;
            case "2_into_1":
                groupInto = groupManagementService.loadGroup(groupId1);
                groupFrom = groupManagementService.loadGroup(groupId2);
                break;
            case "1_into_2":
                groupInto = groupManagementService.loadGroup(groupId2);
                groupFrom = groupManagementService.loadGroup(groupId1);
                break;
            default:
                orderedIds = groupManagementService.orderPairByNumberMembers(groupId1, groupId2);
                groupInto = groupManagementService.loadGroup(orderedIds[0]);
                groupFrom = groupManagementService.loadGroup(orderedIds[1]);
        }

        model.addAttribute("groupInto", groupInto);
        model.addAttribute("groupFrom", groupFrom);
        model.addAttribute("numberFrom", groupManagementService.getGroupSize(groupFrom, false));
        model.addAttribute("leaveActive", leaveActive);

        return "group/consolidate_confirm";
    }

    @RequestMapping(value = "consolidate/do", method = RequestMethod.POST)
    public String consolidateGroupsDo(Model model, @RequestParam("groupInto") Long groupIdInto, @RequestParam("groupFrom") Long groupIdFrom,
                                      @RequestParam(value="leaveActive", required=false) boolean leaveActive, RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: add error handling
        Group consolidatedGroup = groupManagementService.mergeGroupsSpecifyOrder(groupIdInto, groupIdFrom, !leaveActive);
        Integer[] userCounts = new Integer[] { groupManagementService.loadGroup(groupIdFrom).getGroupMembers().size(),
                groupManagementService.getGroupSize(consolidatedGroup, false)};
        redirectAttributes.addAttribute("groupId", consolidatedGroup.getId());
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.merge.success", userCounts, request);
        return "redirect:/group/view";
    }

    /*
    Methods to handle group deactivation and group unsubscribe
    Simple method to delete a group, if it was recently created and this is the creating user, after a confirmation screen
    todo: add Spring Security annotations to stop unauthorized users from even accessing the URLs
     */
    @RequestMapping(value = "modify", params={"group_delete"})
    public String confirmDelete(Model model, @RequestParam("groupId") Long groupId, HttpServletRequest request) {

        User user = getUserProfile();
        Group group = groupManagementService.loadGroup(groupId);

        if (groupManagementService.canUserMakeGroupInactive(user, group)) {
            model.addAttribute("group", group);
            return "group/delete_confirm";
        } else {
            addMessage(model, MessageType.ERROR, "group.delete.error", request);
            return "group/view";
        }

    }

    @RequestMapping(value = "delete")
    public String deleteGroup(Model model, @RequestParam("groupId") Long groupId, @RequestParam("confirm_field") String confirmText,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId);

        if (groupManagementService.canUserMakeGroupInactive(getUserProfile(), group) && confirmText.toLowerCase().equals("delete")) {
            groupManagementService.setGroupInactive(group);
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.delete.success", request);
            return "redirect:/home";
        } else {
            addMessage(model, MessageType.ERROR, "group.delete.error", request);
            return viewGroupIndex(model, groupId);
        }

    }

    @RequestMapping(value = "unsubscribe")
    public String unsubscribeGroup(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        // todo: check if the user is part of the group
        model.addAttribute("group", group);
        return "group/unsubscribe_confirm";

    }

    @RequestMapping(value = "unsubscribe", method = RequestMethod.POST)
    public String unsubGroup(Model model, @RequestParam("groupId") Long groupId, @RequestParam("confirm_field") String confirmText,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {

        // todo: again, check the user is part of the group and/or do error handling
        Group group = groupManagementService.loadGroup(groupId);
        User user = userManagementService.loadUser(getUserProfile().getId()); // else equals in "is user in group" fails

        if (groupManagementService.isUserInGroup(group, user) && confirmText.toLowerCase().equals("unsubscribe")) {
            groupManagementService.removeGroupMember(group, user);
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.unsubscribe.success", request);
        } else {
            addMessage(redirectAttributes, MessageType.ERROR, "group.unsubscribe.error", request);
        }

        return "redirect:/home";
    }


}
