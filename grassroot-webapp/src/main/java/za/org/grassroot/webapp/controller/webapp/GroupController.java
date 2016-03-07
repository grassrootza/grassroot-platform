package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;
import za.org.grassroot.webapp.model.web.MemberWrapper;
import za.org.grassroot.webapp.util.BulkUserImportUtil;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */
@Controller


@RequestMapping("/group/")
@SessionAttributes({"groupCreator", "groupModifier"})

public class GroupController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private GroupBroker groupBroker;


    @Autowired
    private AsyncGroupService asyncGroupService;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    private LogBookService logBookService;

    @Autowired
    private GroupLogService groupLogService;

    @Autowired
    private RoleManagementService roleService;

    @Autowired
    private AsyncRoleService asyncRoleService;

    @Autowired
    @Qualifier("groupWrapperValidator")
    private Validator groupWrapperValidator;

    // todo: when cleaning up, figure out how to move these to group wrapper and/or message sources

    List<String[]> permissionTemplates = Arrays.asList(
            new String[]{GroupPermissionTemplate.DEFAULT_GROUP.toString(),
                    "Any member can call a meeting or vote or record a to-do"},
            new String[]{GroupPermissionTemplate.CLOSED_GROUP.toString(),
                    "Only designated members can call a meeting or vote or record a to-do"});

    List<String[]> roleDescriptions = Arrays.asList(new String[]{BaseRoles.ROLE_ORDINARY_MEMBER, "Ordinary member"},
                                                    new String[]{BaseRoles.ROLE_COMMITTEE_MEMBER, "Committee member"},
                                                    new String[]{BaseRoles.ROLE_GROUP_ORGANIZER, "Group organizer"});

    /*
    Binding validators to model attributes. We could just user groupWrapper for both Creator and Modifier, but in the
    future we may need to handle differently, and the redundant code is minimal, so am making two calls
     */
    @InitBinder("groupCreator")
    private void initCreatorBinder(WebDataBinder binder) {
        binder.setValidator(groupWrapperValidator);
    }

    @InitBinder("groupModifier")
    private void initModifierBinder(WebDataBinder binder) {
        binder.setValidator(groupWrapperValidator);
    }

    private boolean isUserPartOfGroup(User sessionUser, Group group) {
        // todo: do this from cache so it's not slow ...
        return groupManagementService.isUserInGroup(group, sessionUser);
    }

    private Group secureLoadGroup(Long id) {
        return loadGroup(id, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
    }

    /*
    First method is for users brand new and without any group membership, and/or later for any user, to find & join group
     */

    // todo: work out how to prevent a computer just cycling through all possible numbers on the token code

    @RequestMapping(value = "search", method = RequestMethod.POST)
    public String searchForGroup(Model model, @RequestParam String searchTerm) {
        Group groupByToken = groupManagementService.findGroupByToken(searchTerm);
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
    public String joinGroup(Model model, @RequestParam String groupUid, HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        // todo: add in group join requests, etc
        MembershipInfo member = new MembershipInfo(getUserProfile().getPhoneNumber(), BaseRoles.ROLE_ORDINARY_MEMBER,
                                                   getUserProfile().getDisplayName());
        groupBroker.addMembers(getUserProfile().getUid(), groupUid, Sets.newHashSet(member));
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.join.success", request);
        return "redirect:/home"; // redirecting to group view is creating issues ... todo: fix those
    }

    /*
    Next methods are to view a group, core part of interface
     */


    // @PreAuthorize("hasPermission(#groupId, ' za.org.grassroot.core.domain.Group', 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS')")
    @RequestMapping("view")
    public String viewGroupIndex(Model model, @RequestParam("groupId") Long groupId) {

        // todo: all sorts of user/group permission checking
        User user = userManagementService.getUserById(getUserProfile().getId()); // todo: remove this once caching etc working
        log.info("Loading group, user has this role ..." + user.getStandardRoles());

        Long startTime = System.currentTimeMillis();
        // Group group = secureLoadGroup(groupId);
        Group group = groupManagementService.loadGroup(groupId);
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Checking group membership took ... %d msec", endTime - startTime));

        startTime = System.currentTimeMillis();
        boolean hasUpdatePermission = (group.getCreatedByUser().equals(user));
        endTime = System.currentTimeMillis();
        log.info(String.format("Checking if update permission took ... %d msec", endTime - startTime));

        model.addAttribute("group", group);
        model.addAttribute("hasParent", (group.getParent() != null));
        model.addAttribute("groupMeetings", eventManagementService.getUpcomingMeetings(group));
        model.addAttribute("groupVotes", eventManagementService.getUpcomingVotes(group));
        model.addAttribute("subGroups", groupManagementService.getSubGroups(group));
        model.addAttribute("openToken", groupManagementService.groupHasValidToken(group));

//         if (groupAccessControlManagementService.hasGroupPermission(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS, group, user)) {
            model.addAttribute("groupMembers", MembershipInfo.createFromMembers(group.getMemberships()));
//         } // removing from master until reset historical groups' roles, else will cause UX issues

        if (hasUpdatePermission) {
            model.addAttribute("canAlter", hasUpdatePermission);
            model.addAttribute("canDeleteGroup", groupBroker.isDeactivationAvailable(user, group));
            model.addAttribute("canMergeWithOthers", hasUpdatePermission); // replace w/ permission later
            model.addAttribute("isDiscoverable", group.isDiscoverable());
            model.addAttribute("roles", roleDescriptions);
        }

        // todo: use Thyemeleaf Java 8 localdatetime library; also, use subgroup method, when that is less of a kludge
        // model.addAttribute("lastActiveTime", Timestamp.valueOf(groupManagementService.getLastTimeGroupActive(group)));

        return "group/view";
    }

    /**
     * SECTION: Methods to handle view for creating a group
     */

    @RequestMapping("create")
    public String startGroupIndex(Model model, @RequestParam(value = "parent", required = false) Long parentId) {

        GroupWrapper groupCreator;

        if (parentId != null) {
            Group parent = groupManagementService.loadGroup(parentId);
            if (parent != null) {
                groupCreator = new GroupWrapper(parent);
            } else {
                groupCreator = new GroupWrapper();
            }
        } else {
            groupCreator = new GroupWrapper();
        }

        MembershipInfo creator = new MembershipInfo(getUserProfile().getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER,
                                                    getUserProfile().getDisplayName());
        groupCreator.addMember(creator); // to remove ambiguity about group creator being part of group

        model.addAttribute("groupCreator", groupCreator);
        model.addAttribute("roles", roleDescriptions);
        model.addAttribute("permissionTemplates", permissionTemplates);

        return "group/create";
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createGroup(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                              @RequestParam("groupTemplate") String templateRaw,
                              BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Long timeStart, timeEnd;
        GroupPermissionTemplate template = GroupPermissionTemplate.fromString(templateRaw); // todo: set in wrapper

        if (bindingResult.hasErrors()) {
            model.addAttribute("groupCreator", groupCreator);
            addMessage(model, MessageType.ERROR, "group.creation.error", request);
            return "group/create";
        }

        timeStart = System.currentTimeMillis();
        User userCreator = getUserProfile();
        String parentUid = (groupCreator.getHasParent()) ? groupCreator.getParent().getUid() : null;
        Group groupCreated = groupBroker.create(userCreator.getUid(), groupCreator.getGroupName(),
                                             parentUid, new HashSet<>(groupCreator.getAddedMembers()), template);
        timeEnd = System.currentTimeMillis();
        log.info(String.format("User load & group creation: %d msecs", timeEnd - timeStart));

        // removing from master branch until more comfortable about interface and UX for this and security

        if (groupCreator.isDiscoverable())
            groupCreated = groupManagementService.setGroupDiscoverable(groupCreated, true, userCreator.getId());

        if (groupCreator.getGenerateToken())
            groupManagementService.generateExpiringGroupToken(groupCreated.getUid(), userCreator.getUid(), groupCreator.getTokenDaysValid());

        addMessage(redirectAttributes, MessageType.SUCCESS, "group.creation.success", new Object[]{groupCreated.getGroupName()}, request);
        redirectAttributes.addAttribute("groupId", groupCreated.getId());
        return "redirect:view";

    }

    @RequestMapping(value = "create", params = {"addMember"})
    public String addMember(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
                            BindingResult bindingResult, HttpServletRequest request) {

        // major todo: shift this to client side
        log.info(String.format("The group wrapper passed back has %d members ...", groupCreator.getAddedMembers().size()));
        if (bindingResult.hasErrors()) {
            log.info("binding_error thrown within binding result");
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        } else {
            groupCreator.getAddedMembers().add(new MembershipInfo("", BaseRoles.ROLE_ORDINARY_MEMBER, ""));
        }
        model.addAttribute("roles", roleDescriptions);
        model.addAttribute("permissionTemplates", permissionTemplates);
        return "group/create";
    }


    /*@RequestMapping(value = "create", params = {"removeMember"})
    public String removeMember(Model model, @ModelAttribute("groupCreator") GroupWrapper groupCreator,
                               @RequestParam("removeMember") Integer memberId) {

        groupCreator.setAddedMembers(removeMember(groupCreator, memberId));
        model.addAttribute("permissionTemplates", permissionTemplates);
        return "group/create";
    }*/

    /*
    SECTION: Methods for handling group modification
    Major todo: permissions, throughout
     */

    @RequestMapping(value = "remove")
    public String removeMember(Model model, @RequestParam String groupUid, @RequestParam String msisdn) {
        log.info(String.format("Alright, removing user with number " + msisdn + "from group with UID " + groupUid));
        Long startTime = System.currentTimeMillis();
        Group group = groupManagementService.loadGroupByUid(groupUid); // todo: remove once passing Uids everywhere
        // being cautious ... in use, if user doesn't have permission, button shouldn't appear on prior page
        // todo: uncomment once roles & permissions are working properly
        /* if (!groupAccessControlManagementService.hasGroupPermission(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
                                                                    group, getUserProfile()))
            throw new AccessDeniedException("You do not have permission to remove this member");*/
        Set<String> memberToRemove = Sets.newHashSet(userManagementService.findByInputNumber(msisdn).getUid());
        groupBroker.removeMembers(getUserProfile().getUid(), groupUid, memberToRemove);
        log.info(String.format("Removing user from group took ... %d msecs", System.currentTimeMillis() - startTime));
        return viewGroupIndex(model, group.getId());
    }

    @RequestMapping(value = "addmember")
    public String addMember(Model model, @RequestParam String groupUid, @RequestParam String phoneNumber,
                            @RequestParam String displayName, @RequestParam String roleName, HttpServletRequest request) {

        Group group = groupManagementService.loadGroupByUid(groupUid);
        /* if (!groupAccessControlManagementService.hasGroupPermission(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                                                                    group, getUserProfile()))
            throw new AccessDeniedException("You do not have permission to add members to this group");*/

        if (PhoneNumberUtil.testInputNumber(phoneNumber)) { //todo: do this client side
            log.info("tested phone number and it is valid ... " + phoneNumber);
            MembershipInfo newMember = new MembershipInfo(phoneNumber, roleName, displayName);
            groupBroker.addMembers(getUserProfile().getUid(), groupUid, Sets.newHashSet(newMember));
            addMessage(model, MessageType.SUCCESS, "group.addmember.success", request);
        } else {
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        }

        return viewGroupIndex(model, group.getId());
    }

    @RequestMapping(value = "rename")
    public String renameGroup(Model model, @RequestParam String groupUid, @RequestParam String groupName,
                              HttpServletRequest request) {
        Group group = groupManagementService.loadGroupByUid(groupUid);
        /* if (!groupAccessControlManagementService.hasGroupPermission(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                                                                    group, getUserProfile()))
            throw new AccessDeniedException(""); */
        // todo: some validation & checking of group name
        groupBroker.updateName(getUserProfile().getUid(), groupUid, groupName);
        addMessage(model, MessageType.SUCCESS, "group.rename.success", request);
        return viewGroupIndex(model, group.getId());
    }

    @RequestMapping(value = "modify", method = RequestMethod.POST, params = {"group_modify"})
    public String modifyGroup(Model model, @RequestParam("groupId") Long groupId) {

        // todo: check permissions
        Group group = groupManagementService.loadGroup(groupId);
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");

        log.info("Okay, modifying this group: " + group.toString());

        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(group);

        log.info("The GroupWrapper now contains: " + groupModifier.getGroup().toString());

        model.addAttribute("groupModifier", groupModifier);
        return "group/modify";

    }

    @RequestMapping(value = "modify", params = {"addMember"})
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

    @RequestMapping(value = "add_members")
    public String addMembersBulk(Model model, @RequestParam("groupId") Long groupId, HttpServletRequest request) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);

        return "group/add_members";
    }

    @RequestMapping(value = "add_members_do", method = RequestMethod.POST)
    public String addMembersBulkDo(Model model, @RequestParam("groupId") Long groupId, @RequestParam(value = "list")
    String list, @RequestParam(value = "closed", required = false) boolean isClosedGroup) {


        log.debug("closedgroup", String.valueOf(isClosedGroup));

        Group group = groupManagementService.loadGroup(groupId);
        Map<String, List<String>> mapOfNumbers = BulkUserImportUtil.splitPhoneNumbers(list);
        List<String> numbersToBeAdded = mapOfNumbers.get("valid");

        if (!numbersToBeAdded.isEmpty()) {
            asyncGroupService.addBulkMembers(groupId, numbersToBeAdded, getUserProfile());
        }

        boolean errors = (mapOfNumbers.get("error").isEmpty()) ? false : true;
        model.addAttribute("errors", errors);
        model.addAttribute("group", group);
        model.addAttribute("invalid", mapOfNumbers.get("error"));
        model.addAttribute("members_added", numbersToBeAdded.size());

        return "group/add_members_do";
    }


    /*@RequestMapping(value = "modify", params = {"removeMember"})
    public String removeMemberModify(Model model, @ModelAttribute("groupModifier") GroupWrapper groupModifier,
                                     @RequestParam("removeMember") Integer memberId) {

        // todo: check permissions (either in validator or in service layer)
        groupModifier.setAddedMembers(removeMember(groupModifier, memberId));
        return "group/modify";
    }*/


/*    @RequestMapping(value = "modify", method = RequestMethod.POST)
    public String modifyGroupDo(Model model, @ModelAttribute("groupModifier") @Validated GroupWrapper groupModifier,
                                BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            log.debug("binding result error ..." + bindingResult.getAllErrors().iterator().next().getCode());
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

        Group savedGroup = groupManagementService.addRemoveGroupMembers(groupToUpdate, updatedUserList, getUserProfile().getId(), true);

        addMessage(redirectAttributes, MessageType.SUCCESS, "group.update.success", new Object[]{savedGroup.getGroupName()}, request);
        redirectAttributes.addAttribute("groupId", savedGroup.getId());
        return "redirect:view";

    }*/

    /*
    Helper methods for handling user addition and updating
     */

    private Set<MembershipInfo> addMember(GroupWrapper groupWrapper) {
        Set<MembershipInfo> groupMembers = groupWrapper.getAddedMembers();
        groupMembers.add(MembershipInfo.makeEmpty());
        return groupMembers;
    }

    private Set<MembershipInfo> removeMember(GroupWrapper groupWrapper, MembershipInfo member) {
        // todo: fully rethink / redo this
        Set<MembershipInfo> groupMembers = groupWrapper.getAddedMembers();
        groupMembers.remove(member);
        return groupMembers;
    }

    /*
    Methods for handling join tokens
     */

    @RequestMapping(value = "modify", method = RequestMethod.POST, params = {"token_create"})
    public String newToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");
        model.addAttribute("group", group);
        return "group/new_token";
    }

    @RequestMapping(value = "modify", method = RequestMethod.POST, params = {"token_extend"})
    public String extendToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/extend_token";
    }

    @RequestMapping(value = "modify", method = RequestMethod.POST, params = {"token_cancel"})
    public String cancelToken(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);
        model.addAttribute("group", group);
        return "group/close_token";
    }

    @RequestMapping(value = "token", method = RequestMethod.POST)
    public String createGroupToken(Model model, @RequestParam("groupId") Long groupId, @RequestParam("action") String action,
                                   @RequestParam(value = "days", required = false) Integer days,
                                   HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId); // todo: just leave it out once refactored to Uid

        switch (action) {
            case "create":
                if (days == 0)
                    group = groupManagementService.generateGroupToken(group.getUid(), getUserProfile().getUid());
                else
                    group = groupManagementService.generateExpiringGroupToken(group.getUid(), getUserProfile().getUid(), days);
                log.info("New token created with value: " + group.getGroupTokenCode());
                addMessage(redirectAttributes, MessageType.SUCCESS, "group.token.creation.success",
                        new Object[]{group.getGroupTokenCode()}, request);
                break;
            case "extend":
                group = groupManagementService.extendGroupToken(group, days, getUserProfile());
                log.info("Token extended until: " + group.getTokenExpiryDateTime().toString());
                break;
            case "close":
                group = groupManagementService.closeGroupToken(group.getUid(), getUserProfile().getUid());
                log.info("Token closed!");
                break;
        }

        redirectAttributes.addAttribute("groupId", group.getId());
        return "redirect:/group/view";

    }

    @RequestMapping(value = "modify", params = {"group_language"})
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
        groupManagementService.setGroupDefaultLanguage(group, locale, includeSubGroups);

        // todo: there is probably a more efficient way to do this than the redirect
        redirectAttributes.addAttribute("groupId", group.getId());
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.language.success", request);
        return "redirect:/group/view";
    }

    /**
     * Methods to handle changing group discoverability
     */
    @RequestMapping(value = "modify", params = {"discoverable"})
    public String alterGroupDiscoverability(Model model, @RequestParam("groupId") Long groupId) {
        Group group = loadGroup(groupId, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        model.addAttribute("group", group);
        return "group/discoverable";
    }

    @RequestMapping(value = "discoverable", method = RequestMethod.POST)
    public String changeDiscoverableConfirmed(Model model, @RequestParam Long groupId,
                                              @RequestParam(value = "confirm_field", required = false) String confirmField,
                                              RedirectAttributes redirectAttributes, HttpServletRequest request) {

        Group group = loadGroup(groupId, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        if (group.isDiscoverable()) {
            groupManagementService.setGroupDiscoverable(group, false, getUserProfile().getId());
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.invisible.success", request);
        } else {
            if (confirmField.equalsIgnoreCase("visible")) {
                groupManagementService.setGroupDiscoverable(group, true, getUserProfile().getId());
                addMessage(redirectAttributes, MessageType.SUCCESS, "group.visible.success", request);
            } else {
                addMessage(redirectAttributes, MessageType.ERROR, "group.visible.error", request);
            }
        }

        redirectAttributes.addAttribute("groupId", groupId);
        return "redirect:/group/view";
    }

    /*
    Methods for handling group linking to a parent (as observing that users often create group first, link later)
     */

    @RequestMapping(value = "parent")
    public String listPossibleParents(Model model, @RequestParam("groupId") Long groupId,
                                      HttpServletRequest request, RedirectAttributes redirectAttributes) {
        // todo: check permissions, handle exceptions (in fact, on view group page), etc.
        log.info("Looking for possible parents of group with ID: " + groupId);

        Group groupToMakeChild = groupManagementService.loadGroup(groupId);

        List<Group> userGroups = groupManagementService.getActiveGroupsPartOf(getUserProfile());
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

    @RequestMapping(value = "link", method = RequestMethod.POST)
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
                                           @RequestParam("order") String order, @RequestParam(value = "leaveActive", required = false) boolean leaveActive,
                                           HttpServletRequest request) {

        Group groupInto;
        Group groupFrom;

        switch (order) {
            case "small_to_large":
                Group groupA = groupManagementService.loadGroup(groupId1);
                Group groupB = groupManagementService.loadGroup(groupId2);
                if (groupA.getMemberships().size() >= groupB.getMemberships().size()) {
                    groupInto = groupA;
                    groupFrom = groupB;
                } else {
                    groupInto = groupB;
                    groupFrom = groupA;
                }
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
                groupInto = groupManagementService.loadGroup(groupId1);
                groupFrom = groupManagementService.loadGroup(groupId2);
                break;
        }

        model.addAttribute("groupInto", groupInto);
        model.addAttribute("groupFrom", groupFrom);
        model.addAttribute("numberFrom", groupManagementService.getGroupSize(groupFrom.getId(), false));
        model.addAttribute("leaveActive", leaveActive);

        return "group/consolidate_confirm";
    }

    @RequestMapping(value = "consolidate/do", method = RequestMethod.POST)
    public String consolidateGroupsDo(Model model, @RequestParam("groupInto") Long groupIdInto, @RequestParam("groupFrom") Long groupIdFrom,
                                      @RequestParam(value = "leaveActive", required = false) boolean leaveActive, RedirectAttributes redirectAttributes, HttpServletRequest request) {

        // todo: add error handling
        Group groupInto = groupManagementService.loadGroup(groupIdInto);
        Group groupFrom = groupManagementService.loadGroup(groupIdFrom);
        Group consolidatedGroup = groupManagementService.mergeGroups(groupIdInto, groupIdFrom, getUserProfile().getId(), leaveActive, true, false);
        Integer[] userCounts = new Integer[]{groupFrom.getMembers().size(),
                groupManagementService.getGroupSize(consolidatedGroup.getId(), false)};
        redirectAttributes.addAttribute("groupId", consolidatedGroup.getId());
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.merge.success", userCounts, request);
        return "redirect:/group/view";
    }

    /*
    Methods to handle group deactivation and group unsubscribe
    Simple method to delete a group, if it was recently created and this is the creating user, after a confirmation screen
    todo: add Spring Security annotations to stop unauthorized users from even accessing the URLs
     */
    @RequestMapping(value = "modify", params = {"group_delete"})
    public String confirmDelete(Model model, @RequestParam("groupId") Long groupId,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {

        User user = getUserProfile();
        Group group = groupManagementService.loadGroup(groupId);

        if (groupBroker.isDeactivationAvailable(user, group)) {
            model.addAttribute("group", group);
            return "group/delete_confirm";
        } else {
            log.info("Nope, can't make the group inactive ...");
            addMessage(redirectAttributes, MessageType.ERROR, "group.delete.error", request);
            redirectAttributes.addAttribute("groupId", groupId);
            return "group/view";
        }

    }

    @RequestMapping(value = "delete")
    public String deleteGroup(Model model, @RequestParam("groupId") Long groupId, @RequestParam("confirm_field") String confirmText,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupManagementService.loadGroup(groupId);

        if (groupBroker.isDeactivationAvailable(getUserProfile(), group) &&
                confirmText.toLowerCase().equals("delete")) {
            groupBroker.deactivate(getUserProfile().getUid(), group.getUid());
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
            groupBroker.removeMembers(user.getUid(), group.getUid(), Sets.newHashSet(user.getUid()));
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.unsubscribe.success", request);
        } else {
            addMessage(redirectAttributes, MessageType.ERROR, "group.unsubscribe.error", request);
        }

        return "redirect:/home";
    }

    /**
     * SECTION: Group history pages
     * todo: maybe separate this off into its own controller if it starts to become overly complex
     */

    @RequestMapping(value = "history")
    public String viewGroupHistory(Model model, @RequestParam Long groupId,
                                   @RequestParam(value = "monthToView", required = false) String monthToView) {

        Group group = groupManagementService.loadGroup(groupId); // todo: use permissions
        if (!isUserPartOfGroup(getUserProfile(), group)) throw new AccessDeniedException("");

        final LocalDateTime startDateTime;
        final LocalDateTime endDateTime;

        if (monthToView == null) {
            startDateTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            endDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS); // leaving miliseconds on causes spurious test failures
        } else {
            startDateTime = LocalDate.parse("01-" + monthToView, DateTimeFormatter.ofPattern("dd-M-yyyy")).atStartOfDay();
            endDateTime = startDateTime.plusMonths(1L);
        }

        Long startTime = System.currentTimeMillis();
        List<Event> eventsInPeriod = eventManagementService.getGroupEventsInPeriod(group, startDateTime, endDateTime);
        List<LogBook> logBooksInPeriod = logBookService.getLogBookEntriesInPeriod(group.getId(), startDateTime, endDateTime);
        List<GroupLog> groupLogsInPeriod = groupLogService.getLogsForGroup(group, startDateTime, endDateTime);
        List<LocalDate> monthsActive = groupManagementService.getMonthsGroupActive(group);
        Long endTime = System.currentTimeMillis();

        log.info(String.format("Retrieved the events and group log ... time taken: %d msecs", endTime - startTime));

        model.addAttribute("group", group);
        model.addAttribute("eventsInPeriod", eventsInPeriod);
        model.addAttribute("logBooksInPeriod", logBooksInPeriod);
        model.addAttribute("groupLogsInPeriod", groupLogsInPeriod);
        model.addAttribute("monthsToView", monthsActive);

        return "group/history";
    }

    @RequestMapping(value = "view_event")
    public String redirectToEvent(Model model, @RequestParam Long eventId, @RequestParam EventType eventType,
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {

        String path = (eventType == EventType.Meeting) ? "/meeting/" : "/vote/";
        redirectAttributes.addAttribute("eventId", eventId);
        return "redirect:" + path + "view";

    }

    /**
     * Group role view pages
     */

    @RequestMapping(value = "roles/view")
    public String viewGroupRoles(Model model, @RequestParam Long groupId) {

        Group group = loadGroup(groupId, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        Set<Role> roles = group.getGroupRoles();

        // todo: replace this with Membership entity once built ... very badly done kludge for present

        List<MemberWrapper> members = new ArrayList<>();
        for (User user : userManagementService.getGroupMembersSortedById(group))
            members.add(new MemberWrapper(user, group, roleService.getUserRoleInGroup(user, group)));

        List<String[]> roleDescriptionsWithNull = new ArrayList<>(roleDescriptions);
        roleDescriptionsWithNull.add(0, new String[] { "NULL", "Not set"});

        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("roles", roleDescriptions);

        Role organizer = group.getRole(BaseRoles.ROLE_GROUP_ORGANIZER);
        model.addAttribute("organizerPerms", organizer.getPermissions());

        Role committee = group.getRole(BaseRoles.ROLE_COMMITTEE_MEMBER);
        model.addAttribute("committeePerms", committee.getPermissions());

        Role ordinary = group.getRole(BaseRoles.ROLE_ORDINARY_MEMBER);
        model.addAttribute("ordinaryPerms", ordinary.getPermissions());

        return "group/roles";
    }

    @RequestMapping(value = "roles/change", method = RequestMethod.POST)
    public String changeGroupRole(Model model, @RequestParam Long groupId, @RequestParam Long userId,
                                  @RequestParam("roleName") String roleName, HttpServletRequest request) {

        User userToModify = userManagementService.loadUser(userId);
        Group group = groupManagementService.loadGroup(groupId);
        asyncRoleService.addRoleToGroupAndUser(roleName, group, userToModify, getUserProfile());

        addMessage(model, MessageType.INFO, "group.role.done", request);
        return viewGroupRoles(model, groupId);
    }

}
