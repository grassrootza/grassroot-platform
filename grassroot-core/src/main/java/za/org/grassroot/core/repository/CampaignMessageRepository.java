package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;

import java.util.Locale;
import java.util.Set;

public interface CampaignMessageRepository extends JpaRepository<CampaignMessage, Long>, JpaSpecificationExecutor<CampaignMessage> {

    @Query("select distinct(cm.locale) from CampaignMessage cm where cm.campaign = ?1")
    Set<Locale> selectLocalesForCampaign(Campaign campaign);

    CampaignMessage findOneByUid(String msgUid);

}
