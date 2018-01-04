package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.user.UserProfileStatus;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.group.GroupJoinInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@RestController @Grassroot2RestController
@Api("/api/group/outside/join/") @Slf4j
@RequestMapping(value = "/api/group/outside/join/")
public class IncomingGroupJoinController extends BaseRestController {

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;

    public IncomingGroupJoinController(JwtService jwtService, UserManagementService userManagementService, GroupBroker groupBroker) {
        super(jwtService, userManagementService);
        this.groupBroker = groupBroker;
        this.userManager = userManagementService;
    }

    @RequestMapping(value = "/start/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Initiate use of a join code (word, or URL)")
    public ResponseEntity<GroupJoinInfo> initiateUseOfJoinWord(HttpServletRequest request,
                                                               @PathVariable String groupUid,
                                                               @RequestParam String code) {
        Group group = groupBroker.loadAndRecordUse(groupUid, code);
        GroupJoinInfo.GroupJoinInfoBuilder builder = GroupJoinInfo.builder()
                .groupName(group.getName())
                .groupUid(group.getUid())
                .groupDescription(group.getDescription())
                .groupTopics(group.getTopics());

        String userUid = getUserIdFromRequest(request);
        if (!StringUtils.isEmpty(userUid)) {
            log.info("joining with an existing, and logged in user!");
            User user = getUserFromRequest(request);
            builder = builder.userLoggedIn(false)
                    .userAlreadyMember(group.hasMember(user));
        } else {
            builder.userLoggedIn(false);
        }

        return ResponseEntity.ok(builder.build());
    }

    @RequestMapping(value = "/complete/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Complete the use of the join code", notes = "Returns a user status depending on whether " +
            "they have an active web profile or not")
    public ResponseEntity<UserProfileStatus> completeUseOfJoinWord(HttpServletRequest request,
                                                                   @PathVariable String groupUid,
                                                                   @RequestParam String code,
                                                                   @RequestParam(required = false) String name,
                                                                   @RequestParam(required = false) String phone,
                                                                   @RequestParam(required = false) String email,
                                                                   @RequestParam(required = false) Province province,
                                                                   @RequestParam(required = false) String[] topics) {
        String joinedUserUid = groupBroker.addMemberViaJoinCode(groupUid, code, getUserIdFromRequest(request),
                name, phone, email, province, Arrays.asList(topics));
        return ResponseEntity.ok(userManager.fetchUserProfileStatus(joinedUserUid));
    }

}
