package za.org.grassroot.webapp.controller.rest.campaign;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.campaign.CampaignActivityStatsRequest;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@Grassroot2RestController
@Api("/v2/api/campaign/stats") @Slf4j
@RequestMapping(value = "/v2/api/campaign/stats")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class CampaignStatsController extends BaseRestController {

    private final CampaignStatsBroker campaignStatsBroker;
    private final MemberDataExportBroker memberDataExportBroker;

    @Autowired
    public CampaignStatsController(JwtService jwtService, UserManagementService userManagementService, CampaignStatsBroker campaignStatsBroker, MemberDataExportBroker memberDataExportBroker) {
        super(jwtService, userManagementService);
        this.campaignStatsBroker = campaignStatsBroker;
        this.memberDataExportBroker = memberDataExportBroker;
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
        log.debug("received an activity stats request: {}", statsRequest);
        return campaignStatsBroker.getCampaignActivityCounts(campaignUid, statsRequest);
    }

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportCampaign(@RequestParam String campaignUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        String fileName = "campaign_results.xlsx";
        XSSFWorkbook xls = memberDataExportBroker.exportCampaignJoinedData(campaignUid, userUid);
        return RestUtil.convertWorkbookToDownload(fileName, xls);
    }

    @RequestMapping(value = "/billing", method = RequestMethod.GET)
    public ResponseEntity<byte[]> downloadBillingData(@RequestParam String campaignUid, HttpServletRequest request) {
        String fileName = "campaign_billing.xlsx";
        Map<String, String> counts = campaignStatsBroker.getCampaignBillingStatsInPeriod(campaignUid, null, null);
        XSSFWorkbook xls = memberDataExportBroker.exportCampaignBillingData(campaignUid, counts);
        return RestUtil.convertWorkbookToDownload(fileName, xls);
    }

}

