package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.exception.UserExistsException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.UserRegistration;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class UserSignUpController extends BaseController {

    private Logger log = LoggerFactory.getLogger(UserSignUpController.class);

    private final UserManagementService userManagementService;

    @Autowired
    public UserSignUpController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @RequestMapping("/signup/extra")
    public String accountSignup(@RequestParam(required = false) String accountType, RedirectAttributes attributes) {
        log.info("inside signup from outside, on account path");
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth != null && currentAuth.isAuthenticated() && !(currentAuth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/account/signup";
        } else {
            log.info("not authenticated, going to register page");
            if (!StringUtils.isEmpty(accountType)) {
                attributes.addAttribute("accountType", accountType);
            }
            return "redirect:/signup";
        }
    }

    @RequestMapping("/signup")
    public String signUpIndex(Model model, @RequestParam(required = false) String accountType) {
        model.addAttribute("userRegistration", new UserRegistration());
        if (!StringUtils.isEmpty(accountType)) {
            model.addAttribute("accountType", accountType);
        }
        return "signup";
    }

    @RequestMapping(value = "/signup", method = RequestMethod.POST)
    public String register(Model model, @Valid @ModelAttribute("userRegistration") UserRegistration userRegistration,
                                 BindingResult bindingResult, @RequestParam(required = false) String accountType,
                                 HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            if (bindingResult.hasErrors()) {
                log.info("Error in sign up! Error output: {}", bindingResult.getAllErrors().toString());
                model.addAttribute("userRegistration", userRegistration);
                return "signup";
            }

            User user = userManagementService.createUserWebProfile(userRegistration.getUser());
            setAuthentication(user, request);
            if (StringUtils.isEmpty(accountType)) {
                addMessage(redirectAttributes, MessageType.SUCCESS, "user.creation.successful", request);
                return "redirect:/home";
            } else {
                log.info("adding account type : {}", accountType);
                redirectAttributes.addAttribute("accountType", accountType);
                return "redirect:/account/signup";
            }
        } catch (UserExistsException userException) {
            addMessage(model, MessageType.INFO, "user.creation.exception.userExists", request);
            log.error("Error saving user. User exists.");
            return "signup";
        } catch (Exception e) {
            log.error("Error saving user.", e);
            addMessage(model, MessageType.ERROR, "user.creation.exception", request);
            return "signup";
        }
    }

    private void setAuthentication(UserDetails userDetails, HttpServletRequest request) {
        try {
            request.getSession();
            Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.error("Error creating authentication after signup: {}", e.toString());
            throw new AuthenticationServiceException("Problem  auto logging user", e);
        }
    }
}
