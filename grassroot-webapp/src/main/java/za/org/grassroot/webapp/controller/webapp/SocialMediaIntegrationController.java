package za.org.grassroot.webapp.controller.webapp;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.user.UserManagementService;

@Controller
public class SocialMediaIntegrationController {

    @Autowired
    private UserManagementService userManagementService;

    @RequestMapping("/socialmedia/connect/{providerId}")
    public RedirectView redirectToSocialMedia(
            @PathVariable("providerId") String providerId,
            @ModelAttribute("currentUser") UserDetails userDetails) {
        User user = userManagementService.fetchUserByUsernameStrict(userDetails.getUsername());
        RedirectView redirectView = new RedirectView();
        String location = "http://localhost:8085/connect/"+providerId+"?authuser_uid="+user.getUid();
        redirectView.setUrl(location);
        return redirectView;
    }

}
