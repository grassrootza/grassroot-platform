package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Campaign;

import java.time.Instant;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Campaign findByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);
    Campaign findByCampaignNameAndEndDateTimeAfter(String campaignName, Instant date);
}
