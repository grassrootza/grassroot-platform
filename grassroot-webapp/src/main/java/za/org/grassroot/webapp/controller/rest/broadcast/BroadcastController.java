package za.org.grassroot.webapp.controller.rest.broadcast;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.broadcasts.BroadcastBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/broadcast") @Slf4j
@RequestMapping(value = "/api/broadcast")
public class BroadcastController extends BaseRestController {

    private final BroadcastBroker broadcastBroker;

    public BroadcastController(JwtService jwtService, UserManagementService userManagementService, BroadcastBroker broadcastBroker) {
        super(jwtService, userManagementService);
        this.broadcastBroker = broadcastBroker;
    }

    @RequestMapping(value = "/list/group/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts attached to a group", notes = "Can optionally including those from its associated campaigns")
    public ResponseEntity<List<BroadcastDTO>> fetchGroupBroadcasts(@PathVariable String groupUid,
                                                                   @RequestParam boolean includingCampaigns) {
        return ResponseEntity.ok(
                broadcastBroker.fetchGroupBroadcasts(groupUid).stream().map(BroadcastDTO::new)
                        .collect(Collectors.toList()));
    }

    @RequestMapping(value = "/list/campaign/{campaignUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch the broadcasts from a campaign")
    public ResponseEntity<List<BroadcastDTO>> fetchCampaignBroadcasts(@PathVariable String campaignUid) {
        return ResponseEntity.ok(
                broadcastBroker.fetchCampaignBroadcasts(campaignUid).stream().map(BroadcastDTO::new)
                        .collect(Collectors.toList()));
    }

    @RequestMapping(value = "/create/group/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast on the group", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createGroupBroadcast(@PathVariable String groupUid,
                                                             @RequestBody BroadcastCreateRequest createRequest) {
        return null;
    }

    @RequestMapping(value = "/create/campaign/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a broadcast for a campaign", notes = "NB : this will be a heavy operation, so do it async")
    public ResponseEntity<BroadcastDTO> createCampaignBroadcast(@PathVariable String campaignUid,
                                                                @RequestBody BroadcastCreateRequest createRequest) {
        return null;
    }


}
