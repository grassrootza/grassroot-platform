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
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.GroupJoinRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.AuthenticationUtil;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupJoinRequestService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupViewNodeSql;

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
    GroupBroker groupBroker;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    GroupJoinRequestService groupJoinRequestService;

    @Autowired
    AsyncUserLogger userLogger;

    @Autowired
    AuthenticationUtil authenticationUtil;

    @Autowired
    SigninController signinController;

    private long prevRoot = 0;
    private int level = 0;
    private GroupViewNodeSql nodeSql = new GroupViewNodeSql();
    private GroupViewNodeSql subNodeSql = new GroupViewNodeSql();
    private int nodeCount = 0;
    private int idx = 0;
    private GroupTreeDTO node = null;
    private List<GroupTreeDTO> treeList = null;


    @RequestMapping(value={"/", "/home"})
    public ModelAndView getRootPage(Model model, @ModelAttribute("currentUser") UserDetails userDetails,
                                    HttpServletRequest request) {

        log.info("getRootPage ... attempting to authenticate user ...");

        if (signinController.isAuthenticated()) {
            authenticationUtil.debugAuthentication();
            return generateHomePage(model);
        }

        if (signinController.isRememberMeAuthenticated()) {
            return signinController.autoLogonUser(request, model);
        }

        return new ModelAndView("index", model.asMap());
    }

    /*@RequestMapping("/home")
    public ModelAndView getHomePage(Model model, @ModelAttribute("currentUser") UserDetails userDetails) {
        return generateHomePage(model);
    }*/

    public ModelAndView generateHomePage(Model model) {

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
            homePageModelAndView = getHomePageUserHasNoGroups(model, user);
        }

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Home page generated ... took %d msec", endTime - startTime));
        return homePageModelAndView;


    }

    private ModelAndView getHomePageUserHasNoGroups(Model model, User user) {
        return new ModelAndView("home_new", model.asMap());
    }

    private ModelAndView getHomePageUserHasGroups(Model model, User user) {

        Long startTime = System.currentTimeMillis();
        log.info("getHomePage...NEW tree starting...");
        treeList = groupBroker.groupTree(user.getUid());

        /*
         Recursive construction in the view node will turn each of these into a tree with a root node as the top level
         group. This is done through a recursive SQL query rather than via the group list
        */

        List<GroupViewNodeSql> groupViewNodeSqls = new ArrayList<>();
        nodeCount = treeList.size();
        idx = 0;

        while (idx < nodeCount) {

            node = treeList.get(idx++);
            //log.info("getHomePage..." + idx + "...group..." + node.getGroupName());

            if (node.getRoot() != prevRoot) { // finish of last root and start a new one
                if (prevRoot != 0) { // not the first record
                    groupViewNodeSqls.add(nodeSql);
                }
                level = 0;
                nodeSql = new GroupViewNodeSql(node.getGroupName(), level, node.getParentId());
                prevRoot = node.getRoot();
                continue;
            }
            // recursive call for subnodes - not root
            subNodeSql = recursiveTreeSubnodes(new GroupViewNodeSql(node.getGroupName(), ++level, node.getParentId()));
            nodeSql.getSubgroups().add(subNodeSql);
        }
        // add the last record
        groupViewNodeSqls.add(nodeSql);

        Long endTime = System.currentTimeMillis();
        log.info(String.format("getHomePage...NEW tree ending... took %d msec", endTime - startTime));

        // end of SQL tree

        Long startTime2 = System.currentTimeMillis();
        model.addAttribute("userGroups", permissionBroker.getActiveGroupDTOs(user, null));
        Long endTime2 = System.currentTimeMillis();
        log.info(String.format("Retrieved the active groups for the user ... took %d msecs", endTime2 - startTime2));

        // get lists of outstanding RSVPs and votes
        Long startTime3 = System.currentTimeMillis();
        List<Event> meetingsToRsvp = new ArrayList<>();
        List<Event> votesToAnswer = new ArrayList<>();
        int upcomingEvents = eventManagementService.countUpcomingEvents(user);

        if (upcomingEvents > 0) {
            meetingsToRsvp = eventManagementService.getOutstandingRSVPForUser(user);
            votesToAnswer = eventManagementService.getOutstandingVotesForUser(user);
        }

        Long endTime3 = System.currentTimeMillis();
        log.info(String.format("Retrieved %d events for the user ... took %d msecs", upcomingEvents, endTime3 - startTime3));

        model.addAttribute("meetingRsvps", meetingsToRsvp);
        model.addAttribute("votesToAnswer", votesToAnswer);

        Long startTime4 = System.currentTimeMillis();
        List<GroupJoinRequest> joinRequests = groupJoinRequestService.getOpenRequestsForUser(user.getUid());
        if (joinRequests != null && !joinRequests.isEmpty()) {
            model.addAttribute("joinRequestsPending", joinRequests);
            log.info("Found join requests pending ... " + joinRequests);
        }
        log.info(String.format("Checking join requests took %d msecs", System.currentTimeMillis() - startTime4));

        return new ModelAndView("home", model.asMap());

    }

    private GroupViewNodeSql recursiveTreeSubnodes(GroupViewNodeSql parentNode) {

        // see if there are more records
        while (idx < nodeCount) {
            node = treeList.get(idx++);
            //log.info("recursiveTreeSubnodes..." + idx + "...group..." + node.getGroupName() + "...parent..." + node.getParentId());
            if (node.getRoot() == prevRoot) {

                if (node.getParentId() == parentNode.getParentId()) {
                    parentNode.getSubgroups().add(new GroupViewNodeSql(node.getGroupName(), level, node.getParentId()));
                } else {
                    GroupViewNodeSql childNode = recursiveTreeSubnodes(new GroupViewNodeSql(node.getGroupName(), ++level, node.getParentId()));
                    parentNode.getSubgroups().add(childNode);
                }

            } else {
                // if we get here we to to carry on processing on the outer loop so decrement counter
                idx--;
                break;
            }

        }
        return parentNode;

    }

}
