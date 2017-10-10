package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.ObjectLocationBroker;

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
        List<ObjectLocation> objectLocations;
        GeoLocation location = new GeoLocation(latitude,longitude);
        ResponseEntity<List<ObjectLocation>> listResponseEntity = null;
        if(location.isValid()){
            objectLocations = objectLocationBroker.fetchGroupsNearUser(userUid,location,radiusMetres,filterTerm,searchType);
            if(objectLocations != null){
                listResponseEntity = new ResponseEntity<>(objectLocations, HttpStatus.OK);
            }
        }
        return listResponseEntity;
    }

    // do similar with a method that looks for only public groups, or only public meetings

    // and have a method that sends back both the public entities, and the entities private to the user (e.g.,
    // meetings of groups they are part of, where meeting or group is within the radius)

}
