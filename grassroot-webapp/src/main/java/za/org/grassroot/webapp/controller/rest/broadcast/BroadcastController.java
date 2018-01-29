package za.org.grassroot.webapp.controller.rest.broadcast;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.integration.MediaFileBroker;
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
import java.util.ArrayList;
import java.util.List;

@RestController @Grassroot2RestController
@Api("/api/broadcast") @Slf4j
@RequestMapping(value = "/api/broadcast")
public class BroadcastController extends BaseRestController {

    private final BroadcastBroker broadcastBroker;
    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public BroadcastController(JwtService jwtService, UserManagementService userManagementService, BroadcastBroker broadcastBroker, MediaFileBroker mediaFileBroker) {
        super(jwtService, userManagementService);
        this.broadcastBroker = broadcastBroker;
        this.mediaFileBroker = mediaFileBroker;
    }

    @RequestMapping(value = "/fetch/group/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts attached to a group")
    public ResponseEntity<Page<BroadcastDTO>> fetchGroupSentBroadcasts(@PathVariable String groupUid,
                                                                       @RequestParam BroadcastSchedule broadcastSchedule,
                                                                       Pageable pageable) {
        Page<BroadcastDTO> broadcastDTOPage = new PageImpl<>(new ArrayList<>());
        if(broadcastSchedule.equals(BroadcastSchedule.IMMEDIATE)){
            broadcastDTOPage = broadcastBroker.fetchSentGroupBroadcasts(groupUid, pageable);
        }else if(broadcastSchedule.equals(BroadcastSchedule.FUTURE))
            broadcastDTOPage = broadcastBroker.fetchScheduledGroupBroadcasts(groupUid, pageable);

        return ResponseEntity.ok(broadcastDTOPage);
    }


    @RequestMapping(value = "/fetch/campaign/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts from a campaign")
    public ResponseEntity<List<BroadcastDTO>> fetchCampaignBroadcasts(@PathVariable String campaignUid) {
        return ResponseEntity.ok(broadcastBroker.fetchCampaignBroadcasts(campaignUid));
    }

    @RequestMapping(value = "/create/group/info/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch some basic information on the group, including social media options")
    public ResponseEntity<BroadcastInfo> getGroupBroadcastParams(HttpServletRequest request, @PathVariable String groupUid) {
        BroadcastInfo info = broadcastBroker.fetchGroupBroadcastParams(getUserIdFromRequest(request), groupUid);
        log.info("returning info: {}", info);
        return ResponseEntity.ok(info);
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

    @RequestMapping(value = "/create/image/upload", method = RequestMethod.POST)
    @ApiOperation(value = "Upload an image for a broadcast", notes = "Will pass back an image key")
    public ResponseEntity uploadImage(HttpServletRequest request, @RequestBody MultipartFile image) {
        // todo : rate limiting?
        log.info("do we have a file? : ", image);
        String imageKey = mediaFileBroker.storeFile(image, MediaFunction.BROADCAST_IMAGE, image.getContentType(), null);
        return ResponseEntity.ok(imageKey);
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
