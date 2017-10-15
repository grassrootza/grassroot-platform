package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@Api("/api/location")
@RequestMapping(value = "/api/location")
public class AroundMeRestController {


    private final ObjectLocationBroker objectLocationBroker;
    private final UserRepository userRepository;
    private final LiveWireAlertBroker liveWireAlertBroker;

    @Autowired
    public AroundMeRestController(ObjectLocationBroker objectLocationBroker,UserRepository userRepository,LiveWireAlertBroker liveWireAlertBroker){
        this.objectLocationBroker = objectLocationBroker;
        this.userRepository = userRepository;
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/public/{userUid}")
    @ApiOperation(value = "All public entities near user", notes = "Fetch all public groups, public meetings, " +
            "and public alerts near to the user")
    public ResponseEntity<List<ObjectLocation>> fetchAllEntitiesNearUser(@PathVariable String userUid,
                                                                         @RequestParam double longitude,
                                                                         @RequestParam double latitude,
                                                                         @RequestParam int radiusMetres,
                                                                         @RequestParam String filterTerm,
                                                                         String searchTerm) {
        GeoLocation location = new GeoLocation(latitude,longitude);
        User user = userRepository.findOneByUid(userUid);

        Set<ObjectLocation> objectLocationSet = new HashSet<>();

        objectLocationSet.addAll(objectLocationBroker.fetchGroupsNearby(location,radiusMetres,searchTerm,filterTerm,userUid)); //Adding Groups near user
        objectLocationSet.addAll(objectLocationBroker.fetchMeetingLocationsNearUser(user, location, radiusMetres, GeographicSearchType.PUBLIC, null)); // Adding Meetings near user

        List<ObjectLocation> list = new ArrayList<>(objectLocationSet);

        return ResponseEntity.ok(list);
    }

    @RequestMapping(value = "/public/groups/{userUid}")
    @ApiOperation(value = "All public Groups near user", notes = "Fetch all public groups near to the user")
    public ResponseEntity<List<ObjectLocation>> fetchGroupsNearUser(@PathVariable String userUid,
                                                                    @RequestParam double longitude,
                                                                    @RequestParam double latitude,
                                                                    @RequestParam int radiusMetres,
                                                                    @RequestParam String filterTerm){
        GeoLocation location = new GeoLocation(latitude,longitude);
        // merge this broker method with fetchPublicGroupsNearMe to make coherent and reduce redundancy
        return ResponseEntity.ok(objectLocationBroker.fetchUserGroupsNearThem(userUid,location,radiusMetres,filterTerm));
    }

    @RequestMapping(value = "/public/alerts/{userUid}")
    @ApiOperation(value = "All public Alerts near user", notes = "Fetch all public alerts near to the user")
    public ResponseEntity<List<LiveWireAlert>> getAlertsNearUser(@PathVariable String userUid,
                                                                  @RequestParam double longitude,
                                                                  @RequestParam double latitude,
                                                                  @RequestParam int radiusMetres,
                                                                  @RequestParam String createdByMe){
        GeoLocation location = new GeoLocation(latitude,longitude);

        List<LiveWireAlert> liveWireAlerts = liveWireAlertBroker.fetchAlertsNearUser(userUid,location,createdByMe,radiusMetres);

        return ResponseEntity.ok(liveWireAlerts);
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ResponseWrapper> invalidLocationPassed() {
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.LOCATION_EMPTY);
    }



    // do similar with a method that looks for only public groups, or only public meetings

    // and have a method that sends back both the public entities, and the entities private to the user (e.g.,
    // meetings of groups they are part of, where meeting or group is within the radius)

}
