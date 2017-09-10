package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Campaign;

import java.time.Instant;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Campaign findByCampaignCodeAndEndDateTimeBefore(String campaignCode, Instant date);
    Campaign findBycampaignNameAndEndDateTimeBefore(String campaignName, Instant date);
}
