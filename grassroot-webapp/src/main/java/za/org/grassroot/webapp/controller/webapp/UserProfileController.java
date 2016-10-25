package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.exception.InvalidTokenException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
@Controller
@RequestMapping(value = "/user/")
public class UserProfileController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    @Autowired
    private UserManagementService userManagementService;

    @ModelAttribute("sessionUser")
    public User getCurrentUser(Authentication authentication) {
        return (authentication == null) ? null :
                userManagementService.fetchUserByUsername(((UserDetails) authentication.getPrincipal()).getUsername());
    }

    @RequestMapping(value = "settings", method = RequestMethod.GET)
    public String index(Model model) {
        return "user/settings";
    }

    @RequestMapping(value = "settings", method = RequestMethod.POST)
    public String post(@ModelAttribute User sessionUser, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.info("retrieved this user, displayName={}, alertPref={}, language={}", sessionUser.getDisplayName(),
                sessionUser.getAlertPreference(), sessionUser.getLanguageCode());
        try {
            userManagementService.updateUser(getUserProfile().getUid(), sessionUser.getDisplayName(), sessionUser.getAlertPreference(),
                    new Locale(sessionUser.getLanguageCode()));
            addMessage(redirectAttributes, MessageType.SUCCESS, "user.profile.change.success", request);
            return "redirect:settings"; // using redirect to avoid reposting
        } catch (IllegalArgumentException e) {
            addMessage(redirectAttributes, MessageType.ERROR, "user.profile.change.error", request);
            return "redirect:settings";
        }
    }

    @RequestMapping(value = "password", method = RequestMethod.GET)
    public String changePasswordPrompt(@ModelAttribute User sessionUser) {
        return "user/password";
    }

    @RequestMapping(value = "password", method = RequestMethod.POST)
    public String changePasswordDo(Model model, @ModelAttribute User sessionUser, @RequestParam(value = "otp_entered") String otpField,
                                   @RequestParam String password, RedirectAttributes attributes, HttpServletRequest request) {

        // todo : extra validation
        try {
            userManagementService.resetUserPassword(getUserProfile().getPhoneNumber(), password, otpField);
            addMessage(attributes, MessageType.SUCCESS, "user.profile.password.done", request);
            return "redirect:/home";
        } catch (InvalidTokenException e) {
            addMessage(model, MessageType.ERROR, "user.profile.password.invalid.otp", request);
            return "user/password";
        }
    }

}
