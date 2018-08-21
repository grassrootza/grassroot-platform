package za.org.grassroot.services.campaign;

import za.org.grassroot.core.domain.campaign.CampaignLog;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface CampaignStatsBroker {

    void clearCampaignStatsCache(String campaignUid);

    Map<String, Integer> getCampaignMembershipStats(String campaignUid, @Nullable Integer year, @Nullable Integer month);

    Map<String, Long> getCampaignConversionStats(String campaignUid);

    Map<String, Long> getCampaignChannelStats(String campaignUid);

    Map<String, Long> getCampaignProvinceStats(String campaignUid);

    Map<String, Object> getCampaignActivityCounts(String campaignUid, CampaignActivityStatsRequest request);

    List<CampaignLog> getCampaignJoinedAndBetter(String campaignUid);

}
