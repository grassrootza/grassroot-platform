package za.org.grassroot.webapp.controller.rest.search;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.PublicGroupDTO;
import za.org.grassroot.core.dto.task.PublicMeetingDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.dto.task.TaskRefDTO;
import za.org.grassroot.core.enums.TaskType;
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
import za.org.grassroot.webapp.controller.rest.task.TaskFetchController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api("/api/search")
@RequestMapping(value = "/api/search")
public class SearchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(TaskFetchController.class);

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

    @Timed
    @RequestMapping(value = "/userTasks/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User tasks using search term")
    public ResponseEntity<List<TaskFullDTO>> searchForUserTasksByTerm(@PathVariable String userUid,
                                                                  @PathVariable String searchTerm,
                                                                  HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            List<TaskFullDTO> tasks = taskBroker.searchForTasks(userUid,searchTerm);
            logger.info("User tasks using search term{},tasks{}",searchTerm,tasks);
            return ResponseEntity.ok(tasks);
        }else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }


    @Timed
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
            logger.info("user location : " + lastUserLocation);
            filter = lastUserLocation != null ? new GroupLocationFilter(lastUserLocation.getLocation(), 10, false) : null;
        }

        List<PublicGroupDTO> groupDTOs = groupQueryBroker.findPublicGroups(getUserIdFromRequest(request), searchTerm, filter, true)
                .stream().map(PublicGroupDTO::new).collect(Collectors.toList());
        logger.info("Groups found..........",groupDTOs);
        return ResponseEntity.ok(groupDTOs);
    }

    // switch this to same pattern as I have done for groups
    @RequestMapping(value = "/publicMeetings/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User public meetings using search term")
    public ResponseEntity<List<PublicMeetingDTO>> userPublicMeetings(@PathVariable String userUid,
                                                                     @PathVariable String searchTerm,
                                                                     HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            User user = getUserFromRequest(request);
            List<PublicMeetingDTO> meetingDTOS = eventBroker.publicMeetingsUserIsNotPartOf(searchTerm,user)
                    .stream().map(PublicMeetingDTO::new).collect(Collectors.toList());

            return ResponseEntity.ok(meetingDTOS);
        }
        else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }

    @Timed
    @RequestMapping(value = "/join/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Sends a join requests and get a join uid")
    public ResponseEntity<ResponseWrapper> askToJoinGroup(@PathVariable String groupUid,
                                                          @RequestParam String requestorUid,
                                                          @RequestParam String joinWord,
                                                          HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(requestorUid.equals(loggedInUserUid)){
            final String joinRequestUid = groupJoinRequestService.open(requestorUid,groupUid,joinWord);
            final GroupJoinRequestDTO returnedRequest =
                    new GroupJoinRequestDTO(groupJoinRequestService.loadRequest(joinRequestUid), getUserFromRequest(request));
            ResponseEntity<ResponseWrapper> response =
                    RestUtil.okayResponseWithData(RestMessage.GROUP_JOIN_REQUEST_SENT, Collections.singletonList(returnedRequest));
            return response;
        }else{
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
    }


}
