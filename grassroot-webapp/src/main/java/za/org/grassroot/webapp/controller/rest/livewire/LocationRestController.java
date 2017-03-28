package za.org.grassroot.webapp.controller.rest.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.GroupSearchWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/location", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationRestController {
    private static final Logger log = LoggerFactory.getLogger(LocationRestController.class);

    private final UserManagementService userManagementService;
    private final GeoLocationBroker geoLocationBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public LocationRestController (UserManagementService userManagementService, GroupQueryBroker groupQueryBroker,
                                   GroupJoinRequestService groupJoinRequestService, GeoLocationBroker geoLocationBroker) {
        this.userManagementService = userManagementService;
        this.groupQueryBroker = groupQueryBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping(value = "/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> search (@PathVariable("phoneNumber") String phoneNumber,
                                                   @RequestParam(value = "radius", required = false) Integer radius) {
        String searchTerm = "";
        Integer searchRadius = (radius == null ? 5 : radius);

        // Get user
        User user = userManagementService.findByInputNumber(phoneNumber);
        log.info("the user {} and radius {}", user, radius);

        // Get last user position
        PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(user.getUid());
        log.info("here is the user location : " + lastUserLocation);

        ResponseEntity<ResponseWrapper> responseEntity;
        GroupLocationFilter filter = (lastUserLocation != null ?
                new GroupLocationFilter(lastUserLocation.getLocation(), searchRadius, false) : null);

        log.info("searching for groups and with location filter = {}", filter);
        List<Group> groupsToReturn = groupQueryBroker.findPublicGroups(user.getUid(), searchTerm, filter, false);

        if (groupsToReturn == null || groupsToReturn.isEmpty()) {
            log.info("found no groups ... returning empty ...");
            responseEntity = RestUtil.okayResponseWithData(RestMessage.NO_GROUP_MATCHING_TERM_FOUND, Collections.emptyList());
        } else {
            // next line is a slightly heavy duty way to handle separating task & name queries, vs a quick string comparison on all
            // groups, but (a) ensures no discrepancies in what user sees, and (b) sets up for non-English/case languages
            List<Group> possibleGroupsOnlyName = groupQueryBroker.findPublicGroups(user.getUid(), searchTerm, null, true);

            // note : we likely want to switch this to just getting the groups, via a proper JPQL query (first optimization, then maybe above)
            List<GroupJoinRequest> openRequests = groupJoinRequestService.getOpenUserRequestsForGroupList(user.getUid(), groupsToReturn);

            // Similarly, this should likely be incorporated into the return entity from the broker above, hence refactor once past next
            // version
            List<Group> groupsWithLocation = geoLocationBroker.fetchGroupsWithRecordedLocationsFromSet(new HashSet<>(groupsToReturn));

            log.info("searched for possible groups found {}, which are {}, of which {} have locations", groupsToReturn.size(),
                    groupsToReturn, groupsWithLocation != null ? groupsWithLocation.size() : "null");

            List<GroupSearchWrapper> groupSearchWrappers = groupsToReturn.stream().map(
                    group->new GroupSearchWrapper(group, possibleGroupsOnlyName.contains(group),
                            groupsWithLocation != null && groupsWithLocation.contains(group), openRequests)).sorted(
                    Comparator.reverseOrder()).collect(Collectors.toList());

            responseEntity = RestUtil.okayResponseWithData(RestMessage.POSSIBLE_GROUP_MATCHES, groupSearchWrappers);
        }
        return responseEntity;
    }
}
