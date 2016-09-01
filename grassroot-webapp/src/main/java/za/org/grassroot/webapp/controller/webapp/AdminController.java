package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.*;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/10/08.
 * Class for G/R's internal administration functions
 * In time, will probably have multiple controllers in a separate or sub-folder
 * Obviously also need to add security, pronto
 */
@Controller
public class AdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private AdminService adminService;

    // todo : move this to a common interface
    private final static List<String[]> roleDescriptions = Arrays.asList(new String[]{BaseRoles.ROLE_ORDINARY_MEMBER, "Ordinary member"},
            new String[]{BaseRoles.ROLE_COMMITTEE_MEMBER, "Committee member"},
            new String[]{BaseRoles.ROLE_GROUP_ORGANIZER, "Group organizer"});

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/home")
    public String adminIndex(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime week = now.minusWeeks(1L);
        LocalDateTime month = now.minusMonths(1L);

        // todo: put these into maps

        model.addAttribute("countLastWeek", adminService.countUsersCreatedInInterval(week, now));
        model.addAttribute("countLastMonth", adminService.countUsersCreatedInInterval(month, now));
        model.addAttribute("userCount", adminService.countAllUsers());

        model.addAttribute("countUssdLastWeek", adminService.countUsersCreatedAndInitiatedInPeriod(week, now));
        model.addAttribute("countUssdLastMonth", adminService.countUsersCreatedAndInitiatedInPeriod(month, now));
        model.addAttribute("countUssdTotal", adminService.countUsersThatHaveInitiatedSession());

        model.addAttribute("countWebLastWeek", adminService.countUsersCreatedWithWebProfileInPeriod(week, now));
        model.addAttribute("countWebLastMonth", adminService.countUsersCreatedWithWebProfileInPeriod(month, now));
        model.addAttribute("countWebTotal", adminService.countUsersThatHaveWebProfile());


        model.addAttribute("countAndroidLastWeek", adminService.countUsersCreatedWithAndroidProfileInPeriod(week, now));
        model.addAttribute("countAndroidLastMonth", adminService.countUsersCreatedWithAndroidProfileInPeriod(month, now));
        model.addAttribute("countAndroidTotal", adminService.countUsersThatHaveAndroidProfile());

        model.addAttribute("groupsLastWeek", adminService.countGroupsCreatedInInterval(week, now));
        model.addAttribute("groupsLastMonth", adminService.countGroupsCreatedInInterval(month, now));
        model.addAttribute("groupsTotal", adminService.countActiveGroups());

        model.addAttribute("meetingsLastWeek", adminService.countEventsCreatedInInterval(week, now, EventType.MEETING));
        model.addAttribute("meetingsLastMonth", adminService.countEventsCreatedInInterval(month, now, EventType.MEETING));
        model.addAttribute("meetingsTotal", adminService.countAllEvents(EventType.MEETING));

        model.addAttribute("votesLastWeek", adminService.countEventsCreatedInInterval(week, now, EventType.VOTE));
        model.addAttribute("votesLastMonth", adminService.countEventsCreatedInInterval(month, now, EventType.VOTE));
        model.addAttribute("votesTotal", adminService.countAllEvents(EventType.VOTE));

        model.addAttribute("todosLastWeek", adminService.countLogBooksRecordedInInterval(week, now));
        model.addAttribute("todosLastMonth", adminService.countLogBooksRecordedInInterval(month, now));
        model.addAttribute("todosTotal", adminService.countAllLogBooks());

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

        model.addAttribute("totalUserCount", adminService.countAllUsers());
        model.addAttribute("maxUserSessions", adminService.getMaxSessionsInLastMonth());

        Map<Integer, Integer> sessionHistogram = adminService.getSessionHistogram(start, end, 10);
        model.addAttribute("histogramData", sessionHistogram);

        log.info("max user sessions in last month: " + adminService.getMaxSessionsInLastMonth());
        log.info("session histogram : " + sessionHistogram.toString());

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
        List<MaskedUserDTO> foundUsers = adminService.searchByInputNumberOrDisplayName(lookupTerm);

        log.info("Admin site, found this many users with the search term ... " + foundUsers.size());

        if (foundUsers.size() == 0) {
            // say no users found, and ask to search again ... use a redirect, I think
            addMessage(model, MessageType.ERROR, "no.one.found", request);
            pageToDisplay = "admin/users/home";
        } else if (foundUsers.size() == 1) {
            // display just that user, todo: rethink this once refactored all to Uids
            User user = userManagementService.load(foundUsers.get(0).getUid());
            model.addAttribute("user", foundUsers.get(0));
            model.addAttribute("numberGroups", permissionBroker.getActiveGroupDTOs(user, null).size());
            pageToDisplay = "admin/users/view";
        } else {
            // display a list of users
            model.addAttribute("userList", foundUsers);
            pageToDisplay = "admin/users/list";
        }

        return pageToDisplay;
    }

	/*
	RESTRUCTURED METHODS TO HANDLE GROUP ADMIN
	 */

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/groups/search")
    public String searchForGroup() {
        return "admin/groups/search";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/search", method = RequestMethod.POST)
    public String findGroup(Model model, @RequestParam(value = "search_term") String searchTerm) {
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm);
        List<Group> possibleGroups = groupRepository.findByGroupNameContainingIgnoreCase(tsQuery);
        model.addAttribute("possibleGroups", possibleGroups);
        model.addAttribute("roles", roleDescriptions);
        return "admin/groups/search_result";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/view", method = RequestMethod.GET)
    public String adminViewGroup(Model model, @RequestParam String groupUid) {
	    model.addAttribute("group", groupBroker.load(groupUid));
        return "admin/groups/view";
    }

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/deactivate", method = RequestMethod.GET)
	public String deactivateGroup(Model model, @RequestParam String groupUid, HttpServletRequest request) {
		adminService.deactiveGroup(getUserProfile().getUid(), groupUid);
        addMessage(model, MessageType.SUCCESS, "admin.group.deactivated", request);
        return "admin/groups/search";
	}

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/add", method = RequestMethod.POST)
	public String addMemberToGroup(Model model, HttpServletRequest request,
                                   @RequestParam String groupUid,
                                   @RequestParam String displayName,
                                   @RequestParam String phoneNumber,
	                               @RequestParam String roleName) {
        MembershipInfo membershipInfo = new MembershipInfo(phoneNumber, roleName, displayName);
		adminService.addMemberToGroup(getUserProfile().getUid(), groupUid, membershipInfo);
        addMessage(model, MessageType.SUCCESS, "admin.group.added", request);
        return "admin/groups/search";
	}

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/remove", method = RequestMethod.GET)
    public String removeGroupMember(Model model, HttpServletRequest request,
                                    @RequestParam String groupUid,
                                    @RequestParam String phoneNumber) {
        try {
            adminService.removeMemberFromGroup(getUserProfile().getUid(), groupUid, phoneNumber);
            addMessage(model, MessageType.SUCCESS, "admin.group.removed", request);
        } catch (Exception e) {
            addMessage(model, MessageType.ERROR, "admin.group.removed.error", request);
        }
        return "admin/groups/search";
    }

	@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
	@RequestMapping(value = "/admin/groups/role_change", method = RequestMethod.POST)
	public String changeMemberRole(Model model, @RequestParam String groupUid, @RequestParam String userUid,
	                               @RequestParam String roleName, HttpServletRequest request) {
		User userToModify = userManagementService.load(userUid);
		Group group = groupBroker.load(groupUid);

		groupBroker.updateMembershipRole(getUserProfile().getUid(), group.getUid(), userToModify.getUid(), roleName);

		addMessage(model, MessageType.INFO, "admin.done", request);
		return "admin/groups/search";
	}

    /**
     * Methods to create institutional accounts and designate their administrators
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/home")
    public String listAccounts(Model model) {
        model.addAttribute("accounts", new ArrayList<>(accountManagementService.loadAllAccounts()));
        return "admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/view")
    public String adminViewAccount(Model model, @RequestParam("accountId") Long accountId) {
        Account account = accountManagementService.loadAccount(accountId);
        log.info("Viewing account ... " + account.toString());
        model.addAttribute("account", account);
        return "admin/accounts/view";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/create")
    public String createAccountForm(Model model) {
        return "admin/accounts/create";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/accounts/create", method = RequestMethod.POST)
    public String createAccountDo(Model model, @RequestParam("accountName") String accountName, @RequestParam("billingAddress") String billingEmail,
                                  HttpServletRequest request) {

        // todo: can almost certainly do this better with a passed entity, binding & validation etc., but doing minimal for now
        // todo: all the checks & validation, e.g., whether account already exists, etc

        log.info("Okay, we're going to create an account ... with name: " + accountName);
        Account account = accountManagementService.createAccount(accountName);
        account = accountManagementService.setBillingEmail(account, billingEmail);
        model.addAttribute("account", account);
        return "admin/accounts/view";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/designate")
    public String designateUserForm(Model model, @RequestParam(value = "accountId", required = false) Long accountId) {
        // todo: use a selector box if accountId not specified
        model.addAttribute("account", accountManagementService.loadAccount(accountId));
        return "admin/accounts/designate";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "admin/accounts/designate", method = RequestMethod.POST)
    public String designateUserConfirm(Model model, @RequestParam("searchNumber") String searchNumber,
                                       @RequestParam("accountId") Long accountId, HttpServletRequest request) {

        Account account = accountManagementService.loadAccount(accountId);
        model.addAttribute("account", account);

        try {
            User userToDesignate = userManagementService.findByInputNumber(searchNumber); // todo: make this a multi-result search
            model.addAttribute("userToDesignate", userToDesignate);
            return "admin/accounts/designate-confirm";
        } catch (Exception e) {
            addMessage(model, MessageType.ERROR, "admin.find.error", request);
            return "admin/accounts/designate";
        }

    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/accounts/designate_confirmed", method = RequestMethod.POST)
    public String designateUserDo(Model model, @RequestParam String userUid, @RequestParam("accountId") Long accountId) {

        // todo: add error handling, etc

        String roleName = "ROLE_ACCOUNT_ADMIN";
        log.info("Okay, adding the role " + roleName + " to a user ...");

        User userToDesignate = userManagementService.load(userUid);
        Account account = accountManagementService.loadAccount(accountId);

        log.info("Inside designating method, going to add role on this account: " + account.getAccountName() + ", " +
                         "for this user: " + userToDesignate.nameToDisplay());

        accountManagementService.addAdministrator(account, userToDesignate);

        log.info("Added the user to the account and saved it");

        roleManagementService.addStandardRoleToUser("ROLE_ACCOUNT_ADMIN", userToDesignate);

        log.info("Added the role to the user and saved them");

        return listAccounts(model);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/accounts/settings")
    public String viewAccountSettings(Model model, @RequestParam Long accountId) {
        model.addAttribute("account", accountManagementService.loadAccount(accountId));
        return "admin/accounts/settings";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/accounts/settings", method = RequestMethod.POST)
    public String changeAccountSettings(Model model, @ModelAttribute Account account) {
        accountManagementService.adjustSettings(account);
        return adminViewAccount(model, account.getId());
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/designate/group")
    public String designateGroup(Model model) {
        return "admin/group";
    }

}
