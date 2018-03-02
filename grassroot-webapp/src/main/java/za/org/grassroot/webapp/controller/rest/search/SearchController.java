package za.org.grassroot.webapp.controller.rest.search;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.PublicGroupDTO;
import za.org.grassroot.core.dto.task.PublicMeetingDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.*;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    private final GroupBroker groupBroker;

    @Autowired
    public SearchController(TaskBroker taskBroker,
                            GroupQueryBroker groupQueryBroker,
                            GroupFetchBroker groupFetchBroker,
                            EventBroker eventBroker,
                            GeoLocationBroker geoLocationBroker,
                            GroupBroker groupBroker,
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
        this.groupBroker = groupBroker;
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
        final String userUid = getUserIdFromRequest(request);
        List<Group> groups = groupQueryBroker.searchUsersGroups(userUid,searchTerm,false);
        log.info("group names: {}", groups.stream().map(Group::getName).collect(Collectors.joining(", ")));
        List<GroupFullDTO> dtos = groups.stream().map(group -> groupFetchBroker.fetchGroupFullInfo(userUid, group.getUid(),
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

    @RequestMapping(value = "/group/join", method = RequestMethod.POST)
    @ApiOperation(value = "Adds a member to a group using join code")
    public ResponseEntity<ResponseWrapper> addMemberWithJoinCode(@RequestParam String joinCode,
                                                                 @RequestParam String groupUid,
                                                                 HttpServletRequest request){
        try{
            groupBroker.addMemberViaJoinCode(getUserIdFromRequest(request),groupUid,joinCode, UserInterfaceType.WEB);
            return new ResponseEntity<ResponseWrapper>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.MEMBERS_ADDED, RestStatus.SUCCESS),HttpStatus.OK);
        }catch (Exception e){
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.MEMBER_ALREADY_SET);
        }
    }


    @RequestMapping(value = "/group",method = RequestMethod.GET)
    @ApiOperation(value = "Searches for a group and returns ref DTO")
    public ResponseEntity<GroupRefDTO> findGroup(@RequestParam String joinCode){
        ResponseEntity<GroupRefDTO> dtoResponseEntity;
        Optional<Group> groupByToken = groupQueryBroker.findGroupFromJoinCode(joinCode);
        if(groupByToken.isPresent()){
            GroupRefDTO groupRefDTO = new GroupRefDTO(groupByToken.get().getUid(),
                    groupByToken.get().getGroupName(),groupByToken.get().getMembers().size());
            log.info("Group ref.............",groupRefDTO);
            return ResponseEntity.ok(groupRefDTO);
        }else{
            return null;
        }
    }
}
