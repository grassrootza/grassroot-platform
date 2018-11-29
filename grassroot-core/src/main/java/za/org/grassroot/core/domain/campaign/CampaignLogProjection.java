package za.org.grassroot.core.domain.campaign;

import za.org.grassroot.core.enums.CampaignLogType;

import java.time.Instant;

public interface CampaignLogProjection {

    long getUserId();
    CampaignLogType getCampaignLogType();
    Instant getCreationTime();

    default boolean typeFilter(CampaignLogType logType) {
        return logType.equals(getCampaignLogType());
    }

}
