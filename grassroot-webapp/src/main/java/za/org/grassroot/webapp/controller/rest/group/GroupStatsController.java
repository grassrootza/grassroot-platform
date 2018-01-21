package za.org.grassroot.webapp.controller.rest.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.group.GroupStatsBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.Map;

@RestController
@Grassroot2RestController
@Api("/api/group/stats")
@RequestMapping(value = "/api/group/stats")
public class GroupStatsController extends BaseRestController {


    private final GroupStatsBroker groupStatsBroker;

    public GroupStatsController(JwtService jwtService, UserManagementService userManagementService, GroupStatsBroker groupStatsBroker) {
        super(jwtService, userManagementService);
        this.groupStatsBroker = groupStatsBroker;
    }


    @RequestMapping(value = "/member-growth")
    @ApiOperation("Returns a map of member count at time units (day if month parameter is provided, month otherwise)")
    public Map<String, Integer> getMemberGrowthStats(@RequestParam String groupUid,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month) {
        return groupStatsBroker.getMembershipGrowthStats(groupUid, year, month);
    }


    @RequestMapping(value = "/provinces")
    @ApiOperation("Returns a map of member count by province")
    public Map<String, Long> getProvincesStats(String groupUid) {
        return groupStatsBroker.getProvincesStats(groupUid);
    }

    @RequestMapping(value = "/sources")
    @ApiOperation("Returns a map of member count by source (join method)")
    public Map<String, Long> getSourcesStats(String groupUid) {
        return groupStatsBroker.getSourcesStats(groupUid);
    }


    @RequestMapping(value = "/organisations")
    @ApiOperation("Returns a map of member count by organisation")
    public Map<String, Long> getOrganisationsStats(String groupUid) {
        return groupStatsBroker.getOrganisationsStats(groupUid);
    }

    @RequestMapping(value = "/member-details")
    @ApiOperation("Returns percents of users who has specific info set")
    public Map<String, Integer> getMemberDetailsStats(String groupUid) {
        return groupStatsBroker.getMemberDetailsStats(groupUid);
    }


    @RequestMapping(value = "/topic-interests")
    @ApiOperation("Returns percents of users interested in specific topic")
    public Map<String, Integer> getTopicInterestStats(String groupUid) {
        return groupStatsBroker.getTopicInterestStats(groupUid);
    }

}
