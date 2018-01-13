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

}
