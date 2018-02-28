package za.org.grassroot.webapp.controller.rest.search;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.PublicGroupDTO;
import za.org.grassroot.core.dto.task.PublicMeetingDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api("/api/search") @Slf4j
@RequestMapping(value = "/api/search")
public class SearchController extends BaseRestController {

    private final TaskBroker taskBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final GroupFetchBroker groupFetchBroker;
    private final EventBroker eventBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public SearchController(TaskBroker taskBroker,
                            GroupQueryBroker groupQueryBroker,
                            GroupFetchBroker groupFetchBroker,
                            EventBroker eventBroker,
                            GeoLocationBroker geoLocationBroker,
                            GroupJoinRequestService groupJoinRequestService,
                            JwtService jwtService,
                            UserManagementService userManagementService){
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.groupFetchBroker = groupFetchBroker;
        this.eventBroker = eventBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.groupJoinRequestService = groupJoinRequestService;
    }

    @RequestMapping(value = "/tasks/user", method = RequestMethod.GET)
    @ApiOperation(value = "User tasks using search term")
    public ResponseEntity<List<TaskFullDTO>> searchForUserTasksByTerm(@RequestParam String searchTerm,
                                                                      HttpServletRequest request){
        List<TaskFullDTO> tasks = taskBroker.searchForTasks(getUserIdFromRequest(request),searchTerm);
        log.info("User tasks using search term {}, tasks{}",searchTerm,tasks);
        return ResponseEntity.ok(tasks);
    }


    @RequestMapping(value = "/groups/private", method = RequestMethod.GET)
    @ApiOperation(value = "User groups using search term")
    public ResponseEntity<List<GroupFullDTO>> searchForUserGroupsByTerm(@RequestParam String searchTerm,
                                                                        HttpServletRequest request){
        List<Group> groups = groupQueryBroker.searchUsersGroups(getUserIdFromRequest(request),searchTerm,false);
        List<GroupFullDTO> dtos = groups.stream().map(group -> groupFetchBroker.fetchGroupFullInfo(group.getCreatedByUser().getUid(),group.getUid(),
                false,false,false)).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @RequestMapping(value = "/groups/public", method = RequestMethod.GET)
    @ApiOperation(value = "User public groups using search term")
    public ResponseEntity<List<PublicGroupDTO>> userPublicGroups(@RequestParam String searchTerm,
                                                                 @RequestParam(value = "searchByLocation", required = false) boolean searchByLocation,
                                                                 HttpServletRequest request){
        GroupLocationFilter filter = null;
        if (searchByLocation) {
            PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(getUserIdFromRequest(request), LocalDate.now());
            log.info("user location : " + lastUserLocation);
            filter = lastUserLocation != null ? new GroupLocationFilter(lastUserLocation.getLocation(), 10, false) : null;
        }

        List<PublicGroupDTO> groupDTOs = groupQueryBroker.findPublicGroups(getUserIdFromRequest(request), searchTerm, filter, true)
                .stream().map(PublicGroupDTO::new).collect(Collectors.toList());
        log.info("Groups found..........",groupDTOs);
        return ResponseEntity.ok(groupDTOs);
    }

    // switch this to same pattern as I have done for groups
    @RequestMapping(value = "/meetings/public", method = RequestMethod.GET)
    @ApiOperation(value = "User public meetings using search term")
    public ResponseEntity<List<PublicMeetingDTO>> userPublicMeetings(@RequestParam String searchTerm,
                                                                     HttpServletRequest request){
        List<PublicMeetingDTO> meetingDTOS = eventBroker.publicMeetingsUserIsNotPartOf(searchTerm, getUserFromRequest(request))
                .stream().map(PublicMeetingDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(meetingDTOS);
    }

    @RequestMapping(value = "/join/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Sends a join requests and get a join uid")
    public ResponseEntity<GroupJoinRequestDTO> askToJoinGroup(@PathVariable String groupUid,
                                                          @RequestParam String joinWord,
                                                          HttpServletRequest request){
        final String joinRequestUid = groupJoinRequestService.open(getUserIdFromRequest(request), groupUid, joinWord);
        return ResponseEntity.ok(new GroupJoinRequestDTO(groupJoinRequestService.loadRequest(joinRequestUid), getUserFromRequest(request)));
    }


}
