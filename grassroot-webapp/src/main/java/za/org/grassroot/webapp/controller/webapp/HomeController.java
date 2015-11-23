package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.AuthenticationUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupViewNode;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class HomeController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    AuthenticationUtil authenticationUtil;

    @Autowired
    SigninController signinController;

    @RequestMapping("/")
    public ModelAndView getRootPage(Model model, HttpServletRequest request) {
        log.debug("Getting home page");
        if (signinController.isRememberMeAuthenticated()) {
            return signinController.autoLogonUser(request, model);
        }
        if (signinController.isAuthenticated()) {
            model.addAttribute("userGroups", groupManagementService.getActiveGroupsPartOf(getUserProfile()));
            return new ModelAndView("home",model.asMap());
        }

        return new ModelAndView("index", model.asMap());
    }


    @RequestMapping("/home")
    public String getHomePage(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {

        authenticationUtil.debugAuthentication();

        User user = userManagementService.fetchUserByUsername(userDetails.getUsername());

        /*
         Recursive construction in the view node will turn each of these into a tree with a root node as the top level
         group. There may be a more efficient way to do this than the groupManagement call (and/or optimizing within it
         */

        List<Group> topLevelGroups = groupManagementService.getActiveTopLevelGroups(user);
        List<GroupViewNode> groupViewNodes = new ArrayList<>();
        for (Group group : topLevelGroups) {
            log.info("Creating a group node from group: " + group.getGroupName());
            groupViewNodes.add(new GroupViewNode(group, user, groupManagementService));
        }

        model.addAttribute("userGroups", groupManagementService.getActiveGroupsPartOf(user));
        model.addAttribute("groupTrees", groupViewNodes);

        // get lists of outstanding RSVPs and votes
        List<Event> meetingsToRsvp = eventManagementService.getOutstandingRSVPForUser(user);
        List<Event> votesToAnswer = eventManagementService.getOutstandingVotesForUser(user);

        model.addAttribute("meetingRsvps", meetingsToRsvp);
        model.addAttribute("votesToAnswer", votesToAnswer);

        return "home";
    }


    private List<Event> getConsolidatedGroupEvents(List<Group> groups) {
        List<Event> groupEvents = new ArrayList<>();
        for (Group group : groups) {

            List<Event> events = eventManagementService.findByAppliesToGroup(group);
            if (!events.isEmpty()) {
                groupEvents.addAll(events);
            }
        }
        return groupEvents;
    }

}
