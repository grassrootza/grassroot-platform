package za.org.grassroot.webapp.controller.rest.search;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.PublicGroupDTO;
import za.org.grassroot.core.dto.task.PublicMeetingDTO;
import za.org.grassroot.core.dto.task.TaskFullDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.*;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping(value = "/v2/api/search") @Api("/v2/api/search")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class SearchController extends BaseRestController {

    private static final int MAX_JOIN_CODE_ATTEMPTS = 5;

    private final TaskBroker taskBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final GroupFetchBroker groupFetchBroker;
    private final EventBroker eventBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final GroupJoinRequestService groupJoinRequestService;
    private final GroupBroker groupBroker;
    private final CacheUtilService cacheUtilService;
    private int attemptsCounter = 0;

    @Autowired
    public SearchController(TaskBroker taskBroker,
                            GroupQueryBroker groupQueryBroker,
                            GroupFetchBroker groupFetchBroker,
                            EventBroker eventBroker,
                            GeoLocationBroker geoLocationBroker,
                            GroupBroker groupBroker,
                            GroupJoinRequestService groupJoinRequestService,
                            JwtService jwtService,
                            CacheUtilService cacheUtilService,
                            UserManagementService userManagementService){
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.groupFetchBroker = groupFetchBroker;
        this.eventBroker = eventBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.groupBroker = groupBroker;
        this.cacheUtilService = cacheUtilService;
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
        log.debug("group names: {}", groups.stream().map(Group::getName).collect(Collectors.joining(", ")));
        List<GroupFullDTO> dtos = groups.stream().map(group -> groupFetchBroker.fetchGroupFullInfo(userUid, group.getUid(),
                false,false,false)).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @RequestMapping(value = "/groups/public", method = RequestMethod.GET)
    @ApiOperation(value = "User public groups using search term")
    public ResponseEntity<List<PublicGroupDTO>> userPublicGroups(@RequestParam String searchTerm,
                                                                 @RequestParam(value = "useLocation", required = false) boolean useLocation,
                                                                 HttpServletRequest request) {
        GroupLocationFilter filter = null;
        if (useLocation) {
            PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(getUserIdFromRequest(request), LocalDate.now());
            log.info("user location : " + lastUserLocation);
            filter = lastUserLocation != null ? new GroupLocationFilter(lastUserLocation.getLocation(), 10, false) : null;
        }

        List<PublicGroupDTO> groupDTOs = groupQueryBroker.findPublicGroups(getUserIdFromRequest(request), searchTerm, filter, true)
                .stream().map(PublicGroupDTO::new).collect(Collectors.toList());
        log.info("Groups found..........",groupDTOs);
        return ResponseEntity.ok(groupDTOs);
    }

    // todo: come back and introduce a location filter to this later
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
                                                              @RequestParam String message,
                                                              HttpServletRequest request){
        final String joinRequestUid = groupJoinRequestService.open(getUserIdFromRequest(request), groupUid, message);
        return ResponseEntity.ok(new GroupJoinRequestDTO(groupJoinRequestService.loadRequest(joinRequestUid), getUserFromRequest(request)));
    }

    @RequestMapping(value = "/group/check", method = RequestMethod.GET)
    @ApiOperation(value = "Checks for a group by join code", notes = "Searches for a group by the given join code, and " +
            "returns it if it exists, otherwise returns nothing. This is rate limited to 5 attempts per half hour")
    public ResponseEntity findGroup(@RequestParam String joinCode,
                                                 HttpServletRequest request) {
        int recentUserAttemptCount = cacheUtilService.fetchJoinAttempts(getUserIdFromRequest(request));
        if (recentUserAttemptCount > MAX_JOIN_CODE_ATTEMPTS) {
            return RestUtil.errorResponse(RestMessage.EXCEEDED_MAX_ATTEMPTS);
        }

        cacheUtilService.putJoinAttempt(getUserIdFromRequest(request), recentUserAttemptCount + 1);

        Optional<GroupRefDTO> result = groupQueryBroker.findGroupFromJoinCode(joinCode).map(group ->
            new GroupRefDTO(group.getUid(), group.getName(), group.getMemberships().size()));

        return result.isPresent() ? ResponseEntity.ok(result.get()) : ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/group/join", method = RequestMethod.POST)
    @ApiOperation(value = "Adds a member to a group using join code")
    public ResponseEntity<GroupFullDTO> addMemberWithJoinCode(@RequestParam String joinCode,
                                                                 @RequestParam String groupUid,
                                                                 HttpServletRequest request){
        groupBroker.addMemberViaJoinCode(getUserIdFromRequest(request),groupUid,joinCode, UserInterfaceType.WEB_2);
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(getUserIdFromRequest(request),
                groupUid, false, false, false));
    }

}