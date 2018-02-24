package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.campaign.CampaignActivityStatsRequest;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.Map;

@RestController
@Grassroot2RestController
@Api("/api/campaign/stats") @Slf4j
@RequestMapping(value = "/api/campaign/stats")
public class CampaignStatsController extends BaseRestController {

    private final CampaignStatsBroker campaignStatsBroker;

    @Autowired
    public CampaignStatsController(JwtService jwtService, UserManagementService userManagementService, CampaignStatsBroker campaignStatsBroker) {
        super(jwtService, userManagementService);
        this.campaignStatsBroker = campaignStatsBroker;
    }

    @RequestMapping(value = "/member-growth", method = RequestMethod.GET)
    @ApiOperation("Returns a map counting members joining master group via campaign, at time units (day if month parameter is provided, month otherwise)")
    public Map<String, Integer> getMemberGrowthStats(@RequestParam String campaignUid,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month) {
        return campaignStatsBroker.getCampaignMembershipStats(campaignUid, year, month);
    }

    @RequestMapping(value = "/conversion", method = RequestMethod.GET)
    @ApiOperation("Returns a map of member conversion through various stages of funnel")
    public Map<String, Long> getMemberConversion(@RequestParam String campaignUid) {
        return campaignStatsBroker.getCampaignConversionStats(campaignUid);
    }

    @RequestMapping(value = "/channels", method = RequestMethod.GET)
    @ApiOperation("Returns a map of engagement by channel")
    public Map<String, Long> getChannelEngagement(@RequestParam String campaignUid) {
        return campaignStatsBroker.getCampaignChannelStats(campaignUid);
    }

    @RequestMapping(value = "/provinces", method = RequestMethod.GET)
    @ApiOperation("Returns a map of engagement by users' province (null province is converted to 'UNKNOWN'")
    public Map<String, Long> getProvinceEngagement(@RequestParam String campaignUid) {
        return campaignStatsBroker.getCampaignProvinceStats(campaignUid);
    }

    @RequestMapping(value = "/activity", method = RequestMethod.GET)
    @ApiOperation("Returns maps of activity by time unit, split by province or channel")
    public Map<String, Object> getActivityStatus(@RequestParam String campaignUid,
                                                               @RequestParam String datasetDivision,
                                                               @RequestParam String timePeriod) {
        CampaignActivityStatsRequest statsRequest = new CampaignActivityStatsRequest(datasetDivision, timePeriod);
        log.info("received an activity stats request: {}", statsRequest);
        return campaignStatsBroker.getCampaignActivityCounts(campaignUid, statsRequest);
    }

}
