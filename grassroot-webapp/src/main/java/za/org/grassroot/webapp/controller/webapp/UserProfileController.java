package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.UserManager;
import za.org.grassroot.webapp.controller.BaseController;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class UserProfileController extends BaseController {

    @Autowired
    private UserManagementService userManagementService;

    @ModelAttribute("currentUserProfile")
    public User getCurrentUser(Authentication authentication) {
        return (authentication == null) ? null :
                userManagementService.fetchUserByUsername(((UserDetails)authentication.getPrincipal()).getUsername());
    }

    @RequestMapping(value = "/user-profile", method = RequestMethod.GET)
    public String index()
    {
        return "user";
    }

}
