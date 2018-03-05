package za.org.grassroot.core.repository;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.enums.CampaignLogType;

import java.util.List;

public interface CampaignLogRepository extends JpaRepository<CampaignLog, Long>, JpaSpecificationExecutor<CampaignLog> {

    List<CampaignLog> findByCampaignLogTypeAndCampaignMasterGroupUid(CampaignLogType campaignLogType, String masterGroupUid);

    List<CampaignLog> findByCampaignLogType(CampaignLogType logType, Pageable pageable);
}
