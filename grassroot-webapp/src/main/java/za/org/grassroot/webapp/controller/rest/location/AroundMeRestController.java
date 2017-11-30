package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.LiveWireAlertDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Api("/api/location")
@RequestMapping(value = "/api/location")
public class AroundMeRestController {

    private final ObjectLocationBroker objectLocationBroker;
    private final UserManagementService userManager;
    private final GroupBroker groupBroker;
    private final MeetingRepository meetingRepository;
    private final LiveWireAlertBroker liveWireAlertBroker;

    @Autowired
    public AroundMeRestController(ObjectLocationBroker objectLocationBroker, UserManagementService userManager, GroupBroker groupBroker, MeetingRepository meetingRepository, LiveWireAlertBroker liveWireAlertBroker){
        this.objectLocationBroker = objectLocationBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.meetingRepository = meetingRepository;
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    @RequestMapping(value = "/all/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All entities near user", notes = "Fetch all public groups, public meetings, " +
            "and public alerts near to the user, as well as those entities the user belongs to")
    public ResponseEntity<List<AroundMeDTO>> fetchAllEntitiesNearUser(@PathVariable String userUid,
                                                                      @RequestParam double latitude,
                                                                      @RequestParam double longitude,
                                                                      @RequestParam int radiusMetres,
                                                                      @ApiParam(value = "Whether to return entities that the user is part of (private), " +
                                                                              "or only those they are not (public), or both (default)")
                                                                      @RequestParam(required = false) GeographicSearchType searchType,
                                                                      @ApiParam(value = "Whether to include public groups in " +
                                                                              "the results (which can expand result set a lot)")
                                                                      @RequestParam(required = false) Boolean includeGroups,
                                                                      @ApiParam(value = "An optional term to filter by " +
                                                                                 "name (subject etc) of the entities")
                                                                      @RequestParam(required = false) String filterTerm) {
        GeoLocation location = new GeoLocation(latitude,longitude);
        User user = userManager.load(userUid);

        Set<AroundMeDTO> objectLocationSet = new HashSet<>();

        GeographicSearchType type = searchType == null ? GeographicSearchType.PUBLIC : searchType;
        if (includeGroups != null && includeGroups) {
            objectLocationSet.addAll(objectLocationBroker
                    .fetchGroupsNearby(userUid, location, radiusMetres, filterTerm, type)
                    .stream().map(gl -> convertGroupLocation(gl, user)).collect(Collectors.toList())); //Adding Groups near user
        }
        objectLocationSet.addAll(objectLocationBroker
                .fetchMeetingLocationsNearUser(user, location, radiusMetres, type, null)
                .stream().map(ml -> convertMeetingLocation(ml, user)).collect(Collectors.toList())); // Adding Meetings near user
        objectLocationSet.addAll(liveWireAlertBroker.fetchAlertsNearUser(userUid, location, radiusMetres, type)
                .stream().map(al -> convertLiveWireAlert(al, user)).collect(Collectors.toList()));

        return ResponseEntity.ok(new ArrayList<>(objectLocationSet));
    }

    @RequestMapping(value = "/all/groups/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All groups near user", notes = "Fetch all groups near to the user, both those they belong to" +
            " and those they don't")
    public ResponseEntity<List<ObjectLocation>> fetchGroupsNearUser(@PathVariable String userUid,
                                                                    @RequestParam double longitude,
                                                                    @RequestParam double latitude,
                                                                    @RequestParam int radiusMetres,
                                                                    @RequestParam(required = false) String filterTerm){
        GeoLocation location = new GeoLocation(latitude,longitude);
        return ResponseEntity.ok(objectLocationBroker.fetchGroupsNearby(userUid,location,radiusMetres,filterTerm,
                GeographicSearchType.PUBLIC));
    }

    @RequestMapping(value = "/all/alerts/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "All public alerts near user", notes = "Fetch all public alerts near to the user")
    public ResponseEntity<List<LiveWireAlertDTO>> getAlertsNearUser(@PathVariable String userUid,
                                                                    @RequestParam double longitude,
                                                                    @RequestParam double latitude,
                                                                    @RequestParam int radiusMetres,
                                                                    @RequestParam(required = false) GeographicSearchType searchType){
        GeoLocation location = new GeoLocation(latitude,longitude);
        GeographicSearchType type = searchType == null ? GeographicSearchType.PUBLIC : searchType;
        log.info("searching for alerts near user at location : {}", location);
        List<LiveWireAlert> liveWireAlerts = liveWireAlertBroker.fetchAlertsNearUser(userUid,location,
                radiusMetres, type);
        log.info("found alerts ? {}, look like : {}", !liveWireAlerts.isEmpty(), liveWireAlerts);
        return ResponseEntity.ok(liveWireAlerts.stream()
                .map(LiveWireAlertDTO::new).collect(Collectors.toList()));
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ResponseWrapper> invalidLocationPassed() {
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.LOCATION_EMPTY);
    }

    // todo : improve these overall (e.g., fewer queries, neater name generation)
    private AroundMeDTO convertGroupLocation(ObjectLocation groupLocation, User user) {
        Group group = groupBroker.load(groupLocation.getUid());
        final String contactName = group.getJoinApprover() != null ?
                group.getJoinApprover().getName() : group.getCreatedByUser().getName();
        return new AroundMeDTO(groupLocation.getUid(),
                GeoLocatedEntityType.GROUP,
                groupLocation.getName(),
                groupLocation.getDescription(),
                groupLocation.getName(),
                group.hasMember(user),
                contactName,
                group.getCreatedDateTime().toEpochMilli(),
                groupLocation.getLatitude(),
                groupLocation.getLongitude());
    }

    private AroundMeDTO convertMeetingLocation(ObjectLocation mtgLocation, User user) {
        Meeting meeting = meetingRepository.findOneByUid(mtgLocation.getUid());
        return new AroundMeDTO(meeting.getUid(),
                GeoLocatedEntityType.MEETING,
                mtgLocation.getName(),
                mtgLocation.getDescription(),
                meeting.getAncestorGroup().getName(),
                meeting.getMembers().contains(user),
                meeting.getName(),
                meeting.getEventStartDateTime().toEpochMilli(),
                mtgLocation.getLatitude(),
                mtgLocation.getLongitude());
    }

    private AroundMeDTO convertLiveWireAlert(LiveWireAlert alert, User user) {
        final String ancestorName = LiveWireAlertType.INSTANT.equals(alert.getType()) ?
                alert.getGroup().getName() : alert.getMeeting().getAncestorGroup().getName();
        return new AroundMeDTO(alert.getUid(),
                GeoLocatedEntityType.LIVE_WIRE_ALERT,
                alert.getHeadline(),
                alert.getDescription(),
                ancestorName,
                alert.getCreatingUser().equals(user),
                alert.getContactName(),
                alert.getCreationTime().toEpochMilli(),
                alert.getLocation().getLatitude(),
                alert.getLocation().getLongitude());
    }

    // do similar with a method that looks for only public groups, or only public meetings

    // and have a method that sends back both the public entities, and the entities private to the user (e.g.,
    // meetings of groups they are part of, where meeting or group is within the radius)

}
