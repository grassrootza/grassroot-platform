package za.org.grassroot.webapp.controller.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api("/api/request/fetch")
@RequestMapping(value = "/api/request/fetch")
public class RequestFetchController {

    private static final Logger logger = LoggerFactory.getLogger(RequestFetchController.class);

    private final UserManagementService userManagementService;
    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public RequestFetchController(UserManagementService userManagementService, GroupJoinRequestService groupJoinRequestService) {
        this.userManagementService = userManagementService;
        this.groupJoinRequestService = groupJoinRequestService;
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Requests to join group, which this user can authorize",
            notes = "Fetch requests to join the group, which the user has permission to respond to",
            response = GroupJoinRequestDTO.class)
    public ResponseEntity<List<GroupJoinRequestDTO>> fetchGroupJoinRequests(@PathVariable String userUid,
                                                                            @PathVariable String groupUid) {
        final User user = userManagementService.load(userUid);
        return ResponseEntity.ok(groupJoinRequestService
                .getPendingRequestsForGroup(userUid, groupUid)
                .stream()
                .map(jr -> new GroupJoinRequestDTO(jr, user))
                .collect(Collectors.toList()));
    }


}
