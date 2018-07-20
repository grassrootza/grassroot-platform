package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.analytics.AnalyticsServiceBroker;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController @Grassroot2RestController
@Slf4j @Api("/v2/api/admin/analytics")
@RequestMapping(value = "/v2/api/admin/analytics")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AnalyticsRestController extends BaseRestController {

    private final AnalyticsServiceBroker analyticsServiceBroker;

    public AnalyticsRestController(JwtService jwtService, UserManagementService userManagementService, AnalyticsServiceBroker analyticsServiceBroker) {
        super(jwtService, userManagementService);
        this.analyticsServiceBroker = analyticsServiceBroker;
    }

    @RequestMapping(value = "/metrics", method = RequestMethod.GET)
    @ApiOperation("Returns the metrics for which analytics are available, with their total counts as of now")
    public Map<String, BigDecimal> getAvailableMetrics() {
        return analyticsServiceBroker.getAvailableMetrics();
    }

    @RequestMapping(value = "/cumulative", method = RequestMethod.GET)
    @ApiOperation("Returns the cumulative counts for a metric, with keys being timestamps")
    public Map<Long, BigDecimal> getCumulativeCounts(@RequestParam String metric) {
        return analyticsServiceBroker.getMetricCumulativeCounts(metric);
    }

    @RequestMapping(value = "/incremental", method = RequestMethod.GET)
    @ApiOperation("Returns the incremental counts within each metric, with key being of format interval_start--->>>interval_end")
    public Map<String, BigDecimal> getIncrementalCounts(@RequestParam String metric) {
        return analyticsServiceBroker.getMetricIntervalCalcs(metric);
    }


}
