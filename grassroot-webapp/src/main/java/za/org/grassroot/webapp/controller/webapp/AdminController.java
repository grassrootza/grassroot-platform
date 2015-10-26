package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by luke on 2015/10/08.
 * Class for G/R's internal administration functions
 * In time, will probably have multiple controllers in a separate or sub-folder
 * Obviously also need to add security, pronto
 */
@Controller
public class AdminController extends BaseController {

    private Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @PreAuthorize("hasRole('ADMINISTRATOR')")
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
    @PreAuthorize("hasRole('ADMINISTRATOR')")
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
    @PreAuthorize("hasRole('ADMINISTRATOR')")
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

    /* Method to designate a user as an 'institutional admin', with authority to link groups to an institutional account
    Major todo: access control this, since it opens _a lot_
     */
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @RequestMapping("/admin/users/designate")
    public String designateUser(Model model, @RequestParam("userId") Long userId) {
        
        return "admin/designate";
    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @RequestMapping("/admin/groups")
    public String allGroups(Model model) {

        return "admin/groups";

    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @RequestMapping("/admin/designate/group")
    public String designateGroup(Model model) {


        return "admin/group";

    }

}
