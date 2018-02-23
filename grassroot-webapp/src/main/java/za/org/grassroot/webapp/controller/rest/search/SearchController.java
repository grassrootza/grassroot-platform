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
    @RequestMapping(value = "/groups/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User groups using search term")
    public ResponseEntity<List<GroupFullDTO>> searchForUserGroupsByTerm(@PathVariable String userUid,
                                                                        @PathVariable String searchTerm,
                                                                        HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            List<Group> groups = groupQueryBroker.searchUsersGroups(userUid,searchTerm,false);

            List<GroupFullDTO> dtos = new ArrayList<>();

            groups.forEach(group -> dtos.add(groupFetchBroker.fetchGroupFullInfo(group.getCreatedByUser().getUid(),group.getUid(),
                    false,false,false)));

            logger.info("Groups full..................",dtos);
            return ResponseEntity.ok(dtos);
        }else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }

    @Timed
    @RequestMapping(value = "/publicGroups/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User public groups using search term")
    public ResponseEntity<List<GroupFullDTO>> userPublicGroups(@PathVariable String userUid,
                                                               @PathVariable String searchTerm,
                                                               @RequestParam(value = "searchByLocation", required = false) boolean searchByLocation,
                                                               HttpServletRequest request){

        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            User user = getUserFromRequest(request);
            GroupLocationFilter filter = null;
            if (searchByLocation) {
                PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(user.getUid(), LocalDate.now());
                logger.info("user location : " + lastUserLocation);
                filter = lastUserLocation != null ? new GroupLocationFilter(lastUserLocation.getLocation(), 10, false) : null;
            }

            List<Group> groups = groupQueryBroker.findPublicGroups(userUid,searchTerm,filter,true);
            List<GroupFullDTO> groupFullDTOS = new ArrayList<>();
            if(groups != null){
                groups.forEach(group -> groupFullDTOS.add(groupFetchBroker.fetchGroupFullInfo(group.getCreatedByUser().getUid(),group.getUid(),false,false,false)));
            }
            logger.info("Groups found..........",groupFullDTOS);
            return ResponseEntity.ok(groupFullDTOS);
        }else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }

    @Timed
    @RequestMapping(value = "/publicMeetings/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User public meetings using search term")
    public ResponseEntity<List<TaskFullDTO>> userPublicMeetings(@PathVariable String userUid,
                                                               @PathVariable String searchTerm,
                                                               HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            User user = getUserFromRequest(request);
            List<Meeting> meetings = eventBroker.publicMeetingsUserIsNotPartOf(searchTerm,user);
            List<TaskFullDTO> taskFullDTOS = new ArrayList<>();

            meetings.forEach(meeting -> taskFullDTOS.add(new TaskFullDTO(meeting,user,meeting.getDeadlineTime(),null)));
            return ResponseEntity.ok(taskFullDTOS);
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
