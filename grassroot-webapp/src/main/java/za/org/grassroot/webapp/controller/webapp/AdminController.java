package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.AccountManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    AccountManagementService accountManagementService;

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/home")
    public String adminIndex(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        // todo: additional security checks

        User user = getUserProfile();
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
        List<User> foundUsers;

        switch (lookupField) {
            case "phoneNumber":
                foundUsers = userManagementService.searchByInputNumber(lookupTerm);
                break;
            case "displayName":
                foundUsers = userManagementService.searchByDisplayName(lookupTerm);
                break;
            default:
                foundUsers = userManagementService.searchByInputNumber(lookupTerm);
                break;
        }

        log.info("Admin site, found this many users with the search term ... " + foundUsers.size());

        if (foundUsers.size() == 0) {
            // say no users found, and ask to search again ... use a redirect, I think
            addMessage(model, MessageType.ERROR, "no.one.found", request);
            pageToDisplay = "admin/users/home";
        } else if (foundUsers.size() == 1) {
            // display just that user
            model.addAttribute("user", foundUsers.get(0));
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
            Page<Group> groupList = groupManagementService.getAllGroupsPaginated(page, GROUP_PAGE_SIZE);

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

    /**
     * Methods to create institutional accounts and designate their administrators
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/index")
    public String listAccounts(Model model) {

        model.addAttribute("accounts", new ArrayList<>(accountManagementService.loadAllAccounts()));
        return "admin/accounts/index";
    }


    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/accounts/create")
    public String createAccount(Model model) {

        // todo: additional security checks, given the sensitivity of this

        model.addAttribute("account", new Account());
        return "admin/accounts/create";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/admin/accounts/create", method = RequestMethod.POST)
    public String createAccountDo(Model model, @ModelAttribute("account") Account account,
                                  HttpServletRequest request) {

        // todo: work out best way to do this
        return null;
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/users/designate")
    public String designateUser(Model model, @RequestParam("userId") Long userId) {

        return "admin/designate";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/admin/designate/group")
    public String designateGroup(Model model) {

        return "admin/group";

    }

}
