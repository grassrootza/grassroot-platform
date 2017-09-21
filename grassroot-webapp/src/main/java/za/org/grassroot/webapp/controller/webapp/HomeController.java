package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class HomeController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final TaskBroker taskBroker;
    private final GroupFetchBroker groupFetchBroker;
    private final GroupJoinRequestService groupJoinRequestService;
    private final AsyncUserLogger userLogger;

    @Autowired
    public HomeController(TaskBroker taskBroker, GroupFetchBroker groupFetchBroker, GroupJoinRequestService groupJoinRequestService,
                          AsyncUserLogger userLogger) {
        this.taskBroker = taskBroker;
        this.groupFetchBroker = groupFetchBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.userLogger = userLogger;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String getLoginPage() {
        return "/login";
    }

    @RequestMapping(value={"/", "/home"})
    public ModelAndView getRootPage(Model model, @ModelAttribute("currentUser") UserDetails userDetails, HttpServletRequest request) {

        Long startTime = System.currentTimeMillis();
        ModelAndView homePageModelAndView;
        User user = userManagementService.load(getUserProfile().getUid());

        if (user.getPrimaryAccount() != null && !user.getPrimaryAccount().isEnabled()) {
            addMessage(model, MessageType.ERROR, "account.disabled.banner", request);
        }

        // will check cache & only log if not called for several hours, i.e., will not log spurious returns to home page
        userLogger.recordUserSession(user.getUid(), UserInterfaceType.WEB);
        Long startTimeCountGroups = System.currentTimeMillis();

        log.info(String.format("Getting user profile took %d msecs", startTimeCountGroups - startTime));

        if (permissionBroker.countActiveGroupsWithPermission(user, null) != 0) {
            log.info(String.format("Counting user groups took ... %d msecs", System.currentTimeMillis() - startTimeCountGroups));
            homePageModelAndView = getHomePageUserHasGroups(model, user);
        } else {
            homePageModelAndView = getHomePageUserHasNoGroups(model, user);
        }

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Home page generated ... took %d msec", endTime - startTime));
        return homePageModelAndView;
    }

    private ModelAndView getHomePageUserHasNoGroups(Model model, User user) {
        model.addAttribute("account", user.getPrimaryAccount());
        return new ModelAndView("home_new", model.asMap());
    }

    private ModelAndView getHomePageUserHasGroups(Model model, User user) {

        Long startTime1 = System.currentTimeMillis();
        model.addAttribute("userGroups", groupFetchBroker.fetchAllUserGroupsSortByLatestTime(user.getUid()));
        log.info(String.format("Retrieved the active groups for the user ... took %d msecs", System.currentTimeMillis() - startTime1));

        Long startTime2 = System.currentTimeMillis();
        model.addAttribute("upcomingTasks", taskBroker.fetchUpcomingUserTasks(user.getUid()));
        log.debug(String.format("Retrieved the user's upcoming tasks ... took %d msecs", System.currentTimeMillis() - startTime2));

        Long startTime3 = System.currentTimeMillis();
        model.addAttribute("joinRequestsPending", groupJoinRequestService.getPendingRequestsForUser(user.getUid()));
        log.debug(String.format("Checking join requests took %d msecs", System.currentTimeMillis() - startTime3));

        return new ModelAndView("home", model.asMap());

    }

}
