package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.UserFullDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.group.join.JoinInfoExternal;
import za.org.grassroot.webapp.controller.rest.group.join.JoinSubmitInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController @Grassroot2RestController
@Api("/v2/api/group/outside/join/") @Slf4j
@RequestMapping(value = "/v2/api/group/outside/join/")
public class IncomingGroupJoinController extends BaseRestController {

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;
    private final PasswordTokenService tokenService;
    private final CampaignBroker campaignBroker;

    public IncomingGroupJoinController(JwtService jwtService, UserManagementService userManagementService, GroupBroker groupBroker, PasswordTokenService tokenService, CampaignBroker campaignBroker) {
        super(jwtService, userManagementService);
        this.groupBroker = groupBroker;
        this.userManager = userManagementService;
        this.tokenService = tokenService;
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = "/start/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Initiate use of a join code (word, or URL)")
    public ResponseEntity<JoinInfoExternal> initiateUseOfJoinWord(HttpServletRequest request,
                                                                  @PathVariable String groupUid,
                                                                  @RequestParam(required = false) String code,
                                                                  @RequestParam(required = false) String broadcastId) {
        Group group = groupBroker.loadAndRecordUse(groupUid, code, broadcastId);

        JoinInfoExternal.JoinInfoExternalBuilder builder = JoinInfoExternal.builder()
                .groupName(group.getName())
                .groupUid(group.getUid())
                .groupDescription(group.getDescription())
                .groupImageUrl(group.getImageUrl())
                .groupJoinTopics(group.getJoinTopics())
                .hasActiveCampaign(campaignBroker.doesGroupHaveActiveCampaign(group.getUid()));

        String userUid = getUserIdFromRequest(request);
        if (!StringUtils.isEmpty(userUid)) {
            log.info("joining with an existing, and logged in user!");
            User user = getUserFromRequest(request);
            builder = builder.userLoggedIn(false).userAlreadyMember(user.isMemberOf(group));
        } else {
            builder.userLoggedIn(false);
        }

        return ResponseEntity.ok(builder.build());
    }

    @RequestMapping(value = "/complete/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Complete the use of the join code", notes = "Returns a DTO with the details of the user, either " +
            "as created by this join or already existing")
    public ResponseEntity completeUseOfJoinWord(HttpServletRequest request,
                                                                 @PathVariable String groupUid,
                                                                 @RequestParam(required = false) String code,
                                                                 @RequestParam(required = false) String broadcastId,
                                                                 @RequestParam(required = false) List<String> joinTopics,
                                                                 @RequestBody(required = false) JoinSubmitInfo joinSubmitInfo) {
        String userUid = getUserIdFromRequest(request);
        if (userUid == null && joinSubmitInfo == null) {
            log.error("Error! Join request called without logged in user and without join info");
            return ResponseEntity.badRequest().build();
        }

        String joinedUserUid;
        if (joinSubmitInfo != null) {
            joinedUserUid = groupBroker.addMemberViaJoinPage(groupUid, code, broadcastId, getUserIdFromRequest(request),
                joinSubmitInfo.getName(), joinSubmitInfo.getPhone(), joinSubmitInfo.getEmail(),
                joinSubmitInfo.safeProvince(), joinSubmitInfo.getLanguage(), joinSubmitInfo.getTopics(), UserInterfaceType.WEB_2);
        } else {
            groupBroker.addMemberViaJoinPage(groupUid, code, broadcastId, userUid, null, null, null, null, null,
                    joinTopics, UserInterfaceType.WEB_2);
            joinedUserUid = userUid;
        }
        User user = userManager.load(joinedUserUid);
        // since the next call has to be unprotected, we need a way to prevent spoofing & strafing
        final String tokenForSubequent = tokenService.generateShortLivedOTP(user.getUsername()).getCode();
        final UserFullDTO userFullDTO = new UserFullDTO(user);
        return ResponseEntity.ok(new JoinResponse(tokenForSubequent, userFullDTO));
    }

    @RequestMapping(value = "/topics/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Set the join topics for the user who just joined (pass back generated code to validate it is same user")
    public ResponseEntity setJoinTopics(HttpServletRequest request, @PathVariable String groupUid,
                                                      @RequestParam String joinedUserUid,
                                                      @RequestParam String validationCode,
                                                      @RequestParam List<String> joinTopics) {
        String requestUserUid = getUserIdFromRequest(request);
        if (!StringUtils.isEmpty(requestUserUid) && requestUserUid.equals(joinedUserUid)) {
            throw new AccessDeniedException("Spoofing attempt, inserting another user UID");
        }

        User user = userManager.load(joinedUserUid);
        tokenService.validateOtp(user.getUsername(), validationCode);

        log.debug("okay, all validated, setting topics = {}", joinTopics);
        groupBroker.setMemberJoinTopics(joinedUserUid, groupUid, joinedUserUid, joinTopics);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/auto/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Endpoint for automated join, e.g., as posted from a sign up form on an alternate website")
    public ResponseEntity addToGroupAuto(@PathVariable String groupUid, @RequestParam String firstname, @RequestParam String surname, @RequestParam String email,
                                         @RequestParam String joinCode, @RequestParam String accessToken) {
        final String userId = getUserIdFromToken(accessToken);
        if (StringUtils.isEmpty(userId))
            throw new AccessDeniedException("Token lacks valid user ID");
        // we still check this explicitly because even if user has permission (which group broker will check), have to
        // check that the specific token that's being used has the add member permission
        if (!getPermissionsFromToken(accessToken).contains(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER.name()))
            throw new AccessDeniedException("Token does not authorize needed permission");

        groupBroker.addMemberViaJoinPage(groupUid, joinCode, null, null, firstname + " " + surname, null, email,
                null, null, null, UserInterfaceType.OTHER_WEB);
        return ResponseEntity.ok().build();
    }

}
