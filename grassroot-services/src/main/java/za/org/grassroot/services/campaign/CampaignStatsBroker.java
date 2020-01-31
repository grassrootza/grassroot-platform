package za.org.grassroot.services.campaign;

import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.dto.CampaignLogsDataCollection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CampaignStatsBroker {

    void clearCampaignStatsCache(String campaignUid);

    CampaignLogsDataCollection getCampaignLogData(String campaignUid);

    Map<String, Integer> getCampaignMembershipStats(String campaignUid, Integer year, Integer month);

    Map<String, Long> getCampaignConversionStats(String campaignUid);

    Map<String, Long> getCampaignChannelStats(String campaignUid);

    Map<String, Long> getCampaignProvinceStats(String campaignUid);

    Map<String, Object> getCampaignActivityCounts(String campaignUid, CampaignActivityStatsRequest request);

    List<CampaignLog> getCampaignJoinedAndBetter(String campaignUid);

    Map<String, String> getCampaignBillingStatsInPeriod(String campaignUid, Instant start, Instant end);

}
