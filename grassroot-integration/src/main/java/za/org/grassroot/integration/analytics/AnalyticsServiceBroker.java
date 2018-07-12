package za.org.grassroot.integration.analytics;

import java.math.BigDecimal;
import java.util.Map;

public interface AnalyticsServiceBroker {

    // returns what's available, with most recent cumulative count
    Map<String, BigDecimal> getAvailableMetrics();

    // returns all the metric counts (timestamps & cumulative count)
    Map<Long, BigDecimal> getMetricCumulativeCounts(String metricName);

    // returns all the counts for the metric within the interval
    Map<String, BigDecimal> getMetricIntervalCalcs(String metricName);

}
