package za.org.grassroot.core.repository;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignLogProjection;
import za.org.grassroot.core.enums.CampaignLogType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface CampaignLogRepository extends JpaRepository<CampaignLog, Long>, JpaSpecificationExecutor<CampaignLog> {

    List<CampaignLog> findByCampaignLogTypeAndCampaignMasterGroupUid(CampaignLogType campaignLogType, String masterGroupUid);

    List<CampaignLog> findByCampaignLogType(CampaignLogType logType, Pageable pageable);

    void deleteAllByCampaignAndUserAndCampaignLogType(Campaign campaign, User user, CampaignLogType logType);

    @Query(value = "select " +
            "sum(case when campaign_log_type = 'CAMPAIGN_FOUND' then 1 else 0 end) as total_engaged, " +
            "sum(case when campaign_log_type = 'CAMPAIGN_PETITION_SIGNED' then 1 else 0 end) as total_signed, " +
            "sum(case when campaign_log_type = 'CAMPAIGN_USER_ADDED_TO_MASTER_GROUP' then 1 else 0 end) as total_joined " +
            "from (select distinct user_id, campaign_log_type from campaign_log where campaign_id = ?1) as unique_records", nativeQuery = true)
    Map<String, BigInteger> selectCampaignLogCounts(long campaignId);

    CampaignLogProjection findFirstByCampaignOrderByCreationTimeDesc(Campaign campaign);

}
