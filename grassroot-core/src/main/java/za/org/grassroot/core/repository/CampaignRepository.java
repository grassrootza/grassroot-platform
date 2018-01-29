package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;

import java.time.Instant;
import java.util.List;
import java.util.Set;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    Campaign findOneByUid(String uid);

    Campaign findByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);
    Campaign findByNameAndEndDateTimeAfter(String campaignName, Instant date);

    List<Campaign> findByCreatedByUser(User createdByUser, Sort sort);

    List<Campaign> findByMasterGroupUid(String groupUid, Sort sort);

    @Query(value = "select * from campaign where ?1 = ANY(tags) and end_date_time::date > now()", nativeQuery = true)
    Campaign findActiveCampaignByTag(String tag);

    @Query(value = "select distinct unnest(tags) from campaign " +
            "where end_date_time > current_timestamp", nativeQuery = true)
    Set<String> fetchAllActiveCampaignTags();

    @Query(value = "select c.campaignCode from Campaign c where c.endDateTime > current_timestamp")
    Set<String> fetchAllActiveCampaignCodes();
}
