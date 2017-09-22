package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    
    Campaign findByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);
    Campaign findByCampaignNameAndEndDateTimeAfter(String campaignName, Instant date);

    List<Campaign> findByCreatedByUser(User createdByUser, Sort sort);

    @Query(value = "select * from campaign where ?1 = ANY(tags) and end_date_time::date > now()", nativeQuery = true)
    Campaign findActiveCampaignByTag(String tag);
}
