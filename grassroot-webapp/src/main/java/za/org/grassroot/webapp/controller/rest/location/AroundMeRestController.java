package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import java.util.ArrayList;
import java.util.List;

@RestController
@Api("/api/location")
@RequestMapping(value = "/api/location")
public class AroundMeRestController {

    @RequestMapping(value = "/public/{userUid}")
    @ApiOperation(value = "All public entities near user", notes = "Fetch all public groups, public meetings, " +
            "and public alerts near to the user")
    public ResponseEntity<List<ObjectLocation>> fetchAllEntitiesNearUser(@PathVariable  String userUid,
                                                                         @RequestParam double longitude,
                                                                         @RequestParam double latitude,
                                                                         @RequestParam int radiusMetres,
                                                                         @RequestParam String filterTerm) {
        List<ObjectLocation> list = new ArrayList<>();
        // do stuff to populate list
        return ResponseEntity.ok(list);
    }

    // do similar with a method that looks for only public groups, or only public meetings

    // and have a method that sends back both the public entities, and the entities private to the user (e.g.,
    // meetings of groups they are part of, where meeting or group is within the radius)

}
