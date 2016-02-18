package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by luke on 2015/10/08.
 * Class for G/R's internal administration functions
 * In time, will probably have multiple controllers in a separate or sub-folder
 * Obviously also need to add security, pronto
 */
@Controller
public class AdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final Integer GROUP_PAGE_SIZE = 10; // todo: allow user to set

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private AsyncRoleService asyncRoleService;

    @Autowired
    private AnalyticalService analyticalService;

    // todo: move this map somewhere
/*    private static final Map<String, String> groupRoles = ImmutableMap.of(BaseRoles.ROLE_ORDINARY_MEMBER, "Ordinary member",
                                                                          BaseRoles.ROLE_COMMITTEE_MEMBER, "Committee member",
                                                                          BaseRoles.ROLE_GROUP_ORGANIZER, "Group organizer");*/

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/home")
    public String adminIndex(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        User user = getUserProfile();

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

        model.addAttribute("groupsLastWeek", analyticalService.countGroupsCreatedInInterval(week, now));
        model.addAttribute("groupsLastMonth", analyticalService.countGroupsCreatedInInterval(month, now));
        model.addAttribute("groupsTotal", analyticalService.countActiveGroups());

        model.addAttribute("meetingsLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.Meeting));
        model.addAttribute("meetingsLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.Meeting));
        model.addAttribute("meetingsTotal", analyticalService.countAllEvents(EventType.Meeting));

        model.addAttribute("votesLastWeek", analyticalService.countEventsCreatedInInterval(week, now, EventType.Vote));
        model.addAttribute("votesLastMonth", analyticalService.countEventsCreatedInInterval(month, now, EventType.Vote));
        model.addAttribute("votesTotal", analyticalService.countAllEvents(EventType.Vote));

        model.addAttribute("todosLastWeek", analyticalService.countLogBooksRecordedInInterval(week, now));
        model.addAttribute("todosLastMonth", analyticalService.countLogBooksRecordedInInterval(month, now));
        model.addAttribute("todosTotal", analyticalService.countAllLogBooks());

        return "admin/home";

    }

    /*
    First page is to provide a count of users and allow a search by phone number to modify them
    To do will be to have graphs / counts of users by sign up periods, last active date, etc.
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/users/home")
    public String allUsers(Model model) {

        User user = getUserProfile();
        model.addAttribute("totalUserCount", userManagementService.getUserCount());

        return "admin/users/home";
    }

    /*
    Page to provide results of a user search, and, if only one found, provide a list of user details with options
    to be able to modify them, as well as to do a password reset
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/users/view")
    public String viewUser(Model model, @RequestParam("lookup_field") String lookupField,
                           @RequestParam("lookup_term") String lookupTerm, HttpServletRequest request) {

        String pageToDisplay;
        List<MaskedUserDTO> foundUsers = analyticalService.searchByInputNumberOrDisplayName(lookupTerm);

        log.info("Admin site, found this many users with the search term ... " + foundUsers.size());

        if (foundUsers.size() == 0) {
            // say no users found, and ask to search again ... use a redirect, I think
            addMessage(model, MessageType.ERROR, "no.one.found", request);
            pageToDisplay = "admin/users/home";
        } else if (foundUsers.size() == 1) {
            // display just that user
            model.addAttribute("user", foundUsers.get(0));
            model.addAttribute("numberGroups", groupManagementService.getActiveGroupsPartOf(foundUsers.get(0).getId()).size());
            pageToDisplay = "admin/users/view";
        } else {
            // display a list of users
            model.addAttribute("userList", foundUsers);
            pageToDisplay = "admin/users/list";
        }

        return pageToDisplay;
    }

    /*
    Group admin methods
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/groups/home")
    public String allGroups(Model model, @RequestParam(value = "page", required = false) Integer page) {

        // major todo: some way to search through groups
        // major todo: ability to do tree view

        page = (page == null) ? 0 : page;

        if (page == -1) {
            model.addAttribute("paginated", false);
            model.addAttribute("countBase", 0);
            List<Group> groupList = groupManagementService.getAllGroups();
            model.addAttribute("groupList", groupList);
        } else {

            // thymeleaf, as usual, makes the simple (incrementing the page paramater) complex, so have to construct work-around using ints

            model.addAttribute("paginated", true);
            model.addAttribute("countBase", page * GROUP_PAGE_SIZE);
            Page<Group> groupList = groupManagementService.getAllActiveGroupsPaginated(page, GROUP_PAGE_SIZE);

            Integer previousPage = (groupList.hasPrevious()) ? page - 1 : -1;
            Integer nextPage = (groupList.hasNext()) ? page + 1 : -1;

            model.addAttribute("previousPage", previousPage);
            model.addAttribute("nextPage", nextPage);
            model.addAttribute("groupList", groupList.getContent());
        }

        return "admin/groups/home";

    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/groups/filter")
    public String filterGroups(Model model) {
        return "admin/groups/filter";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/groups/list")
    public String listGroups(Model model,@RequestParam(value="createdByUser", required=false) Long createdByUserId,
                             @RequestParam(value="groupMemberSize", required=false) Integer groupSize,
                             @RequestParam(value="createdAfter", required=false) String createdAfter,
                             @RequestParam(value="createdBefore", required=false) String createdBefore) {

        Date createdAfterDate = DateTimeUtil.processDateString(createdAfter);
        Date createdBeforeDate = DateTimeUtil.processDateString(createdBefore);

        log.info("createdAfterDate: " + createdAfterDate);
        log.info("createdBeforeDate: " + createdBeforeDate);

        User createdByUser;
        if (createdByUserId == null || createdByUserId == 0)
            createdByUser = null;
        else
            createdByUser = userManagementService.loadUser(createdByUserId);

        List<Group> groupList = groupManagementService.getGroupsFiltered(createdByUser, groupSize, createdAfterDate, createdBeforeDate);

        model.addAttribute("groupList", groupList);
        return "admin/groups/list";

    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/groups/view")
    public String adminViewGroup(Model model, @RequestParam("groupId") Long groupId) {

        Group group = groupManagementService.loadGroup(groupId);

        List<String[]> groupRoles = Arrays.asList(new String[]{"NULL", "Not set"},
                                                  new String[]{BaseRoles.ROLE_ORDINARY_MEMBER, "Ordinary member"},
                                                  new String[]{BaseRoles.ROLE_COMMITTEE_MEMBER, "Committee member"},
                                                  new String[]{BaseRoles.ROLE_GROUP_ORGANIZER, "Group organizer"});

        List<MemberWrapper> members = new ArrayList<>();
        for (User user : userManagementService.getGroupMembersSortedById(group)) {
            Role roleInGroup = roleManagementService.getUserRoleInGroup(user, group);
            log.info("constructingMemberWrapper ... user's role is ... " + ((roleInGroup != null) ? roleInGroup.describe() : "null"));
            members.add(new MemberWrapper(user, group, roleInGroup));
        }

        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("roles", groupRoles);

        return "admin/groups/view";
    }

    /*
    Group admin methods to adjust roles
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/roles/change")
    public String changeGroupRole(Model model, @RequestParam("groupId") Long groupId, @RequestParam("userId") Long userId,
                                  @RequestParam("roleName") String roleName, HttpServletRequest request) {

        User userToModify = userManagementService.loadUser(userId);
        log.info("Found this user ... " + userToModify);
        Group group = groupManagementService.loadGroup(groupId);

        log.info("Role name retrieved: " + roleName);
        log.info("About to do role assignment etc ... Role: " + roleName + " ... to user ... " + userToModify.nameToDisplay() +
                         " ... to group ... " + group.getGroupName());

        asyncRoleService.addRoleToGroupAndUser(roleName, group, userToModify, getUserProfile());

        addMessage(model, MessageType.INFO, "admin.done", request);
        return adminViewGroup(model, groupId);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/roles/reset")
    public String resetGroupRoles(Model model, @RequestParam Long groupId, HttpServletRequest request) {

        // todo: uh, confirmation screen
        asyncRoleService.resetGroupToDefaultRolesPermissions(groupId);
        addMessage(model, MessageType.INFO, "admin.done", request);
        return allGroups(model, 0);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/roles/reset-select")
    public String resetGroupsSelect(Model model) {
        // todo: add filtering here
        Page<Group> groupsToSelect = groupManagementService.getAllActiveGroupsPaginated(0, 50);
        model.addAttribute("groupList", groupsToSelect);
        return "/admin/groups/reset-select";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/groups/roles/reset-multiple", method = RequestMethod.POST)
    public String resetRolesForMultipleGroups(Model model, @RequestParam("groupId") Long[] groupId) {
        List<Long> groupIds = Arrays.asList(groupId);
        for (Long id : groupIds) {
            log.info("Resetting group ... " + groupManagementService.loadGroup(id).getGroupName());
            asyncRoleService.resetGroupToDefaultRolesPermissions(id);
        }
        return allGroups(model, 0);
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

        // todo: additional security checks, given the sensitivity of this

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
    public String designateUserDo(Model model, @RequestParam("userId") Long userId, @RequestParam("accountId") Long accountId) {

        // todo: add error handling, etc

        String roleName = "ROLE_ACCOUNT_ADMIN";
        log.info("Okay, adding the role " + roleName + " to a user ...");

        User userToDesignate = userManagementService.loadUser(userId);
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
        Account savedAccount = accountManagementService.adjustSettings(account);
        return adminViewAccount(model, account.getId());
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/designate/group")
    public String designateGroup(Model model) {

        return "admin/group";

    }

}
