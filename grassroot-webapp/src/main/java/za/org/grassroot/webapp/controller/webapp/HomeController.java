package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.GroupJoinRequestService;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class HomeController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;

    @Autowired
    private AsyncUserLogger userLogger;

    @RequestMapping(value={"/", "/home"})
    public ModelAndView getRootPage(Model model, @ModelAttribute("currentUser") UserDetails userDetails,
                                    HttpServletRequest request) {

        log.info("getRootPage ... attempting to authenticate user ...");

        Long startTime = System.currentTimeMillis();
        ModelAndView homePageModelAndView;
        log.info("Getting user profile ... ");
        User user = getUserProfile();

        // will check cache & only log if not called for several hours, i.e., will not log spurious returns to home page
        userLogger.recordUserSession(getUserProfile().getUid(), UserInterfaceType.WEB);
        Long startTimeCountGroups = System.currentTimeMillis();
        log.info(String.format("Getting user profile took %d msecs", startTimeCountGroups - startTime));

        if (userManagementService.isPartOfActiveGroups(user)) {
            log.info(String.format("Counting user groups took ... %d msecs", System.currentTimeMillis() - startTimeCountGroups));
            homePageModelAndView = getHomePageUserHasGroups(model, user);
        } else {
            homePageModelAndView = getHomePageUserHasNoGroups(model);
        }

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Home page generated ... took %d msec", endTime - startTime));
        return homePageModelAndView;


    }

    private ModelAndView getHomePageUserHasNoGroups(Model model) {
        return new ModelAndView("home_new", model.asMap());
    }

    private ModelAndView getHomePageUserHasGroups(Model model, User user) {

        Long startTime1 = System.currentTimeMillis();
        model.addAttribute("userGroups", permissionBroker.getActiveGroupDTOs(user, null));
        log.info(String.format("Retrieved the active groups for the user ... took %d msecs", System.currentTimeMillis() - startTime1));

        Long startTime2 = System.currentTimeMillis();
        model.addAttribute("upcomingTasks", taskBroker.fetchUpcomingUserTasks(user.getUid()));
        log.info(String.format("Retrieved the user's upcoming tasks ... took %d msecs", System.currentTimeMillis() - startTime2));

        Long startTime3 = System.currentTimeMillis();
        model.addAttribute("joinRequestsPending", groupJoinRequestService.getPendingRequestsForUser(user.getUid()));
        log.info(String.format("Checking join requests took %d msecs", System.currentTimeMillis() - startTime3));

        return new ModelAndView("home", model.asMap());

    }

}
