package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.UserManagementService;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by luke on 2016/05/12.
 */
@Controller @Slf4j
@RequestMapping("/emulator/test")
@ConditionalOnProperty(name = "grassroot.test.controller.enabled", havingValue = "true",  matchIfMissing = false)
public class TestEmulatorController extends BaseRestController {

    private final GroupBroker groupBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final PermissionBroker permissionBroker;

    public TestEmulatorController(JwtService jwtService, UserManagementService userManagementService, GroupBroker groupBroker, GroupQueryBroker groupQueryBroker, GeoLocationBroker geoLocationBroker, PermissionBroker permissionBroker) {
        super(jwtService, userManagementService);
        this.groupBroker = groupBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.permissionBroker = permissionBroker;
    }

    @RequestMapping(value = "/ajax/list", method = RequestMethod.GET)
    public String ajaxListMembersView(Model model, HttpServletRequest request) {

        Set<Group> groups = permissionBroker.getActiveGroupsWithPermission(getUserFromRequest(request), null);

        model.addAttribute("groups", new ArrayList<>(groups));
        model.addAttribute("userUid", getUserIdFromRequest(request));

        return "emulator/ajax_list";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/geo/start", method = RequestMethod.GET)
    public String locationTestStart(Model model, HttpServletRequest request) {

        Set<Group> groups = permissionBroker.getActiveGroupsWithPermission(getUserFromRequest(request), null);
        log.info("Found this many groups for the user: " + groups.size());
        model.addAttribute("groups", new ArrayList<>(groups));
        model.addAttribute("userUid", getUserIdFromRequest(request));
        return "emulator/geo_start";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/geo/user", method = RequestMethod.GET)
    public String locationUserCalculate(Model model, @RequestParam(value = "groupUid", required = false) String groupUid,
                                        @RequestParam(value="calculate", required = false) boolean calculate) {

        log.info("Inside user calculation! For this groupUid = " + groupUid);

        final LocalDate today = LocalDate.now();
        if (calculate) geoLocationBroker.calculatePreviousPeriodUserLocations(today);

        List<User> usersToDisplay;
        Map<User, PreviousPeriodUserLocation> userLocationMap = new HashMap<>();

        if (groupUid != null && !groupUid.trim().equals("")) {
            usersToDisplay = new ArrayList<>(groupBroker.load(groupUid).getMembers());
        } else {
            usersToDisplay = geoLocationBroker.fetchUsersWithRecordedAverageLocations(today);
        }

        // this is going to be incredibly slow ... once get to using & analyzing, will need to optimize, a lot
        for (User u : usersToDisplay) {
            userLocationMap.put(u, geoLocationBroker.fetchUserLocation(u.getUid(), today));
        }

        model.addAttribute("userLocations", userLocationMap);
        return "emulator/geo_user";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/geo/group", method = RequestMethod.GET)
    public String locationGroupCalculate(Model model, @RequestParam("groupUid") String groupUid, @RequestParam boolean calculate) {
        final LocalDate today = LocalDate.now();

        if (groupUid != null && !groupUid.trim().equals("")) {
            if (calculate) geoLocationBroker.calculateGroupLocation(groupUid, LocalDate.now());

            Group group = groupBroker.load(groupUid);
            GroupLocation groupLocation = geoLocationBroker.fetchGroupLocationWithScoreAbove(groupUid, today, 0);

            model.addAttribute("group", group);
            model.addAttribute("groupLocation", groupLocation);

            List<PreviousPeriodUserLocation> memberLocations = new ArrayList<>();
            for (User u : group.getMembers())
                memberLocations.add(geoLocationBroker.fetchUserLocation(u.getUid(), today));
            model.addAttribute("userLocations", memberLocations);

            return "emulator/geo_group_single_result";
        } else {
            List<Group> allGroups = groupQueryBroker.loadAll();
            List<GroupLocation> allGroupLocations = new ArrayList<>();

            for (Group g : allGroups) {
                if (calculate) geoLocationBroker.calculateGroupLocation(g.getUid(), LocalDate.now());
                allGroupLocations.add(geoLocationBroker.fetchGroupLocationWithScoreAbove(g.getUid(), today, 0));
            }

            model.addAttribute("groupLocations", allGroupLocations);

            return "emulator/geo_group_all_results";
        }
    }

}
