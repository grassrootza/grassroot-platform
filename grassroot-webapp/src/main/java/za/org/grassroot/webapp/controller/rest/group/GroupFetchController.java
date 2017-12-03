package za.org.grassroot.webapp.controller.rest.group;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/group/fetch")
@RequestMapping(value = "/api/group/fetch")
public class GroupFetchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(GroupFetchController.class);

    private final GroupFetchBroker groupFetchBroker;


    public GroupFetchController(GroupFetchBroker groupFetchBroker, JwtService jwtService, UserManagementService userManagementService) {
        super(jwtService, userManagementService);
        this.groupFetchBroker = groupFetchBroker;
    }


    /**
     * Returns a list of groups for currently logged in user
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/list")
    public ResponseEntity<List<GroupMinimalDTO>> listUserGroups(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        List<GroupMinimalDTO> groups = groupFetchBroker.fetchAllUserGroupsSortByLatestTime(userId);
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }



    /**
     * Tells the client which, if any, of the groups have had a structural change since the last time the client knew about
     * Note: does not include / check for a more recent task time (on grounds if client wants to / needs to know that, there
     * are other methods and/or it can store or fetch the tasks itself)
     * @param userUid The UID of the user calling this group
     * @param existingGroups The groups that already exist on the client, with the epochMilli time they were last updated
     * @return
     */
    @Timed
    @RequestMapping(value = "/updated/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<Set<GroupTimeChangedDTO>> fetchUpdatedGroups(@PathVariable String userUid,
                                                                       @RequestBody Map<String, Long> existingGroups) {
        logger.info("checking for groups changes, map sent in: {}", existingGroups);
        return ResponseEntity.ok(groupFetchBroker.findNewlyChangedGroups(userUid, existingGroups));
    }

    /**
     * Sends back a minimal set of information about the groups: names, size, last activity times
     * @param userUid
     * @param groupUids
     * @return
     */
    @RequestMapping(value = "/info/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<Set<GroupMinimalDTO>> fetchGroupInfo(@PathVariable String userUid,
                                                               @RequestParam(required = false) Set<String> groupUids) {
        return ResponseEntity.ok(groupFetchBroker.fetchGroupMinimalInfo(userUid, groupUids));
    }

    /**
     * Full, heavy group retrieval--gets everything a client likely needs
     * @param userUid
     * @param groupUids
     * @return
     */
    @RequestMapping(value = "/full/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<Set<GroupFullDTO>> fetchFullGroupInfo(@PathVariable String userUid,
                                                                @RequestParam(required = false) Set<String> groupUids) {
        final String descriptionTemplate = "Group '%1$s', created on %2$s, has %3$d members, with join code %4$s";
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(userUid, groupUids).stream()
                .map(g -> g.insertDefaultDescriptionIfEmpty(descriptionTemplate)).collect(Collectors.toSet()));
    }


}
