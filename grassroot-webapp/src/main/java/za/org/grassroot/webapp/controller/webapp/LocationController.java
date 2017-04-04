package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Controller
public class LocationController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(LocationController.class);
    private static final int DEFAULT_RADIUS = 5;

    private final GeoLocationBroker geoLocationBroker;
    private final ObjectLocationBroker objectLocationBroker;

    @Autowired
    public LocationController (GeoLocationBroker geoLocationBroker, ObjectLocationBroker objectLocationBroker) {
        this.geoLocationBroker = geoLocationBroker;
        this.objectLocationBroker = objectLocationBroker;
    }

    @RequestMapping(value = "/location", method = RequestMethod.GET)
    public String search (@RequestParam(required = false) Integer radius,
                          @RequestParam(required = false) Double latitude,
                          @RequestParam(required = false) Double lontitude,
                          Model model, RedirectAttributes attributes,
                          HttpServletRequest request) {

        // Check radius
        Integer searchRadius = (radius == null ? DEFAULT_RADIUS : radius);

        // Get user
        final User user = getUserProfile();
        log.info("the user {} and radius {}", user, radius);

        // Get last user position
        GeoLocation location;
        if (latitude != null && lontitude != null) {
            location = new GeoLocation(latitude, lontitude);
            log.info("here is the location: " + location);
        } else {
            PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(user.getUid());
            log.info("here is the user location: " + lastUserLocation);
            location = lastUserLocation.getLocation();
        }

        // Returns list
        List<ObjectLocation> objectsToReturn = new ArrayList<>();

        // Load groups
        List<ObjectLocation> groups = objectLocationBroker.fetchGroupLocations(location, searchRadius);
        log.info("groups found: {}", groups.size());

        // Save groups
        objectsToReturn.addAll(groups);

        // Load meetings
        if (true) { //TODO: Use the new table [meeting_location]
            for (ObjectLocation group : groups) {
                // Get meetings
                List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocationsByGroup(group, location, searchRadius);
                // Concat the results
                objectsToReturn.addAll(meetings);
            }
        } else {
            List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocations(location, searchRadius);

            // Concat the results
            objectsToReturn.addAll(meetings);
        }

        // Send response
        model.addAttribute("user", user);
        model.addAttribute("location", location);
        model.addAttribute("radius", searchRadius);
        model.addAttribute("data", objectsToReturn);

        return "location/map";
    }

}
