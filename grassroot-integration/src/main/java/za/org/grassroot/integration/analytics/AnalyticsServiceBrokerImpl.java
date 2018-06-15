package za.org.grassroot.integration.analytics;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service @Slf4j
public class AnalyticsServiceBrokerImpl implements AnalyticsServiceBroker {

    private AmazonDynamoDB dynamoDBClient;

    @PostConstruct
    public void init() {
        dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.EU_WEST_1)
                .withCredentials(new ProfileCredentialsProvider("analyticalTablesClient")).build();
    }

    @Override
    public Map<String, BigDecimal> getAvailableMetrics() {
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        Table metricsTable = dynamoDB.getTable("activity_metrics");
        ItemCollection<ScanOutcome> result = metricsTable.scan(new ScanSpec());
        Map<String, BigDecimal> mappedResults = new HashMap<>();
        result.forEach(item -> mappedResults.put((String) item.get("name"), (BigDecimal) item.get("totalCount")));
        return mappedResults;
    }

    @Override
    public Map<Long, BigDecimal> getMetricCumulativeCounts(String metricName) {
        ItemCollection<QueryOutcome> items = queryCalcsForMetric(metricName, "end_time,total_count");
        Map<Long, BigDecimal> mappedResults = new HashMap<>();
        // key is a timestamp (epoch millis), so have to convert to long
        items.forEach(item -> mappedResults.put(((BigDecimal) item.get("end_time")).longValue(), (BigDecimal) item.get("total_count")));
        return mappedResults;
    }

    @Override
    public Map<String, BigDecimal> getMetricIntervalCalcs(String metricName) {
        ItemCollection<QueryOutcome> items = queryCalcsForMetric(metricName, "start_time,end_time,this_count");
        Map<String, BigDecimal> mappedResults = new HashMap<>();
        items.forEach(item -> {
            final long start = ((BigDecimal) item.get("start_time")).longValue();
            final long end = ((BigDecimal) item.get("end_time")).longValue();
            final String key = String.format("%d--->>>%d", start, end);
            mappedResults.put(key, (BigDecimal) item.get("this_count"));
        });
        return mappedResults;
    }

    private ItemCollection<QueryOutcome> queryCalcsForMetric(String metricName, String projectionExpression) {
        DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
        Table metricCountTable = dynamoDB.getTable("activity_metric_calcs");
        QuerySpec spec = new QuerySpec()
                .withHashKey("metric_name", metricName)
                .withProjectionExpression(projectionExpression);
        return metricCountTable.query(spec);
    }
}
