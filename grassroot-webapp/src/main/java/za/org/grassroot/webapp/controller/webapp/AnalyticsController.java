package za.org.grassroot.webapp.controller.webapp;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.KeywordDTO;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.time.LocalDate;
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
public class AnalyticsController extends BaseController {

    private Logger log = Logger.getLogger(AnalyticsController.class);

    @Autowired
    private AdminService adminService;

    @Autowired
    private GeoLocationBroker geoLocationBroker;


    @RequestMapping("geo_stats")
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    public String viewGeoInfo(Model model) {

        Long allUsersCount = adminService.countAllUsers();
        Long allGroups = adminService.countActiveGroups();
        int allUsersWithGeoInfoCount = adminService.countUsersWithGeoLocationData();
        int allGroupsWithGeoInfoCount = adminService.countGroupsWithGeoLocationData();
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
                users.stream().map(u -> geoLocationBroker.fetchUserLocation(u.getUid(), LocalDate.now())).collect(Collectors.toList()));
        model.addAttribute("userLocations", userLocations);
        model.addAttribute("geoStats",geoStats);
        return "admin/analytics/geo_stats";

    }

    @RequestMapping("word_stats")
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    public String viewKeywordFequency(Model model) {

        List<KeywordDTO> frequentWords = adminService.getMostFrequentKeyWords();
        model.addAttribute("frequentWords", frequentWords);


        return "admin/analytics/word_stats";

    }

}
