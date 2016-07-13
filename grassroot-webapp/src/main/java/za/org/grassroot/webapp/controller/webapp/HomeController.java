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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.AuthenticationUtil;
import za.org.grassroot.services.*;
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

    @Autowired
    private AuthenticationUtil authenticationUtil;

    @Autowired
    private SigninController signinController;

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
        model.addAttribute("joinRequestsPending", groupJoinRequestService.getOpenRequestsForUser(user.getUid()));
        log.info(String.format("Checking join requests took %d msecs", System.currentTimeMillis() - startTime3));

        return new ModelAndView("home", model.asMap());

    }

    /*

    Code to generate a recursive tree of groups & sub-groups. Since we have de-prioritized, am taking out, but as this
    is fairly complex & non-trivial to reconstruct, have left in here, in comments

     Recursive construction in the view node will turn each of these into a tree with a root node as the top level
     group. This is done through a recursive SQL query rather than via the group list
     */

    /*

    Class construction:
    private long prevRoot = 0;
    private int level = 0;
    private GroupViewNodeSql nodeSql = new GroupViewNodeSql();
    private GroupViewNodeSql subNodeSql = new GroupViewNodeSql();
    private int nodeCount = 0;
    private int idx = 0;
    private GroupTreeDTO node = null;
    private List<GroupTreeDTO> treeList = null;

    Within method:

    treeList = groupBroker.groupTree(user.getUid());

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

    */

}
