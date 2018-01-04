package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.AnalyticalService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberNotPartOfGroupException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.webapp.controller.BaseController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by luke on 2015/10/08.
 * Class for G/R's internal administration functions
 */
@Controller
public class AdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Value("${grassroot.msisdn.length:11}")
    private int phoneNumberLength;

    private static final Random RANDOM = new SecureRandom();

    private final GroupRepository groupRepository;
    private final GroupBroker groupBroker;
    private final PermissionBroker permissionBroker;
    private final PasswordTokenService passwordTokenService;
    private final AdminService adminService;
    private final AnalyticalService analyticalService;

    @Autowired
    public AdminController(GroupRepository groupRepository, GroupBroker groupBroker, PermissionBroker permissionBroker,
                           PasswordTokenService passwordTokenService, AdminService adminService, AnalyticalService analyticalService) {
        this.groupRepository = groupRepository;
        this.groupBroker = groupBroker;
        this.permissionBroker = permissionBroker;
        this.passwordTokenService = passwordTokenService;
        this.adminService = adminService;
        this.analyticalService = analyticalService;
    }

    @PostConstruct
    private void init() {
        // not pretty, but alternatives (both worse) are to hard code length into DTO, or wire it up as a component
        MaskedUserDTO.setPhoneNumberLength(phoneNumberLength);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/home")
    public String adminIndex(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime week = now.minusWeeks(1L);
        LocalDateTime month = now.minusMonths(1L);

        // todo: put these into maps

        model.addAttribute("countLastWeek", analyticalService.countUsersCreatedInInterval(week, now));
        model.addAttribute("countLastMonth", analyticalService.countUsersCreatedInInterval(month, now));
        model.addAttribute("userCount", analyticalService.countAllUsers());

        model.addAttribute("countUssdLastWeek", analyticalService.countUsersCreatedAndInitiatedInPeriod(week, now));
        model.addAttribute("countUssdLastMonth", analyticalService.countUsersCreatedAndInitiatedInPeriod(month, now));
        model.addAttribute("countUssdTotal", analyticalService.countUsersThatHaveInitiatedSession());

        model.addAttribute("countWebLastWeek", analyticalService.countUsersCreatedWithWebProfileInPeriod(week, now));
        model.addAttribute("countWebLastMonth", analyticalService.countUsersCreatedWithWebProfileInPeriod(month, now));
        model.addAttribute("countWebTotal", analyticalService.countUsersThatHaveWebProfile());


        model.addAttribute("countAndroidLastWeek", analyticalService.countUsersCreatedWithAndroidProfileInPeriod(week, now));
        model.addAttribute("countAndroidLastMonth", analyticalService.countUsersCreatedWithAndroidProfileInPeriod(month, now));
        model.addAttribute("countAndroidTotal", analyticalService.countUsersThatHaveAndroidProfile());

        model.addAttribute("groupsLastWeek", analyticalService.countGroupsCreatedInInterval(week, now));
        model.addAttribute("groupsLastMonth", analyticalService.countGroupsCreatedInInterval(month, now));
        model.addAttribute("groupsTotal", analyticalService.countActiveGroups());

        model.addAttribute("meetingsLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.MEETING));
        model.addAttribute("meetingsLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.MEETING));
        model.addAttribute("meetingsTotal", analyticalService.countAllEvents(EventType.MEETING));

        model.addAttribute("votesLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.VOTE));
        model.addAttribute("votesLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.VOTE));
        model.addAttribute("votesTotal", analyticalService.countAllEvents(EventType.VOTE));

        model.addAttribute("todosLastWeek", analyticalService.countTodosRecordedInInterval(week, now));
        model.addAttribute("todosLastMonth", analyticalService.countTodosRecordedInInterval(month, now));
        model.addAttribute("todosTotal", analyticalService.countAllTodos());

        model.addAttribute("safetyLastWeek", analyticalService.countSafetyEventsInInterval(week, now));
        model.addAttribute("safetyLastMonth", analyticalService.countSafetyEventsInInterval(month, now));
        model.addAttribute("safetyTotal", analyticalService.countSafetyEventsInInterval(null, null));

        model.addAttribute("livewireLastWeek", analyticalService.countLiveWireAlertsInInterval(
                week.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("livewireLastMonth", analyticalService.countLiveWireAlertsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("livewireTotal", analyticalService.countLiveWireAlertsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));

        model.addAttribute("notificationsLastWeek", analyticalService.countNotificationsInInterval(
                week.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("notificationsLastMonth", analyticalService.countNotificationsInInterval(
                month.toInstant(ZoneOffset.UTC), Instant.now()));
        model.addAttribute("notificationsTotal", analyticalService.countNotificationsInInterval(
                DateTimeUtil.getEarliestInstant(), Instant.now()));

        return "admin/home";

    }

    /*
    First page is to provide a count of users and allow a search by phone number to modify them
    To do will be to have graphs / counts of users by sign up periods, last active date, etc.
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/users/home")
    public String allUsers(Model model) {

        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);

        model.addAttribute("totalUserCount", analyticalService.countAllUsers());
        model.addAttribute("maxUserSessions", analyticalService.getMaxSessionsInLastMonth());

        Map<Integer, Integer> sessionHistogram = analyticalService.getSessionHistogram(start, end, 10);
        model.addAttribute("histogramData", sessionHistogram);

        log.info("max user sessions in last month: " + analyticalService.getMaxSessionsInLastMonth());

        return "admin/users/home";
    }

    /*
    Page to provide results of a user search, and, if only one found, provide a list of user details with options
    to be able to modify them, as well as to do a password reset
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/users/view")
    public String viewUser(Model model, @RequestParam("lookup_term") String lookupTerm, HttpServletRequest request) {
        String pageToDisplay;

        final String searchTerm = PhoneNumberUtil.testInputNumber(lookupTerm) ? PhoneNumberUtil.convertPhoneNumber(lookupTerm)
                : lookupTerm;
        List<MaskedUserDTO> foundUsers = adminService.searchByInputNumberOrDisplayName(searchTerm);

        log.info("Admin site, found {0} users from the processed search term {1}", foundUsers.size(), searchTerm);

        if (foundUsers.size() == 0) {
            // say no users found, and ask to search again ... use a redirect, I think
            addMessage(model, MessageType.ERROR, "admin.find.error", request);
            pageToDisplay = "redirect:admin/users/home";
        } else if (foundUsers.size() == 1) {
            User user = userManagementService.load(foundUsers.get(0).getUid());
            model.addAttribute("user", foundUsers.get(0));
            model.addAttribute("numberGroups", permissionBroker.getActiveGroupsWithPermission(user, null).size());
            model.addAttribute("adminPhoneNumber", getUserProfile().getPhoneNumber()); // todo : expose a method to just do this (i.e., not sending number back to client)
            pageToDisplay = "admin/users/view";
        } else {
            // display a list of users
            model.addAttribute("userList", foundUsers);
            pageToDisplay = "admin/users/list";
        }

        return pageToDisplay;
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/users/optout", method = RequestMethod.POST)
    public String userOptOut(@RequestParam String userToRemoveUid, @RequestParam String otpEntered,
                             RedirectAttributes attributes, HttpServletRequest request) {

        if (!passwordTokenService.isShortLivedOtpValid(getUserProfile().getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }

        adminService.removeUserFromAllGroups(getUserProfile().getUid(), userToRemoveUid);
        addMessage(attributes, MessageType.SUCCESS, "admin.optout.done", request);
        return "redirect:/admin/users/home";
    }

	/*
	RESTRUCTURED METHODS TO HANDLE GROUP ADMIN
	 */

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/search", method = RequestMethod.GET)
    public String findGroup(Model model, @RequestParam(required = false) String searchTerm) {
        if (StringUtils.isEmpty(searchTerm)) {
            return "admin/groups/search";
        } else {
            String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, false, true);
            log.info("encoded query: {}", tsQuery);
            List<Group> possibleGroups = groupRepository.findByFullTextSearchOnGroupName(tsQuery);
            if (possibleGroups == null || possibleGroups.isEmpty()) {
                // sometimes stop words / lexemes means the query comes back empty, hence use this
                possibleGroups = groupRepository.findByGroupNameContainingIgnoreCase(searchTerm);
            }
            model.addAttribute("possibleGroups", possibleGroups);
            model.addAttribute("roles", BaseRoles.groupRoles);
            return "admin/groups/search_result";
        }
    }

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/deactivate", method = RequestMethod.POST)
	public String deactivateGroup(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String searchTerm, HttpServletRequest request) {
		adminService.updateGroupActive(getUserProfile().getUid(), groupUid, false);
        addMessage(attributes, MessageType.SUCCESS, "admin.group.deactivated", request);
        attributes.addAttribute("searchTerm", searchTerm);
        return "redirect:/admin/groups/search";
	}

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/activate", method = RequestMethod.POST)
    public String activateGroup(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String searchTerm, HttpServletRequest request) {
        adminService.updateGroupActive(getUserProfile().getUid(), groupUid, true);
        addMessage(attributes, MessageType.SUCCESS, "admin.group.activated", request);
        attributes.addAttribute("searchTerm", searchTerm);
        return "redirect:/admin/groups/search";
    }

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/add", method = RequestMethod.POST)
	public String addMemberToGroup(RedirectAttributes attributes, HttpServletRequest request, @RequestParam String searchTerm,
                                   @RequestParam String groupUid, @RequestParam String displayName,
                                   @RequestParam String phoneNumber, @RequestParam String roleName) {
        MembershipInfo membershipInfo = new MembershipInfo(phoneNumber, roleName, displayName);
		adminService.addMemberToGroup(getUserProfile().getUid(), groupUid, membershipInfo);
        addMessage(attributes, MessageType.SUCCESS, "admin.group.added", request);
        attributes.addAttribute("searchTerm", searchTerm);
        return "redirect:/admin/groups/search";
	}

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/remove", method = RequestMethod.POST)
    public String removeGroupMember(RedirectAttributes attributes, HttpServletRequest request,
                                    @RequestParam String groupUid, @RequestParam String phoneNumber, @RequestParam String searchTerm) {
        try {
            adminService.removeMemberFromGroup(getUserProfile().getUid(), groupUid, phoneNumber);
            addMessage(attributes, MessageType.SUCCESS, "admin.group.removed", request);
        } catch (NoSuchUserException e) {
            addMessage(attributes, MessageType.ERROR, "admin.group.removed.nouser", request);
        } catch (MemberNotPartOfGroupException e) {
            addMessage(attributes, MessageType.ERROR, "admin.group.removed.error", request);
        }
        attributes.addAttribute("searchTerm", searchTerm);
        return "redirect:/admin/groups/search";
    }

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/role", method = RequestMethod.POST)
	public String changeMemberRole(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String msisdn,
	                               @RequestParam String roleName, @RequestParam String searchTerm, HttpServletRequest request) {

        final String returnRedir = "redirect:/admin/groups/search";
        attributes.addAttribute("searchTerm", searchTerm);

        log.info("Looking for user with this phone: {}", msisdn);

        if (!userManagementService.userExist(msisdn)) {
            log.info("Could not find user, returning error");
            addMessage(attributes, MessageType.ERROR, "admin.role.change.notfound", new String[] { msisdn }, request);
            return returnRedir;
        }

        User userToModify = userManagementService.findByInputNumber(msisdn);
		Group group = groupBroker.load(groupUid);

		if (!group.getMembers().contains(userToModify)) {
		    log.info("User not in group, returning with error");
		    addMessage(attributes, MessageType.ERROR, "admin.role.change.notmember", new String[] { msisdn }, request);
		    return returnRedir;
        }

		groupBroker.updateMembershipRole(getUserProfile().getUid(), group.getUid(), userToModify.getUid(), roleName);

		addMessage(attributes, MessageType.INFO, "admin.done", request);
		return returnRedir;
	}

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha", method = RequestMethod.GET)
    public String viewAlphaMembers(Model model) {
        model.addAttribute("users", adminService.getUsersWithStdRole(getUserProfile().getUid(),
                BaseRoles.ROLE_ALPHA_TESTER));
        return "admin/users/new_interface_users";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/add", method = RequestMethod.GET)
    public String assignAlphaToUser(RedirectAttributes attributes, HttpServletRequest request,
                                    @RequestParam String phoneNumberOrEmail) {
        try {
            User user = userManagementService.findByNumberOrEmail(phoneNumberOrEmail, phoneNumberOrEmail);
            adminService.addSystemRole(getUserProfile().getUid(), user.getUid(), BaseRoles.ROLE_ALPHA_TESTER);
            addMessage(attributes, MessageType.SUCCESS, "admin.alpha.add.done", request);
        } catch (NoSuchUserException e) {
            log.error("error! can't find that user", e);
            addMessage(attributes, MessageType.ERROR, "admin.alpha.error.nouser", request);
        }
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/create", method = RequestMethod.POST)
    public String addAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone) {
        try {
            log.info("okay, creating a new alpha tester, with name: {}, email: {}, phone: {}",
                    name, email, phone);
            adminService.createUserWithSystemRole(getUserProfile().getUid(), name,
                    phone, email, BaseRoles.ROLE_ALPHA_TESTER);
            addMessage(attributes, MessageType.SUCCESS, "admin.alpha.add.done", request);
        } catch (IllegalArgumentException e) {
            addMessage(attributes, MessageType.ERROR, "admin.alpha.error.fields", request);
        }
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/alpha/user/remove", method = RequestMethod.POST)
    public String removeAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                    @RequestParam String removeUserUid) {
        adminService.removeStdRole(getUserProfile().getUid(), removeUserUid, BaseRoles.ROLE_ALPHA_TESTER);
        addMessage(attributes, MessageType.SUCCESS, "admin.alpha.remove.done", request);
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/user/edit", method = RequestMethod.GET)
    public String editAlphaMember(RedirectAttributes attributes, HttpServletRequest request,
                                  @RequestParam String userUid,
                                  @RequestParam(required = false) String name,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) String phone) {
        if (!StringUtils.isEmpty(name)) {
            userManagementService.updateDisplayName(getUserProfile().getUid(), userUid, name);
        }
        if (!StringUtils.isEmpty(email)) {
            userManagementService.updateEmailAddress(getUserProfile().getUid(), userUid, email);
        }
        if (!StringUtils.isEmpty(phone)) {
            userManagementService.updatePhoneNumber(getUserProfile().getUid(), userUid, phone);
        }
        addMessage(attributes, MessageType.SUCCESS, "admin.alpha.edit.done", request);
        return "redirect:/admin/alpha";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/user/pwd/reset", method = RequestMethod.POST)
    public String updateUserPassword(RedirectAttributes attributes, HttpServletRequest request,
                                     @RequestParam String changeUserUid) {
        String newPwd = generateRandomPwd();
        adminService.updateUserPassword(getUserProfile().getUid(), changeUserUid, newPwd);
        addMessage(attributes, MessageType.SUCCESS, "admin.user.pwd.reset", new String[] { newPwd }, request);
        return "redirect:/admin/alpha"; // or wherever came from, in future
    }

    private String generateRandomPwd() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++){
            int index = (int)(RANDOM.nextDouble()*letters.length());
            password.append(letters.substring(index, index + 1));
        }

        return password.toString();
    }

}
