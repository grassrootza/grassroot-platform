package za.org.grassroot.webapp.controller.rest.broadcast;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;
import za.org.grassroot.services.broadcasts.BroadcastBroker;
import za.org.grassroot.services.broadcasts.BroadcastComponents;
import za.org.grassroot.services.broadcasts.BroadcastInfo;
import za.org.grassroot.services.broadcasts.EmailBroadcast;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController @Grassroot2RestController
@Api("/api/broadcast") @Slf4j
@RequestMapping(value = "/api/broadcast")
public class BroadcastController extends BaseRestController {

    private final BroadcastBroker broadcastBroker;

    public BroadcastController(JwtService jwtService, UserManagementService userManagementService, BroadcastBroker broadcastBroker) {
        super(jwtService, userManagementService);
        this.broadcastBroker = broadcastBroker;
    }

    @RequestMapping(value = "/fetch/group/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts attached to a group")
    public ResponseEntity<List<BroadcastDTO>> fetchGroupBroadcasts(@PathVariable String groupUid) {
        return ResponseEntity.ok(broadcastBroker.fetchGroupBroadcasts(groupUid));
    }

    @RequestMapping(value = "/fetch/campaign/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts from a campaign")
    public ResponseEntity<List<BroadcastDTO>> fetchCampaignBroadcasts(@PathVariable String campaignUid) {
        return ResponseEntity.ok(broadcastBroker.fetchCampaignBroadcasts(campaignUid));
    }

    @RequestMapping(value = "/create/group/info/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch some basic information on the group, including social media options")
    public ResponseEntity<BroadcastInfo> getGroupBroadcastParams(HttpServletRequest request, @PathVariable String groupUid,
                                                                 @RequestParam(required = false) String userUid) {
        return ResponseEntity.ok(broadcastBroker.fetchGroupBroadcastParams(
                userUid == null ? getUserIdFromRequest(request) : userUid, groupUid));
    }

    // todo : this should definitely be async, it's a very heavy operation
    @RequestMapping(value = "/create/group/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast on the group", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createGroupBroadcast(HttpServletRequest request,
                                                             @PathVariable String groupUid,
                                                             @RequestBody BroadcastCreateRequest createRequest) {
        log.info("creating broadcast! request looks like: {}", createRequest);

        String userUid = getUserIdFromRequest(request);

        BroadcastComponents bc = BroadcastComponents.builder()
                .title(createRequest.getTitle())
                .userUid(userUid)
                .groupUid(groupUid)
                .campaignBroadcast(false)
                .broadcastSchedule(createRequest.getSendType())
                .scheduledSendTime(createRequest.getSendDateTime())
                .provinces(createRequest.getProvinces())
                .topics(createRequest.getTopics())
                .build();

        fillInContent(createRequest, bc);

        String broadcastUid = broadcastBroker.sendGroupBroadcast(bc);

        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(broadcastUid));
    }

    @RequestMapping(value = "/create/campaign/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast for a campaign", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createCampaignBroadcast(HttpServletRequest request, @PathVariable String campaignUid,
                                                                @RequestBody BroadcastCreateRequest createRequest) {

        BroadcastComponents bc = BroadcastComponents.builder()
                .title(createRequest.getTitle())
                .userUid(getUserIdFromRequest(request))
                .campaignUid(campaignUid)
                .campaignBroadcast(true)
                .broadcastSchedule(createRequest.getSendType())
                .scheduledSendTime(createRequest.getSendDateTime())
                .provinces(createRequest.getProvinces())
                .topics(createRequest.getTopics())
                .build();

        fillInContent(createRequest, bc);
        String broadcastUid = broadcastBroker.sendGroupBroadcast(bc);

        return ResponseEntity.ok(broadcastBroker.fetchBroadcast(broadcastUid));
    }

    private void fillInContent(BroadcastCreateRequest createRequest, BroadcastComponents bc) {
        if (createRequest.isSendShortMessages()) {
            bc.setShortMessage(createRequest.getShortMessageString());
        }

        if (createRequest.isSendEmail()) {
            bc.setEmail(generateEmail(createRequest));
        }

        if (createRequest.isPostToFacebook()) {
            bc.setFacebookPost(generateFbPost(bc.getUserUid(), createRequest));
        }

        if (createRequest.isPostToTwitter()) {
            bc.setTwitterPostBuilder(generateTweet(bc.getUserUid(), createRequest));
        }

    }

    private EmailBroadcast generateEmail(BroadcastCreateRequest request) {
        return EmailBroadcast.builder()
                .subject(request.getTitle())
                .content(request.getEmailContent())
                .deliveryRoute(DeliveryRoute.EMAIL_USERACCOUNT) // for the moment, default
                .build();
    }

    private FBPostBuilder generateFbPost(String userUid, BroadcastCreateRequest request) {
        log.info("incoming FB page Id = {}", request.getFacebookPage());
        return FBPostBuilder.builder()
                .postingUserUid(userUid)
                .facebookPageId(request.getFacebookPage())
                .message(request.getFacebookContent())
                .linkUrl(request.getFacebookLink())
                .build();
    }

    private TwitterPostBuilder generateTweet(String userUid, BroadcastCreateRequest request) {
        return TwitterPostBuilder.builder()
                .postingUserUid(userUid)
                .message(request.getTwitterContent())
                .imageKey(request.getTwitterLink())
                .build();
    }


}
