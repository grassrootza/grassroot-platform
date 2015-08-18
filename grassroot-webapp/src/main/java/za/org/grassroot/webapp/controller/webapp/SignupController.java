package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.UserRegistration;

import javax.validation.Valid;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class SignUpController extends BaseController{

    @Autowired
    private UserManagementService userManagementService;


    @RequestMapping("/signup")
    public ModelAndView signUpIndex(Model model)
    {
        model.addAttribute("userRegistration", new UserRegistration());
        return new ModelAndView("signup", model.asMap());
    }


    @RequestMapping(value = "/signup", method = RequestMethod.POST)
    public ModelAndView register(Model model, @Valid @ModelAttribute("userRegistration") UserRegistration userRegistration,
                                 BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("userRegistration", userRegistration);
            return new ModelAndView("signup", model.asMap());
        }

       User user = userManagementService.createUserProfile(userRegistration.getUser());

        return new ModelAndView("forward:/login");
    }
}
