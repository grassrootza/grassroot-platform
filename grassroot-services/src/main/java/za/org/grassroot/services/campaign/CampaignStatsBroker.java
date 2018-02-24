package za.org.grassroot.services.campaign;

import javax.annotation.Nullable;
import java.util.Map;

public interface CampaignStatsBroker {

    void clearCampaignStatsCache(String campaignUid);

    Map<String, Integer> getCampaignMembershipStats(String campaignUid, @Nullable Integer year, @Nullable Integer month);

    Map<String, Long> getCampaignConversionStats(String campaignUid);

    Map<String, Long> getCampaignChannelStats(String campaignUid);

    Map<String, Long> getCampaignProvinceStats(String campaignUid);

    Map<String, Map<String, Integer>> getCampaignActivityCounts(String campaignUid, CampaignActivityStatsRequest request);

}
