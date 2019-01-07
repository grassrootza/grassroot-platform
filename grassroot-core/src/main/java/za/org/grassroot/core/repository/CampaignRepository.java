package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.campaign.Campaign;

import java.time.Instant;
import java.util.List;
import java.util.Set;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    Campaign findOneByUid(String uid);

    Campaign findByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);
    long countByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);

    List<Campaign> findByMasterGroupUid(String groupUid, Sort sort);
    long countByMasterGroupUidAndEndDateTimeAfter(String groupUid, Instant endDate);

    @Query(value = "select * from campaign where ?1 = ANY(tags) and end_date_time::date > now()", nativeQuery = true)
    Campaign findActiveCampaignByTag(String tag);

    @Query(value = "select unnest(tags), uid from campaign " +
            "where end_date_time > current_timestamp group by uid, tags", nativeQuery = true)
    List<Object[]> fetchAllActiveCampaignTags();

    @Query(value = "select c.campaignCode from Campaign c where c.endDateTime > current_timestamp")
    Set<String> fetchAllActiveCampaignCodes();

    @Query(value = "select c.* from campaign c where " +
            "(to_tsvector('english', c.name) @@ to_tsquery('english', ?1))", nativeQuery = true)
    List<Campaign> findCampaignsWithNamesIncluding(String tsQuery);

}
