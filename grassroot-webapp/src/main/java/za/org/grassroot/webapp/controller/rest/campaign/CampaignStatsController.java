package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.Map;

@RestController
@Grassroot2RestController
@Api("/api/campaign/stats")
@RequestMapping(value = "/api/campaign/stats")
public class CampaignStatsController extends BaseRestController {

    private final CampaignStatsBroker campaignStatsBroker;

    @Autowired
    public CampaignStatsController(JwtService jwtService, UserManagementService userManagementService, CampaignStatsBroker campaignStatsBroker) {
        super(jwtService, userManagementService);
        this.campaignStatsBroker = campaignStatsBroker;
    }

    @RequestMapping(value = "/member-growth")
    @ApiOperation("Returns a map counting members joining master group via campaign, at time units (day if month parameter is provided, month otherwise)")
    public Map<String, Integer> getMemberGrowthStats(@RequestParam String campaignUid,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month) {
        campaignStatsBroker.getCampaignEngagementStats(campaignUid);
        return campaignStatsBroker.getCampaignMembershipStats(campaignUid, year, month);
    }

}
