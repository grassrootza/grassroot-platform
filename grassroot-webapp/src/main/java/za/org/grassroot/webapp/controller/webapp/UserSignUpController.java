package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserExistsException;
import za.org.grassroot.services.UserManagementService;
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

    @Autowired
    private UserManagementService userManagementService;


    @RequestMapping("/signup")
    public ModelAndView signUpIndex(Model model) {
        model.addAttribute("userRegistration", new UserRegistration());
        return new ModelAndView("signup", model.asMap());
    }


    @RequestMapping(value = "/signup", method = RequestMethod.POST)
    public ModelAndView register(Model model, @Valid @ModelAttribute("userRegistration") UserRegistration userRegistration,
                                 BindingResult bindingResult, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            if (bindingResult.hasErrors()) {
                model.addAttribute("userRegistration", userRegistration);
                return new ModelAndView("signup", model.asMap());
            }

            User user = userManagementService.createUserWebProfile(userRegistration.getUser());

            addMessage(redirectAttributes, MessageType.SUCCESS, "user.creation.successful", request);
            return new ModelAndView(new RedirectView("/signin"));
        }
        catch (UserExistsException userException)
        {
            addMessage(model,MessageType.INFO,"user.creation.exception.userExists",request);
            log.error("Error saving user. User exists.",userException);
            return new ModelAndView("signup", model.asMap());
        }
        catch (Exception e) {
            log.error("Error saving user.",e);
            addMessage(model,MessageType.ERROR,"user.creation.exception",request);
            return new ModelAndView("signup", model.asMap());
        }
    }
}
