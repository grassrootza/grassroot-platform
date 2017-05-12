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
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping(value = "/api/location", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationRestController extends BaseController {
    private static int LOCATION_RADIUS_DEFAULT = 5;
    private static int LOCATION_RADIUS_MAX = 16;
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
                                                   @RequestParam(value = "restriction", required = false) Integer restriction,
                                                   @RequestParam(value = "token", required = true) String token) {

        //TODO: token!
        logger.info("Attempting to list events locations...");

        // Validate parameters
        Integer searchRadius = (radius == null ? LOCATION_RADIUS_DEFAULT : radius);
        if (searchRadius <= 0 || searchRadius > LOCATION_RADIUS_MAX) {
            logger.info(
                    "KPI: GET - BAD REQUEST: Invalid radius. Make sure it is greater than zero and smaller than " + LOCATION_RADIUS_MAX + ".");
            return RestUtil.errorResponse(RestMessage.INVALID_PARAMETER);
        }

        // Check restriction
        Integer useRestriction = (restriction == null ? PUBLIC_LEVEL : restriction);
        if (restriction < PRIVATE_LEVEL || restriction > ALL_LEVEL) {
            logger.info("KPI: GET - BAD REQUEST: Invalid restriction. Make sure it is greater than zero and smaller than " + ALL_LEVEL + ".");
            return RestUtil.errorResponse(RestMessage.INVALID_PARAMETER);
        }

        GeoLocation location = new GeoLocation(latitude, longitude);
        if (!location.isValid()) {
            logger.info("KPI: GET - BAD REQUEST: Invalid location parameter.");
            return RestUtil.errorResponse(RestMessage.INVALID_PARAMETER);
        }

        // Find objects on the given location around the desired radius
        // TODO: filter?
        List<ObjectLocation> objectsToReturn;
        ResponseEntity<ResponseWrapper> responseEntity;

        try {
            objectsToReturn = objectLocationBroker.fetchMeetingLocations(location, searchRadius, useRestriction);
        }
        catch (Exception e){
            logger.info("KPI: GET - INTERNAL SERVER ERROR: " + e.getLocalizedMessage());
            return RestUtil.internalErrorResponse(RestMessage.INVALID_PARAMETER);
        }

        if (objectsToReturn.isEmpty()) {
            logger.info("Found no objects ... returning empty ...");
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_EMPTY, Collections.emptyList());
        } else {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.LOCATION_HAS_MEETINGS, objectsToReturn);
        }
        return responseEntity;
    }
}
