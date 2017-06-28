package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.AnalyticalService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/08/08.
 */

@Controller
@RequestMapping(value = "admin/analytics")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AnalyticsController extends BaseController {

    private final GroupRepository groupRepository;
    private final AnalyticalService analyticalService;
    private final GeoLocationBroker geoLocationBroker;

    @Autowired
    public AnalyticsController(GroupRepository groupRepository, AnalyticalService analyticalService, GeoLocationBroker geoLocationBroker) {
        this.groupRepository = groupRepository;
        this.analyticalService = analyticalService;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping("geo_stats")
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    public String viewGeoInfo(Model model) {

        Long allUsersCount = analyticalService.countAllUsers();
        Long allGroups = analyticalService.countActiveGroups();
        int allUsersWithGeoInfoCount = analyticalService.countUsersWithGeoLocationData();
        int allGroupsWithGeoInfoCount = analyticalService.countGroupsWithGeoLocationData();
        Map<String, Integer> geoStats = new HashMap<>();
        geoStats.put("totalGroupsWithGeoData", allGroupsWithGeoInfoCount);
        geoStats.put("totalUsersWithGeoData", allUsersWithGeoInfoCount);
        //will only become an issue once user count or group count exceeds Integer.Max_Value
        geoStats.put("totalGroupsWithoutGeoData", allGroups.intValue()-allGroupsWithGeoInfoCount);
        geoStats.put("totalUsersWithoutGeoData", allUsersCount.intValue()-allUsersWithGeoInfoCount);
        List<User> users;
        List<PreviousPeriodUserLocation> userLocations = new ArrayList<>();
        users = geoLocationBroker.fetchUsersWithRecordedAverageLocations(LocalDate.now());
        userLocations.addAll(
                users.stream().map(u -> geoLocationBroker.fetchUserLocation(u.getUid())).collect(Collectors.toList()));
        model.addAttribute("userLocations", userLocations);
        model.addAttribute("geoStats",geoStats);
        return "admin/analytics/geo_stats";
    }

    // note: this is _very_ intensive so only trigger locally, never on production
    @RequestMapping("trigger/groups")
    public String triggerGroupCalcs(RedirectAttributes attributes, HttpServletRequest request) {
        LocalDate now = LocalDate.now();
        groupRepository.findAll()
                .forEach(g -> geoLocationBroker.calculateGroupLocation(g.getUid(), now));
        addMessage(attributes, MessageType.SUCCESS, "admin.geo.groupscalc.done", request);
        return "redirect:/admin/analytics/geo_stats";
    }

    @RequestMapping("trigger/users")
    public String triggerUserCalcs(RedirectAttributes attributes, HttpServletRequest request) {
        geoLocationBroker.calculatePreviousPeriodUserLocations(LocalDate.now());
        addMessage(attributes, MessageType.SUCCESS, "admin.geo.userscalc.done", request);
        return "redirect:/admin/analytics/geo_stats";
    }

    @RequestMapping("word_stats")
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    public String viewKeywordFequency(Model model) {

        List<KeywordDTO> frequentWords = analyticalService.getKeywordStats(LocalDateTime.now().minusDays(365));
        model.addAttribute("frequentWords", frequentWords);

        return "admin/analytics/word_stats";

    }

}
