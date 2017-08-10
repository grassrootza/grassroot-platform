package za.org.grassroot.webapp.controller.webapp.group;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;
import za.org.grassroot.webapp.model.web.MemberWrapper;
import za.org.grassroot.webapp.model.web.MemberWrapperList;
import za.org.grassroot.webapp.util.BulkUserImportUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * @author Lesetse Kimwaga
 */
@Controller
@RequestMapping("/group/")
@SessionAttributes({"groupModifier", "listOfMembers"})
public class GroupController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    @Value("${grassroot.accounts.active:false}")
    private boolean accountsActive;

    private final GroupBroker groupBroker;
    private final TaskBroker taskBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final Validator groupWrapperValidator;

    @Autowired
    public GroupController(GroupBroker groupBroker, TaskBroker taskBroker, GroupQueryBroker groupQueryBroker, AccountGroupBroker accountBroker, @Qualifier("groupWrapperValidator") Validator groupWrapperValidator) {
        this.groupBroker = groupBroker;
        this.taskBroker = taskBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.accountGroupBroker = accountBroker;
        this.groupWrapperValidator = groupWrapperValidator;
    }

    /*
    Binding validators to model attributes. We could just user groupWrapper for both Creator and Modifier, but in the
    future we may need to handle differently, and the redundant code is minimal, so am making two calls
     */
    @InitBinder("groupModifier")
    private void initModifierBinder(WebDataBinder binder) { binder.setValidator(groupWrapperValidator); }

    /*
    Next methods are to view a group, core part of interface
     */

    @RequestMapping("view")
    public String viewGroupIndex(Model model, @RequestParam String groupUid) {

        // note, coming here after group creation throws permission checking errors, so need to reload user from DB
        User user = userManagementService.load(getUserProfile().getUid());
        Group group = groupBroker.load(groupUid);

        if (!group.getMembers().contains(user)) {
            throw new AccessDeniedException("Error! You are not part of this group");
        }

        Set<Permission> userPermissions = group.getMembership(user).getRole().getPermissions();

        model.addAttribute("group", group);

        model.addAttribute("alias", group.getMembership(user).getAlias());
        model.addAttribute("displayName", group.getMembership(user).getDisplayName());

        model.addAttribute("reminderOptions", reminderMinuteOptions(true));
        model.addAttribute("hasParent", (group.getParent() != null));

        List<TaskDTO> tasks = taskBroker.fetchUpcomingIncompleteGroupTasks(user.getUid(), groupUid);
        model.addAttribute("groupTasks", tasks);

        model.addAttribute("subGroups", groupQueryBroker.subGroups(groupUid));
        model.addAttribute("openToken", group.hasValidGroupTokenCode());

        boolean canViewMembers = userPermissions.contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        if (canViewMembers) {
            List<MembershipInfo> members = new ArrayList<>(MembershipInfo.createFromMembers(group.getMemberships()));
            members.sort(Collections.reverseOrder());
            model.addAttribute("groupMembers", members);
        }

        boolean hasUpdatePermission = userPermissions.contains(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // note: we could just pass the set and check in Thymeleaf, but then need to explicitly Permissions... so, rather doing here
        model.addAttribute("canAlter", hasUpdatePermission);
        model.addAttribute("canDeleteGroup", hasUpdatePermission && groupBroker.isDeactivationAvailable(user, group, true));
        model.addAttribute("canMergeWithOthers", hasUpdatePermission); // replace with specific permission later
        model.addAttribute("canAddMembers", userPermissions.contains(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER) &&
                groupBroker.canAddMember(groupUid));
        model.addAttribute("canDeleteMembers", userPermissions.contains(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER));
        model.addAttribute("canChangePermissions", userPermissions.contains(Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE));

        model.addAttribute("canCallMeeting", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
        model.addAttribute("canCallVote", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE));
        model.addAttribute("canRecordAction", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));

        boolean isGroupPaidFor = groupQueryBroker.isGroupPaidFor(groupUid);
        model.addAttribute("isPaidFor", isGroupPaidFor);
        model.addAttribute("canCreateSubGroup", isGroupPaidFor && userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_SUBGROUP));

        boolean userHasAccount = user.getPrimaryAccount() != null;
        if (accountsActive && !isGroupPaidFor && userHasAccount) {
            int groupsLeft = accountGroupBroker.numberGroupsLeft(user.getPrimaryAccount().getUid());
            model.addAttribute("canAddToAccount", groupsLeft > 0);
            model.addAttribute("accountGroupsLeft", groupsLeft);
        } else {
            model.addAttribute("canAddToAccount", false);
        }

        if (accountsActive && canViewMembers && userHasAccount) {
            model.addAttribute("canMoveMembers", true);
            model.addAttribute("groupsForMove", permissionBroker.getActiveGroupsSorted(user, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        }

        model.addAttribute("atGroupSizeLimit", !groupBroker.canAddMember(groupUid));
        model.addAttribute("hasAccount", user.getPrimaryAccount() != null);
        model.addAttribute("canRemoveFromAccount", isGroupPaidFor && user.getPrimaryAccount() != null &&
                user.getPrimaryAccount().equals(accountGroupBroker.findAccountForGroup(groupUid)));

        return "group/view";
    }

    /*
    SECTION: Methods for handling group modification
     */

    @RequestMapping(value = "alias", method = RequestMethod.POST)
    public String setGroupAlias(@RequestParam String groupUid, @RequestParam String alias,
                                RedirectAttributes attributes, HttpServletRequest request) {
        groupBroker.updateMemberAlias(getUserProfile().getUid(), groupUid, alias);
        addMessage(attributes, MessageType.SUCCESS, "group.member.alias.done", new String[] { alias }, request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "remove", method = RequestMethod.POST)
    public String removeMember(Model model, @RequestParam String groupUid, @RequestParam String msisdn, HttpServletRequest request) {
        Set<String> memberToRemove = Sets.newHashSet(userManagementService.findByInputNumber(msisdn).getUid());
        groupBroker.removeMembers(getUserProfile().getUid(), groupUid, memberToRemove);
        addMessage(model, MessageType.INFO, "group.member.remove", request);
        return viewGroupIndex(model, groupUid);
    }

    @RequestMapping(value = "addmember", method = RequestMethod.POST)
    public String addMember(@RequestParam String groupUid, @RequestParam String phoneNumber,
                            @RequestParam String displayName, @RequestParam String roleName,
                            RedirectAttributes attributes, HttpServletRequest request) {
        // note : we validate client side, on length & only chars, but need to check again for slip throughs (e.g., right digits, non-existent network)
        if (PhoneNumberUtil.testInputNumber(phoneNumber)) {
            MembershipInfo newMember = new MembershipInfo(phoneNumber, roleName, displayName);
            try {
                groupBroker.addMembers(getUserProfile().getUid(), groupUid, Sets.newHashSet(newMember), false);
                addMessage(attributes, MessageType.SUCCESS, "group.addmember.success", request);
            } catch (GroupSizeLimitExceededException e) {
                addMessage(attributes, MessageType.ERROR, "group.addmember.limit", request);
            }
        } else {
            addMessage(attributes, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        }

        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "rename_member", method = RequestMethod.POST)
    public String renameMember(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String phoneNumber,
                               @RequestParam String displayName, HttpServletRequest request) {
        try {
            User user = userManagementService.findByInputNumber(phoneNumber);
            userManagementService.setDisplayNameByOther(getUserProfile().getUid(), user.getUid(), displayName);
            addMessage(attributes, MessageType.SUCCESS, "group.member.rename.success", request);
        } catch (AccessDeniedException e) {
            log.info("Error! Description: {}", e.toString());
            addMessage(attributes, MessageType.ERROR, "group.member.rename.error", request);
        }
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:view";
    }

    @PostMapping(value = "rename")
    public String renameGroup(@RequestParam String groupUid, @RequestParam String groupName,
                              RedirectAttributes attributes, HttpServletRequest request) {
        groupBroker.updateName(getUserProfile().getUid(), groupUid, groupName);
        addMessage(attributes, MessageType.SUCCESS, "group.rename.success", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @PostMapping("description")
    public String updateDescription(@RequestParam String groupUid, @RequestParam String groupDescription,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        groupBroker.updateDescription(getUserProfile().getUid(), groupUid, groupDescription);
        addMessage(attributes, MessageType.SUCCESS, "group.description.success", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "token", method = RequestMethod.POST)
    public String manageToken(Model model, @RequestParam String groupUid, HttpServletRequest request) {
        Group group = groupBroker.load(groupUid);
        if (!group.hasValidGroupTokenCode()) {
            groupBroker.openJoinToken(getUserProfile().getUid(), groupUid, null);
            addMessage(model, MessageType.SUCCESS, "group.token.created", request);
        } else {
            groupBroker.closeJoinToken(getUserProfile().getUid(), groupUid);
            addMessage(model, MessageType.SUCCESS, "group.token.closed", request);
        }
        return viewGroupIndex(model, groupUid);
    }

    @RequestMapping(value = "discoverable")
    public String changeDiscoverableConfirmed(Model model, @RequestParam String groupUid,
                                              @RequestParam(value="approverPhoneNumber", required = false) String approverPhoneNumber,
                                              HttpServletRequest request) {

        Group group = groupBroker.load(groupUid);

        if (group.isDiscoverable()) {
            groupBroker.updateDiscoverable(getUserProfile().getUid(), groupUid, false, null);
            addMessage(model, MessageType.SUCCESS, "group.invisible.success", request);
        } else {
            log.info("Switching group to discoverable, with approver msisdn : {}", approverPhoneNumber);
            groupBroker.updateDiscoverable(getUserProfile().getUid(), groupUid, true, approverPhoneNumber);
            addMessage(model, MessageType.SUCCESS, "group.visible.success", request);
        }
        return viewGroupIndex(model, groupUid);
    }

    @RequestMapping(value = "reminder")
    public String changeReminderMinutes(Model model, @RequestParam String groupUid, @RequestParam int reminderMinutes,
                                        HttpServletRequest request) {
        groupBroker.updateGroupDefaultReminderSetting(getUserProfile().getUid(), groupUid, reminderMinutes);
        addMessage(model, MessageType.SUCCESS, "group.reminder.success", request);
        return viewGroupIndex(model, groupUid);
    }

    /*
    Add and remove group from an account
     */

    @RequestMapping(value = "account/add")
    public String addGroupToAccount(@RequestParam String groupUid, @RequestParam(required = false) String accountUid,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        // todo : exception handling etc
        accountGroupBroker.addGroupToAccount(accountUid, groupUid, getUserProfile().getUid());
        addMessage(attributes, MessageType.SUCCESS, "group.account.added", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "account/remove")
    public String removeGroupFromAccount(@RequestParam String groupUid, RedirectAttributes attributes, HttpServletRequest request) {
        Account account = accountGroupBroker.findAccountForGroup(groupUid);
        accountGroupBroker.removeGroupsFromAccount(account.getUid(), Collections.singleton(groupUid), getUserProfile().getUid());
        addMessage(attributes, MessageType.INFO, "group.account.removed", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }


    /*
    Methods and views for adding a few members at a time
     */

    @RequestMapping(value = "change_multiple")
    public String modifyGroup(Model model, @RequestParam String groupUid) {

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        permissionBroker.validateGroupPermission(user, group, null);

        model.addAttribute("listOfMembers", new MemberWrapperList(group, user));

        model.addAttribute("canDeleteMembers", permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER));
        model.addAttribute("canAddMembers", permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        model.addAttribute("canUpdateDetails", permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS));

        model.addAttribute("group", group);
        return "group/change_multiple";
    }

    @RequestMapping(value = "change_multiple", method = RequestMethod.POST)
    public String multipleMemberModify(Model model, @ModelAttribute("listOfMembers") MemberWrapperList memberWrapperList,
                                       HttpServletRequest request, RedirectAttributes attributes) {

        final String groupUid = memberWrapperList.getGroupUid();

        log.info("received {} members, in full : {}", memberWrapperList.getMemberList().size(), memberWrapperList.getMemberList());

        Set<MembershipInfo> nonNullMemberInfo = memberWrapperList.getMemberList().stream()
                .filter(MemberWrapper::isNonNull)
                .map(MemberWrapper::convertToMemberInfo)
                .collect(Collectors.toSet());

        Set<String> deletedMembers = memberWrapperList.getMemberList().stream()
                .filter(MemberWrapper::isDeleted)
                .map(MemberWrapper::getMemberUid)
                .collect(Collectors.toSet());

        Set<MembershipInfo> validNumberMembers = nonNullMemberInfo.stream()
                .filter(m -> BulkUserImportUtil.isNumberValid(m.getPhoneNumber()))
                .collect(Collectors.toSet());

        log.info("Filtered, we have {} non-null members, of which {} are to be deleted, and {} have valid numbers",
                nonNullMemberInfo.size(), deletedMembers.size(), validNumberMembers.size());

        try {
            if (!validNumberMembers.isEmpty()) { // in case the object composition got scrambled, avoid wiping group
                groupBroker.updateMembers(getUserProfile().getUid(), groupUid, validNumberMembers, accountsActive);
            }

            if (!deletedMembers.isEmpty()) {
                groupBroker.removeMembers(getUserProfile().getUid(), groupUid, deletedMembers);
            }

            Group updatedGroup = groupBroker.load(groupUid);
            attributes.addAttribute("groupUid", updatedGroup.getUid());

            if (validNumberMembers.isEmpty() && deletedMembers.isEmpty()) {
              addMessage(model, MessageType.ERROR, "group.update.error", request);
            } else if (validNumberMembers.size() == nonNullMemberInfo.size()) {
                addMessage(attributes, MessageType.SUCCESS, "group.update.success", new Object[]{updatedGroup.getGroupName()}, request);
            } else {
                List<MembershipInfo> invalidMembers = new ArrayList<>(nonNullMemberInfo);
                invalidMembers.removeAll(validNumberMembers);
                if (invalidMembers.size() == 1) {
                    MembershipInfo invalidMember = invalidMembers.get(0);
                    final String msgField = StringUtils.isEmpty(invalidMember.getDisplayName()) ? invalidMember.getPhoneNumberWithoutCCode() :
                            invalidMember.getDisplayName();
                    addMessage(attributes, MessageType.ERROR, "group.update.msisdn.error.one", new String[]{ msgField }, request);
                } else {
                    addMessage(attributes, MessageType.ERROR, "group.update.msisdn.error.multi", new Integer[] { invalidMembers.size() }, request);
                }
            }
            return "redirect:view";
        } catch (IllegalArgumentException e) {
            addMessage(model, MessageType.ERROR, "group.update.error", request);
            return "group/change_multiple";
        }
    }

    @RequestMapping(value = "add_bulk")
    public String addMembersBulk(Model model, @RequestParam String groupUid) {

        Group group = groupBroker.load(groupUid);
        model.addAttribute("group", group);

        Set<Permission> ordinaryPermissions = group.getRole(BaseRoles.ROLE_ORDINARY_MEMBER).getPermissions();

        boolean canCallMeetings = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        boolean canCallVotes = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        boolean canRecordToDo = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        boolean canViewMembers = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        boolean closedGroup = !(canCallMeetings || canCallVotes || canRecordToDo || canViewMembers);

        if (closedGroup) {
            model.addAttribute("closedGroup", true);
        } else {
            model.addAttribute("canCallMeetings", canCallMeetings);
            model.addAttribute("canCallVotes", canCallVotes);
            model.addAttribute("canRecordToDos", canRecordToDo);
            model.addAttribute("canViewMembers", canViewMembers);
        }

        return "group/add_bulk";

    }

    @RequestMapping(value = "add_bulk", method = RequestMethod.POST)
    public String addMembersBulkDo(Model model, @RequestParam String groupUid, @RequestParam String list,
                                   HttpServletRequest request) {

        Group group = groupBroker.load(groupUid);

        Map<String, List<String>> mapOfNumbers = BulkUserImportUtil.splitPhoneNumbers(list);
        List<String> numbersToBeAdded = mapOfNumbers.get("valid");
        boolean sizeExceeded = false;

        if (!numbersToBeAdded.isEmpty()) {
            Long startTime = System.currentTimeMillis();
            Set<MembershipInfo> membershipInfoSet = new HashSet<>();
            for (String number : numbersToBeAdded)
                membershipInfoSet.add(new MembershipInfo(number, BaseRoles.ROLE_ORDINARY_MEMBER, null));
            // todo : intercept before it gets here (i.e., put a count and do a validation)
            try {
                groupBroker.addMembers(getUserProfile().getUid(), groupUid, membershipInfoSet, false);
            } catch (GroupSizeLimitExceededException e) {
                sizeExceeded = true;
            }
            Long duration = System.currentTimeMillis() - startTime;
            log.info("Time taken to add {} numbers: {} msecs", numbersToBeAdded.size(), duration);
        }

        // todo : fix this (error page etc)
        if (mapOfNumbers.get("error").isEmpty()) {
            if (!sizeExceeded) {
                addMessage(model, MessageType.SUCCESS, "group.bulk.success", new Integer[]{numbersToBeAdded.size()}, request);
            } else {
                addMessage(model, MessageType.ERROR, "group.addmember.limit", request);
            }
            return viewGroupIndex(model, groupUid);
        } else {
            model.addAttribute("errors", true);
            model.addAttribute("group", group);
            model.addAttribute("invalid", mapOfNumbers.get("error"));
            model.addAttribute("members_added", numbersToBeAdded.size());
            return "group/add_bulk_error";
        }
    }

    @RequestMapping(value = "copy", method = RequestMethod.GET)
    public String listMembersToMoveOrCopy(Model model, @RequestParam String groupUid) {
        User user = userManagementService.load(getUserProfile().getUid());
        Group group = groupBroker.load(groupUid);
        List<Group> candidates = permissionBroker.getActiveGroupsSorted(user, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        candidates.remove(group);
        MemberPicker memberPicker = MemberPicker.create(group, JpaEntityType.GROUP, false);
        memberPicker.removeMember(getUserProfile().getUid());

        model.addAttribute("group", group);
        model.addAttribute("memberPicker", memberPicker);
        model.addAttribute("targetGroups", candidates);

        return "group/move_copy";
    }

    @RequestMapping(value = "copy", method = RequestMethod.POST)
    public String moveOrCopyMembers(RedirectAttributes attributes, HttpServletRequest request,
                                    @ModelAttribute MemberPicker memberPicker,
                                    @RequestParam String originalGroupUid,
                                    @RequestParam String targetGroupUid,
                                    @RequestParam String moveType,
                                    @RequestParam(required = false) Boolean newGroup,
                                    @RequestParam(required = false) String groupName) {
        boolean removeMembers = "MOVE".equalsIgnoreCase(moveType);
        Set<String> memberUids = memberPicker.getSelectedUids() == null ? new HashSet<>() : memberPicker.getSelectedUids();
        String redirectUid;

        if (newGroup) {
            if (StringUtils.isEmpty(groupName) || memberUids.isEmpty()) {
                removeMembers = false;
                addMessage(attributes, MessageType.ERROR, "group.transfer.new.empty", request);
                redirectUid = originalGroupUid;
            } else {
                MembershipInfo thisMember = new MembershipInfo(getUserProfile().getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER,
                        getUserProfile().getDisplayName());
                Group group = groupBroker.create(getUserProfile().getUid(), groupName, null, Collections.singleton(thisMember),
                        GroupPermissionTemplate.DEFAULT_GROUP, null, null, true);
                groupBroker.copyMembersIntoGroup(getUserProfile().getUid(), group.getUid(), memberUids);
                addMessage(attributes, MessageType.SUCCESS, "group.transfer." + (removeMembers ? "move" : "copy") + ".new", request);
                redirectUid = group.getUid();
            }
        } else {
            groupBroker.copyMembersIntoGroup(getUserProfile().getUid(), targetGroupUid, memberUids);
            addMessage(attributes, MessageType.SUCCESS, "group.transfer." + (removeMembers ? "move" : "copy") + ".existing", request);
            redirectUid = targetGroupUid;
        }

        if (removeMembers) {
            groupBroker.removeMembers(getUserProfile().getUid(), originalGroupUid, memberUids);
        }

        attributes.addAttribute("groupUid", redirectUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "language", method = RequestMethod.POST)
    public String setGroupLanguage(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String locale,
                                   @RequestParam(value = "includeSubGroups", required = false) boolean includeSubGroups,
                                   HttpServletRequest request) {

        log.info("Okay, setting the language to: " + locale);

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        groupBroker.updateGroupDefaultLanguage(getUserProfile().getUid(), group.getUid(), locale, includeSubGroups);

        addMessage(attributes, MessageType.SUCCESS, "group.language.success", request);
        attributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }


    /*
    Methods to handle group deactivation and group unsubscribe
     */

    @RequestMapping(value = "inactive", method = RequestMethod.POST)
    public String deleteGroup(Model model, @RequestParam String groupUid, @RequestParam("confirm_field") String confirmText,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupBroker.load(groupUid);

        if ("delete".equalsIgnoreCase(confirmText)) {
            // service layer will check permissions for this (as well as whether it is within time window (defined in properties)
            groupBroker.deactivate(getUserProfile().getUid(), group.getUid(), true);
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.delete.success", request);
            return "redirect:/home";
        } else {
            addMessage(model, MessageType.ERROR, "group.delete.error", request);
            return viewGroupIndex(model, groupUid);
        }
    }

    @RequestMapping(value = "unsubscribe", method = RequestMethod.POST)
    public String unsubGroup(Model model, @RequestParam String groupUid, HttpServletRequest request,
                             @RequestParam("confirm_field") String confirmText, RedirectAttributes redirectAttributes) {

        // services level will take care of checking that user is in group etc etc

        if (confirmText != null && "unsubscribe".equals(confirmText.toLowerCase().trim())) {
            groupBroker.unsubscribeMember(getUserProfile().getUid(), groupUid);
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.unsubscribe.success", request);
            return "redirect:/home";
        } else {
            addMessage(model, MessageType.ERROR, "group.unsubscribe.error", request);
            return viewGroupIndex(model, groupUid);
        }
    }

    /*
    Methods for handling group linking to a parent (as observing that users often create group first, link later)
     */

    @RequestMapping(value = "parent")
    public String listPossibleParents(Model model, @RequestParam String groupUid,
                                      HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("Looking for possible parents of group with uid: " + groupUid);
        Group groupToMakeChild = groupBroker.load(groupUid);
        Set<Group> possibleParents = groupQueryBroker.possibleParents(getUserProfile().getUid(), groupUid);
        if (!possibleParents.isEmpty()) {
            model.addAttribute("group", groupToMakeChild);
            model.addAttribute("possibleParents", possibleParents);
            return "group/parent";
        } else {
            // add an error message
            log.info("The group does not have possible parents");
            addMessage(redirectAttributes, MessageType.ERROR, "group.parents.none", request);
            redirectAttributes.addAttribute("groupUid", groupUid);
            return "redirect:view";
        }
    }

    @RequestMapping(value = "link", method = RequestMethod.POST)
    public String linkToParent(Model model, @RequestParam String groupUid, @RequestParam String parentUid,
                               RedirectAttributes redirectAttributes, HttpServletRequest request) {
        // call will only succeed if user has requisite permissions (and link is only present on view page if so)
        groupBroker.link(getUserProfile().getUid(), groupUid, parentUid);
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.parent.success", request);
        redirectAttributes.addAttribute("groupUid", groupUid);
        return "redirect:view";
    }

    /*
    Methods to consolidate groups
    */

    @RequestMapping(value = "consolidate/select")
    public String selectConsolidate(Model model, @RequestParam String groupUid,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {

        Group group = groupBroker.load(groupUid);
        Set<Group> candidateGroups = groupQueryBroker.mergeCandidates(getUserProfile().getUid(), groupUid);
        if (candidateGroups == null || candidateGroups.size() == 0) {
            addMessage(redirectAttributes, MessageType.ERROR, "group.merge.no-candidates", request);
            redirectAttributes.addAttribute("groupUid", groupUid);
            return "redirect:view";
        } else {
            model.addAttribute("group1", group);
            model.addAttribute("candidateGroups", candidateGroups);
            return "group/consolidate_select";
        }
    }


    @RequestMapping(value = "consolidate/confirm", method = RequestMethod.POST)
    public String consolidateGroupsConfirm(Model model, @RequestParam String groupUid1, @RequestParam String groupUid2,
                                           @RequestParam String order, @RequestParam(value = "leaveActive", required = false) boolean leaveActive) {

        Group groupInto;
        Group groupFrom;

        switch (order) {
            case "small_to_large":
                Group groupA = groupBroker.load(groupUid1);
                Group groupB = groupBroker.load(groupUid2);
                log.info("ZOG: Going small to large ... size1={}, size2={}", groupA.getMemberships().size(), groupB.getMemberships().size());
                if (groupA.getMemberships().size() >= groupB.getMemberships().size()) {
                    log.info("ZOG: Okay, going from size2 into size1");
                    groupInto = groupA;
                    groupFrom = groupB;
                } else {
                    log.info("ZOG: Okay, going from size1 into size2");
                    groupInto = groupB;
                    groupFrom = groupA;
                }
                break;
            case "2_into_1":
                log.info("ZOG: Moving members from group 2 into group 1");
                groupInto = groupBroker.load(groupUid1);
                groupFrom = groupBroker.load(groupUid2);
                break;
            case "1_into_2":
                log.info("ZOG: Moving members from group 1 into group 2");
                groupInto = groupBroker.load(groupUid2);
                groupFrom = groupBroker.load(groupUid1);
                break;
            default:
                groupInto = groupBroker.load(groupUid1);
                groupFrom = groupBroker.load(groupUid2);
                break;
        }

        model.addAttribute("groupInto", groupInto);
        model.addAttribute("groupFrom", groupFrom);
        model.addAttribute("numberFrom", groupFrom.getMembers().size());
        model.addAttribute("leaveActive", leaveActive);

        return "group/consolidate_confirm";
    }

    @RequestMapping(value = "consolidate/do", method = RequestMethod.POST)
    public String consolidateGroupsDo(Model model, @RequestParam("groupInto") String groupUidInto,
                                      @RequestParam("groupFrom") String groupUidFrom,
                                      @RequestParam(value = "leaveActive", required = false) boolean leaveActive,
                                      @RequestParam(value="confirm_field") String confirmField, RedirectAttributes redirectAttributes, HttpServletRequest request) {

        if (!confirmField.equalsIgnoreCase("merge")) {
            addMessage(redirectAttributes, MessageType.ERROR, "group.merge.error", request);
            return "redirect:/home";
        } else {
            log.info("Merging the groups, leave active set to: {}", leaveActive);
            Group groupFrom = groupBroker.load(groupUidFrom);
            Group consolidatedGroup = groupBroker.merge(getUserProfile().getUid(), groupUidInto, groupUidFrom, leaveActive, true, false, null);
            Integer[] userCounts = new Integer[]{groupFrom.getMembers().size(),
                    consolidatedGroup.getMembers().size()};
            redirectAttributes.addAttribute("groupUid", consolidatedGroup.getUid());
            addMessage(redirectAttributes, MessageType.SUCCESS, "group.merge.success", userCounts, request);
            return "redirect:/group/view";
        }
    }

    /**
     * SECTION: Group history pages
     */

    @RequestMapping(value = "history")
    public String viewGroupHistory(Model model, @RequestParam String groupUid,
                                   @RequestParam(value = "month", required = false) String monthToView) {

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        permissionBroker.validateGroupPermission(user, group, null);

        final LocalDateTime startDateTime;
        final LocalDateTime endDateTime;

        if (StringUtils.isEmpty(monthToView)) {
            LocalDate now = LocalDate.now();
            startDateTime = now.with(firstDayOfMonth()).atStartOfDay();
            endDateTime = now.with(lastDayOfMonth()).atTime(23, 59);
        } else {
            startDateTime = LocalDate.parse(monthToView).atStartOfDay();
            endDateTime = startDateTime.plusMonths(1L);
        }

        List<TaskDTO> tasksInPeriod = taskBroker.fetchGroupTasksInPeriod(user.getUid(), groupUid,
                convertToSystemTime(startDateTime, getSAST()), convertToSystemTime(endDateTime, getSAST()));

        List<GroupLog> groupLogsInPeriod = groupQueryBroker.getLogsForGroup(group, startDateTime, endDateTime);
        List<LocalDate> monthsActive = groupQueryBroker.getMonthsGroupActive(groupUid);

        model.addAttribute("group", group);

        log.info("tasksInPeriod: " + tasksInPeriod);

        model.addAttribute("tasksInPeriod", tasksInPeriod);
        model.addAttribute("groupLogsInPeriod", groupLogsInPeriod);

        model.addAttribute("month", StringUtils.isEmpty(monthToView) ? null : LocalDate.parse(monthToView));
        model.addAttribute("monthsToView", monthsActive);

        return "group/history";
    }

    @RequestMapping(value = "view_event")
    public String redirectToEvent(Model model, @RequestParam String eventUid, @RequestParam EventType eventType,
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {

        String path = (eventType == EventType.MEETING) ? "/meeting/" : "/vote/";
        redirectAttributes.addAttribute("eventUid", eventUid);
        return "redirect:" + path + "view";
    }

    @RequestMapping(value = "temp")
    public String redirectToMessaging() {

        return "group/temp";
    }

}
