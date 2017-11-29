package za.org.grassroot.webapp.controller.rest.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// todo : delete? think may not be used anymore
@RestController
@RequestMapping(value = "/api/location", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationRestController extends BaseController {
    private static int LOCATION_RADIUS_DEFAULT = 5;
    private static int LOCATION_RADIUS_MAX = 16;
    private static final Logger logger = LoggerFactory.getLogger(LocationRestController.class);
    private final ObjectLocationBroker objectLocationBroker;

    @Autowired
    public LocationRestController (ObjectLocationBroker objectLocationBroker, GeoLocationBroker geoLocationBroker) {
        this.objectLocationBroker = objectLocationBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> search (@RequestParam(value = "latitude", required = true) Double latitude,
                                                   @RequestParam(value = "longitude", required = true) Double longitude,
                                                   @RequestParam(value = "radius", required = false) Integer radius,
                                                   @RequestParam(value = "restriction", required = false) Integer restriction,
                                                   @RequestParam(value = "token", required = true) String token) {

        // Validate parameters
        Integer searchRadius = (radius == null ? LOCATION_RADIUS_DEFAULT : radius);
        if (searchRadius <= 0 || searchRadius > LOCATION_RADIUS_MAX) {
            String errorMsg = "KPI: GET - BAD REQUEST: Invalid radius. Make sure it is greater than zero and smaller than " +
                    LOCATION_RADIUS_MAX + ".";
            logger.info(errorMsg);
            return RestUtil.errorResponse(RestMessage.INVALID_LOCATION_RADIUS_PARAMETER);
        }

        // Check restriction
        Integer useRestriction = (restriction == null ? PUBLIC_LEVEL : restriction);
        if (useRestriction < PRIVATE_LEVEL || useRestriction > ALL_LEVEL) {
            return RestUtil.errorResponse(RestMessage.INVALID_LOCATION_RESTRICTION_PARAMETER);
        }

        GeoLocation location = new GeoLocation(latitude, longitude);
        if (!location.isValid()) {
            return RestUtil.errorResponse(RestMessage.INVALID_LOCATION_LATLONG_PARAMETER);
        }

        // Find objects on the given location around the desired radius
        List<ObjectLocation> objectsToReturn;
        ResponseEntity<ResponseWrapper> responseEntity;

        try {
            objectsToReturn = objectLocationBroker.fetchMeetingLocationsNearUser(getUserProfile(), location, searchRadius, GeographicSearchType.PUBLIC,
                    null);
        }
        catch (Exception e){
            logger.info("KPI: GET - INTERNAL SERVER ERROR: " + e.getLocalizedMessage());
            return RestUtil.internalErrorResponse(RestMessage.INTERNAL_SERVER_ERROR);
        }

        if (objectsToReturn.isEmpty()) {
            logger.info("Found no objects ... returning empty ...");
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_EMPTY, Collections.emptyList());
        } else {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_HAS_MEETINGS, objectsToReturn);
        }
        return responseEntity;
    }

    /**
     * Local class
     */
    public static class BoundingBox {
        public GeoLocation min;
        public GeoLocation max;

        public BoundingBox() {
            // for JPA
        }

        public boolean isValid () {
            return min.isValid() && max.isValid();
        }
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> searchBox (
                    @RequestParam(required = false) Integer restriction,
                    @RequestParam(required = false) String token,
                    @RequestBody BoundingBox boundingBox) {

        logger.info("Attempting to list events locations from bounding box...");

        // Check restriction
        Integer useRestriction = (restriction == null ? PUBLIC_LEVEL : restriction);
        if (useRestriction < PRIVATE_LEVEL || useRestriction > ALL_LEVEL) {
            String errorMsg = "Invalid restriction. Make sure it is greater than zero and smaller than " + ALL_LEVEL + ".";
            logger.info("KPI: POST - BAD REQUEST: " + errorMsg);
            return RestUtil.errorResponse(RestMessage.INVALID_LOCATION_RESTRICTION_PARAMETER);
        }

        // Check bounding box
        if (!boundingBox.isValid()) {
            String errorMsg = "KPI: POST - BAD REQUEST: Invalid bounding box parameter.";
            logger.info(errorMsg);
            return RestUtil.errorResponse(RestMessage.INVALID_BOUNDINGBOX_LATLONG_PARAMETER);
        }

        logger.info("The bounding box {} - {}", boundingBox.min, boundingBox.max);

        // Returns list
        List<ObjectLocation> objectsToReturn = new ArrayList<>();

        // Load meetings
        /*try {
            objectsToReturn = objectLocationBroker.fetchMeetingLocations(boundingBox.min, boundingBox.max, useRestriction);
            logger.info("Meetings found: {}", objectsToReturn.size());
        }
        catch (Exception e){
            logger.info("KPI: POST - INTERNAL SERVER ERROR: " + e.getLocalizedMessage());
            return RestUtil.internalErrorResponse(RestMessage.INTERNAL_SERVER_ERROR);
        }*/

        // Send response
        ResponseEntity<ResponseWrapper> responseEntity;
        if (objectsToReturn.isEmpty()) {
            logger.info("Found no objects ... returning empty ...");
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_EMPTY, Collections.emptyList());
        } else {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_HAS_MEETINGS, objectsToReturn);
        }
        return responseEntity;
    }
}
