package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.Sets;
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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;
import za.org.grassroot.webapp.util.BulkUserImportUtil;

import javax.servlet.http.HttpServletRequest;
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
    private GroupBroker groupBroker;

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    @Qualifier("groupWrapperValidator")
    private Validator groupWrapperValidator;

    /*
    Binding validators to model attributes. We could just user groupWrapper for both Creator and Modifier, but in the
    future we may need to handle differently, and the redundant code is minimal, so am making two calls
     */
    @InitBinder("groupCreator")
    private void initCreatorBinder(WebDataBinder binder) {
        binder.setValidator(groupWrapperValidator);
    }

    @InitBinder("groupModifier")
    private void initModifierBinder(WebDataBinder binder) { binder.setValidator(groupWrapperValidator); }

    /*
    Next methods are to view a group, core part of interface
     */

    @RequestMapping("view")
    public String viewGroupIndex(Model model, @RequestParam String groupUid) {

        // note, coming here after group creation throws permission checking errors, so need to reload user from DB
        Long startTime = System.currentTimeMillis();
        User user = userManagementService.load(getUserProfile().getUid());
        Group group = groupBroker.load(groupUid);
        Set<Permission> userPermissions = permissionBroker.getPermissions(user, group); // throws exception if not in group
        Long endTime = System.currentTimeMillis();
        log.info("Checking group membership & loading permissions took {} msec, for group {}", endTime - startTime, group);

        model.addAttribute("group", group);
        model.addAttribute("reminderOptions", reminderMinuteOptions(true));
        model.addAttribute("languages", userManagementService.getImplementedLanguages().entrySet());
        model.addAttribute("hasParent", (group.getParent() != null));

        List<TaskDTO> tasks = taskBroker.fetchUpcomingIncompleteGroupTasks(user.getUid(), groupUid);
        model.addAttribute("groupTasks", tasks);

        model.addAttribute("subGroups", groupBroker.subGroups(groupUid));
        model.addAttribute("openToken", group.hasValidGroupTokenCode());

        if (userPermissions.contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            List<MembershipInfo> members = new ArrayList<>(MembershipInfo.createFromMembers(group.getMemberships()));
            Collections.sort(members, Collections.reverseOrder());
            model.addAttribute("groupMembers", members);
        }

        boolean hasUpdatePermission = userPermissions.contains(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // note: we could just pass the set and check in Thymeleaf, but then need to explicitly Permissions... so, rather doing here
        model.addAttribute("canAlter", hasUpdatePermission);
        model.addAttribute("canDeleteGroup", hasUpdatePermission && groupBroker.isDeactivationAvailable(user, group, true));
        model.addAttribute("canMergeWithOthers", hasUpdatePermission); // replace with specific permission later
        model.addAttribute("canAddMembers", userPermissions.contains(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        model.addAttribute("canDeleteMembers", userPermissions.contains(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER));
        model.addAttribute("canChangePermissions", userPermissions.contains(Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE));

        model.addAttribute("canCallMeeting", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
        model.addAttribute("canCallVote", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE));
        model.addAttribute("canRecordAction", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));

        model.addAttribute("canCreateSubGroup", userPermissions.contains(Permission.GROUP_PERMISSION_CREATE_SUBGROUP));

        return "group/view";
    }

    /*
    SECTION: Methods for handling group modification
     */

    @RequestMapping(value = "remove", method = RequestMethod.POST)
    public String removeMember(Model model, @RequestParam String groupUid, @RequestParam String msisdn, HttpServletRequest request) {
        Set<String> memberToRemove = Sets.newHashSet(userManagementService.findByInputNumber(msisdn).getUid());
        groupBroker.removeMembers(getUserProfile().getUid(), groupUid, memberToRemove);
        addMessage(model, MessageType.INFO, "group.member.remove", request);
        return viewGroupIndex(model, groupUid);
    }

    @RequestMapping(value = "addmember", method = RequestMethod.POST)
    public String addMember(Model model, @RequestParam String groupUid, @RequestParam String phoneNumber,
                            @RequestParam String displayName, @RequestParam String roleName, HttpServletRequest request) {

        if (PhoneNumberUtil.testInputNumber(phoneNumber)) { //todo: do this client side
            MembershipInfo newMember = new MembershipInfo(phoneNumber, roleName, displayName);
            groupBroker.addMembers(getUserProfile().getUid(), groupUid, Sets.newHashSet(newMember), false);
            addMessage(model, MessageType.SUCCESS, "group.addmember.success", request);
        } else {
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        }

        return viewGroupIndex(model, groupUid);
    }

    @RequestMapping(value = "rename")
    public String renameGroup(Model model, @RequestParam String groupUid, @RequestParam String groupName,
                              HttpServletRequest request) {
        // todo: some validation & checking of group name
        groupBroker.updateName(getUserProfile().getUid(), groupUid, groupName);
        addMessage(model, MessageType.SUCCESS, "group.rename.success", request);
        return viewGroupIndex(model, groupUid);
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
    Methods and views for adding a few members at a time
     */

    @RequestMapping(value = "change_multiple")
    public String modifyGroup(Model model, @RequestParam String groupUid) {

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        // todo : change all in this controller to this pattern
        permissionBroker.validateGroupPermission(user, group, null);

        log.info("Okay, modifying this group: " + group.toString());

        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(group);

        groupModifier.setCanRemoveMembers(permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER));
        groupModifier.setCanAddMembers(permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        groupModifier.setCanUpdateDetails(permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS));

        log.info("The GroupWrapper now contains: " + groupModifier.getGroup().toString());

        model.addAttribute("groupModifier", groupModifier);
        return "group/change_multiple";
    }

    @RequestMapping(value = "change_multiple", params = {"addMember"})
    public String addMemberModify(Model model, @ModelAttribute("groupModifier") @Validated GroupWrapper groupModifier,
                                  BindingResult bindingResult, HttpServletRequest request) {
        // todo: check permissions
        log.info("Inside changeMultiple methods routine ... adding a member to list");
        if (bindingResult.hasErrors()) {
            addMessage(model, MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
        } else {
            groupModifier.getListOfMembers().add(new MembershipInfo("", BaseRoles.ROLE_ORDINARY_MEMBER, ""));
        }

        return "group/change_multiple";
    }

    @RequestMapping(value = "change_multiple", params = {"removeMember"})
    public String addMemberMmodify(Model model, @ModelAttribute("groupModifier") GroupWrapper groupModifier,
                                   @RequestParam("removeMember") int memberIndex) {
        groupModifier.getListOfMembers().remove(memberIndex);
        return "group/change_multiple";
    }

    @RequestMapping(value = "change_multiple", method = RequestMethod.POST)
    public String multipleMemberModify(Model model, @ModelAttribute("groupModifier") GroupWrapper groupModifier,
                                       HttpServletRequest request, RedirectAttributes attributes) {

        // todo: probably needs a confirmation screen

        log.info("multipleMemberModify ... got these members back : " + groupModifier.getListOfMembers());
        String groupUid = groupModifier.getGroup().getUid();
        groupBroker.updateMembers(getUserProfile().getUid(), groupUid, groupModifier.getAddedMembers());

        Group updatedGroup = groupBroker.load(groupUid);
        addMessage(attributes, MessageType.SUCCESS, "group.update.success", new Object[]{updatedGroup.getGroupName()}, request);
        attributes.addAttribute("groupUid", updatedGroup.getUid());
        return "redirect:view";
    }

    @RequestMapping(value = "add_bulk")
    public String addMembersBulk(Model model, @RequestParam String groupUid, HttpServletRequest request) {

        Group group = groupBroker.load(groupUid);
        model.addAttribute("group", group);

        Set<Permission> ordinaryPermissions = permissionBroker.getPermissions(group, BaseRoles.ROLE_ORDINARY_MEMBER);

        boolean canCallMeetings = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        boolean canCallVotes = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        boolean canRecordToDo = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        boolean canViewMembers = ordinaryPermissions.contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        boolean closedGroup = !(canCallMeetings || canCallVotes || canRecordToDo || canViewMembers);

        if (closedGroup) {
            model.addAttribute("closedGroup", closedGroup);
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

        if (!numbersToBeAdded.isEmpty()) {
            Long startTime = System.currentTimeMillis();
            Set<MembershipInfo> membershipInfoSet = new HashSet<>();
            for (String number : numbersToBeAdded)
                membershipInfoSet.add(new MembershipInfo(number, BaseRoles.ROLE_ORDINARY_MEMBER, null));
            groupBroker.addMembers(getUserProfile().getUid(), groupUid, membershipInfoSet, false);
            Long duration = System.currentTimeMillis() - startTime;
            log.info("Time taken to add {} numbers: {} msecs", numbersToBeAdded.size(), duration);
        }

        if (mapOfNumbers.get("error").isEmpty()) {
            addMessage(model, MessageType.SUCCESS, "group.bulk.success", new Integer[] { numbersToBeAdded.size() }, request);
            return viewGroupIndex(model, groupUid);
        } else {
            model.addAttribute("errors", true);
            model.addAttribute("group", group);
            model.addAttribute("invalid", mapOfNumbers.get("error"));
            model.addAttribute("members_added", numbersToBeAdded.size());
            return "group/add_bulk_error";
        }
    }

    @RequestMapping(value = "language")
    public String setGroupLanguage(Model model, @RequestParam String groupUid, @RequestParam String locale,
                                   @RequestParam(value = "includeSubGroups", required = false) boolean includeSubGroups,
                                   HttpServletRequest request) {

        // todo: add permissions checking, exception handling, etc.

        log.info("Okay, setting the language to: " + locale);

        Group group = groupBroker.load(groupUid);
        groupBroker.updateGroupDefaultLanguage(getUserProfile().getUid(), group.getUid(), locale, includeSubGroups);

        // todo: there is probably a more efficient way to do this than the redirect
        addMessage(model, MessageType.SUCCESS, "group.language.success", request);
        return viewGroupIndex(model, groupUid);
    }


    /*
    Methods to handle group deactivation and group unsubscribe
     */

    @RequestMapping(value = "inactive", method = RequestMethod.POST)
    public String deleteGroup(Model model, @RequestParam String groupUid, @RequestParam("confirm_field") String confirmText,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Group group = groupBroker.load(groupUid);

        if ("delete".equalsIgnoreCase(confirmText)) {
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
        // todo: check permissions, handle exceptions (in fact, on view group page), etc.
        log.info("Looking for possible parents of group with ID: " + groupUid);

        Group groupToMakeChild = groupBroker.load(groupUid);
        Set<Group> possibleParents = groupBroker.possibleParents(getUserProfile().getUid(), groupUid);

        if (!possibleParents.isEmpty()) {
            log.info("The group (with ID " + groupUid + ") has some possible parents, in fact this many: " + possibleParents.size());
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

        // todo: permissions, exceptions, etc.
        // todo: check if need to send a request to parent for permission to bind

        Group group = groupBroker.load(groupUid);
        Group parent = groupBroker.load(parentUid);

        groupBroker.link(getUserProfile().getUid(), group.getUid(), parent.getUid());

        // addMessage(model, MessageType.SUCCESS, "group.parent.success", request);
        addMessage(redirectAttributes, MessageType.SUCCESS, "group.parent.success", request);
        redirectAttributes.addAttribute("groupUid", group.getUid());
        return "redirect:view";

    }

    /*
    Methods to consolidate groups
    todo: add role authorizations, etc
     */

    @RequestMapping(value = "consolidate/select")
    public String selectConsolidate(Model model, @RequestParam String groupUid,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {

        User user = getUserProfile();

        Group group = groupBroker.load(groupUid);
        Set<Group> candidateGroups = groupBroker.mergeCandidates(user.getUid(), groupUid);
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

        // todo: add error handling
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
                                   @RequestParam(value = "monthToView", required = false) String monthToView) {

        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(getUserProfile().getUid());

        permissionBroker.validateGroupPermission(user, group, null);

        final LocalDateTime startDateTime;
        final LocalDateTime endDateTime;

        if (monthToView == null) {
            startDateTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            endDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); // leaving seconds out on causes spurious test failures
        } else {
            startDateTime = LocalDate.parse("01-" + monthToView, DateTimeFormatter.ofPattern("dd-M-yyyy")).atStartOfDay();
            endDateTime = startDateTime.plusMonths(1L);
        }

        Long startTime = System.currentTimeMillis();
        List<Event> eventsInPeriod = eventManagementService.getGroupEventsInPeriod(group, startDateTime, endDateTime);
        List<Todo> logBooksInPeriod = todoBroker.getTodosInPeriod(group, startDateTime, endDateTime);
        List<GroupLog> groupLogsInPeriod = groupBroker.getLogsForGroup(group, startDateTime, endDateTime);
        List<LocalDate> monthsActive = groupBroker.getMonthsGroupActive(groupUid);
        Long endTime = System.currentTimeMillis();

        log.info("Retrieved the events and group log ... time taken: {} msecs", endTime - startTime);

        model.addAttribute("group", group);
        model.addAttribute("eventsInPeriod", eventsInPeriod);
        model.addAttribute("logBooksInPeriod", logBooksInPeriod);
        model.addAttribute("groupLogsInPeriod", groupLogsInPeriod);
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

}
