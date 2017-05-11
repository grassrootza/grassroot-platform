package za.org.grassroot.webapp.controller.rest.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TODO 1 - Create token logic
 * TODO 2 - Deal correctly with the exceptions in this class
 */
@RestController
@RequestMapping(value = "/api/location", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationRestController extends BaseController {
    private static int DEFAULT_RADIUS = 5;
    private static final Logger logger = LoggerFactory.getLogger(LocationRestController.class);
    private final GeoLocationBroker geoLocationBroker;
    private final ObjectLocationBroker objectLocationBroker;

    @Autowired
    public LocationRestController (ObjectLocationBroker objectLocationBroker, GeoLocationBroker geoLocationBroker) {
        this.objectLocationBroker = objectLocationBroker;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> search (@RequestParam(value = "latitude", required = true) Double latitude,
                                                   @RequestParam(value = "longitude", required = true) Double longitude,
                                                   @RequestParam(value = "radius", required = false) Integer radius,
                                                   @RequestParam(value = "token", required = true) String token) {

        logger.info("Attempting to list events locations...");

        // Check radius
        Integer searchRadius = (radius == null ? DEFAULT_RADIUS : radius);

        // Create location
        GeoLocation location = new GeoLocation(latitude, longitude);
        logger.info("Location: " + location);

        // Mount filter
        ResponseEntity<ResponseWrapper> responseEntity;
        GroupLocationFilter filter = new GroupLocationFilter(location, searchRadius, false);
        logger.info("Searching for groups and with location filter = {}", filter);

        // Returns list
        List<ObjectLocation> objectsToReturn = new ArrayList<>();

        // Load groups
        List<ObjectLocation> groups;
        try {
            groups = objectLocationBroker.fetchGroupLocations(location, radius);
        } catch (InvalidParameterException e) {
            logger.info("KPI: POST - BAD REQUEST: " + e.getMessage());
            logger.info("Exception class: " + e.getClass());
            logger.info("Stack trace: ", e);
            return RestUtil.errorResponse(RestMessage.INVALID_PARAMETER);
        }

        // Save groups
        objectsToReturn.addAll(groups);
        logger.info("Groups: {}", groups);

        // Load meetings
        if (groups.size() > 0) {
            for (ObjectLocation group : groups) {

                // Get meetings
                List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocationsByGroup(group, location, radius);

                // Concat the results
                objectsToReturn.addAll(meetings);
            }
        } else {
            List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocations(location, radius);

            // Concat the results
            objectsToReturn.addAll(meetings);
        }

        // Check results
        if (objectsToReturn.isEmpty()) {
            logger.info("Found no objects ... returning empty ...");
            responseEntity = RestUtil.okayResponseWithData(RestMessage.NO_GROUP_MATCHING_TERM_FOUND, Collections.emptyList());
        } else {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.POSSIBLE_GROUP_MATCHES, objectsToReturn);
        }
        return responseEntity;
    }
}
