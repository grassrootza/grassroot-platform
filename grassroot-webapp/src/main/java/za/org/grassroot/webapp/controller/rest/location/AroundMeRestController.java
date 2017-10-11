package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

@RestController
@Api("/api/location")
@RequestMapping(value = "/api/location")
public class AroundMeRestController {


    private final ObjectLocationBroker objectLocationBroker;

    @Autowired
    public AroundMeRestController(ObjectLocationBroker objectLocationBroker){
        this.objectLocationBroker = objectLocationBroker;
    }

    @RequestMapping(value = "/public/{userUid}")
    @ApiOperation(value = "All public entities near user", notes = "Fetch all public groups, public meetings, " +
            "and public alerts near to the user")
    public ResponseEntity<List<ObjectLocation>> fetchAllEntitiesNearUser(@PathVariable String userUid,
                                                                         @RequestParam double longitude,
                                                                         @RequestParam double latitude,
                                                                         @RequestParam int radiusMetres,
                                                                         @RequestParam String filterTerm,
                                                                         @RequestParam String searchType) {
        List<ObjectLocation> list = new ArrayList<>();
        // do stuff to populate list
        return ResponseEntity.ok(list);
    }

    @RequestMapping(value = "/public/groups/{userUid}")
    @ApiOperation(value = "All public Groups near user", notes = "Fetch all public groups near to the user")
    public ResponseEntity<List<ObjectLocation>> fetchGroupsNearUser(@PathVariable String userUid,
                                                                    @RequestParam double longitude,
                                                                    @RequestParam double latitude,
                                                                    @RequestParam int radiusMetres,
                                                                    @RequestParam String filterTerm,
                                                                    @RequestParam String searchType){
        GeoLocation location = new GeoLocation(latitude,longitude);
        // merge this broker method with fetchPublicGroupsNearMe to make coherent and reduce redundancy
        return ResponseEntity.ok(objectLocationBroker.fetchUserGroupsNearThem(userUid,location,radiusMetres,filterTerm,searchType));
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ResponseWrapper> invalidLocationPassed() {
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.LOCATION_EMPTY);
    }

    // do similar with a method that looks for only public groups, or only public meetings

    // and have a method that sends back both the public entities, and the entities private to the user (e.g.,
    // meetings of groups they are part of, where meeting or group is within the radius)

}
