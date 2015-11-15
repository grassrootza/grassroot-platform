package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import za.org.grassroot.core.util.AuthenticationUtil;
import za.org.grassroot.services.UserManagementService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Optional;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class SigninController {

    private static final Logger logger = LoggerFactory.getLogger(SigninController.class);

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    AuthenticationUtil authenticationUtil;

    //private Authentication authentication;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView getLoginPage(@RequestParam (required = false) String error, Model model, HttpServletRequest request) {
        logger.info("Getting login page, error={}", error);
        authenticationUtil.debugAuthentication();
        if (isRememberMeAuthenticated()) {
            logger.info("isRememberMeAuthenticated...true");
             return autoLogonUser(request, model);
        } else {
            logger.info("isRememberMeAuthenticated...false");

        }
        if (isAuthenticated()) {
            logger.info("isAuthenticated...true");
            return new ModelAndView("/home",model.asMap());
        }
        model.addAttribute("error", error);

        return new ModelAndView("signin", model.asMap());
    }
    /**
     * If the login in from remember me cookie, refer
     * org.springframework.security.authentication.AuthenticationTrustResolverImpl
     */
    public boolean isRememberMeAuthenticated() {

        logger.info("isRememberMeAuthenticated...");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.info("isRememberMeAuthenticated...authentication is NULL");
            return false;
        }
        return RememberMeAuthenticationToken.class.isAssignableFrom(authentication.getClass());

    }
    public boolean isAuthenticated() {

        logger.info("isAuthenticated...");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.info("isAuthenticated...authentication is NULL");
            return false;
        }
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication.getClass());

    }
    /**
     * Authenticate User and Redirect to Main Page
     */
    public ModelAndView autoLogonUser(HttpServletRequest request, Model model) {
        logger.info("autoLogonUser...");
        try {
            request.getSession();//ensure that the session exists
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            logger.info("getAuthorities..." +  userDetails.getAuthorities().toString());
            //authentication = new RememberMeAuthenticationToken(userDetails.getUsername(), userDetails, userDetails.getAuthorities());
            authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.info("autoLogonUser...return...ModelAndView.../home");
            return new ModelAndView("/home", model.asMap());
        } catch (Exception e) {
            throw new AuthenticationServiceException("Problem  auto logging user", e);
        }
    }


}
