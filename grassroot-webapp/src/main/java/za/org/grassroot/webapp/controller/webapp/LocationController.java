package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GeoFilterFormModel;

import javax.servlet.http.HttpServletRequest;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class LocationController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

    private static final int DEFAULT_RADIUS = 5;
    private static final double defaultLatitude = -26.277636;
    private static final double defaultLongitude = 27.889045;

    private final GeoLocationBroker geoLocationBroker;
    private final ObjectLocationBroker objectLocationBroker;

    @Autowired
    public LocationController(GeoLocationBroker geoLocationBroker, ObjectLocationBroker objectLocationBroker) {
        this.geoLocationBroker = geoLocationBroker;
        this.objectLocationBroker = objectLocationBroker;
    }

    @RequestMapping(value = "/location", method = RequestMethod.GET)
    public String search(@RequestParam(required = false) Integer radius,
                         @RequestParam(required = false) Double latitude,
                         @RequestParam(required = false) Double longitude,
                         HttpServletRequest request, Model model) {

        // Check radius
        Integer searchRadius = (radius == null ? DEFAULT_RADIUS : radius);

        // Get user
        final User user = getUserProfile();
        logger.info("The user {} and radius {}", user, searchRadius);

        // Center the map on either the provided position, or user's last average location, or a default
        // and set the zoom level appropriately (lower for less precise measurements)
        GeoLocation location;
        int zoom = 13;

        // Check parameters
        if (latitude != null && longitude != null) {
            // Check values
            if (latitude < -90.0 || latitude > 90) {
                addMessage(model, MessageType.ERROR, "location.latitude.error", request);
                return "location/map";
            }
            if (longitude < -180.0 || longitude > 180.0) {
                addMessage(model, MessageType.ERROR, "location.longitude.error", request);
                return "location/map";
            }
            location = new GeoLocation(latitude, longitude);
        } else if (geoLocationBroker.fetchUserLocation(user.getUid()) != null) {
            // Use User location
            PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(user.getUid());
            location = lastUserLocation.getLocation();
        } else {
            // Use the default location
            location = new GeoLocation(defaultLatitude, defaultLongitude);
            zoom = 11;
        }
        logger.info("The location {}", location);

        // Returns list
        List<ObjectLocation> objectsToReturn = new ArrayList<>();

        // Load groups
        List<ObjectLocation> groups = null;
        try {
            groups = objectLocationBroker.fetchGroupLocations(location, searchRadius);
        }
        catch (InvalidParameterException e) {
            logger.info("KPI: POST - BAD REQUEST: " + e.getMessage());
            logger.info("Exception class: " + e.getClass());
            logger.info("Stack trace: ", e);
            //TODO: return jsonErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        logger.info("Groups found: {}", groups.size());

        // Save groups
        objectsToReturn.addAll(groups);

        // Load meetings
        List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocations(location, searchRadius);
        objectsToReturn.addAll(meetings);

        // Send response
        model.addAttribute("user", user);
        model.addAttribute("location", location);
        model.addAttribute("radius", searchRadius);
        model.addAttribute("zoom", zoom);
        model.addAttribute("data", objectsToReturn);

        // Create an empty filter object to start using
        model.addAttribute("filter", new GeoFilterFormModel(location, searchRadius));

        return "location/map";
    }

    @RequestMapping(value = "/location/filter", method = RequestMethod.GET)
    public String searchWithFilter(@ModelAttribute GeoFilterFormModel filter, Model model) {
        model.addAttribute("user", getUserProfile());
        model.addAttribute("location", filter.getLocation());
        model.addAttribute("radius", filter.getSearchRadius());

        model.addAttribute("data",
                objectLocationBroker.fetchLocationsWithFilter(GeoFilterFormModel.convertToFilter(filter)));

        return "location/map";
    }

}