package za.org.grassroot.webapp.controller.rest.home;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.PublicActivityLog;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.List;

@Slf4j
@RestController @Grassroot2RestController
@RequestMapping("/v2/api/activity") @Api("/v2/activity")
public class PublicActivityController {

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public PublicActivityController(LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation("Fetches most recent activity that can be displayed public, up to a limit (max is 100)")
    public List<PublicActivityLog> fetchRecentActivityPublic(@RequestParam(required = false) Integer numberToFetch) {
        return logsAndNotificationsBroker.fetchMostRecentPublicLogs(Math.min(100, numberToFetch));
    }

}
