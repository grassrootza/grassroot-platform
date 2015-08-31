package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class SigninController {

    private static final Logger logger = LoggerFactory.getLogger(SigninController.class);

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView getLoginPage(@RequestParam (required = false) String error, Model model) {
        logger.debug("Getting login page, error={}", error);

        model.addAttribute("error", error);

        return new ModelAndView("signin", model.asMap());
    }

}
