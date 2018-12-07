package za.org.grassroot.core.dto;

import lombok.Builder;
import lombok.Getter;

@Builder @Getter
public class CampaignLogsDataCollection {

    private long totalJoined;
    private long totalEngaged;
    private long totalSigned;
    private long lastActivityEpochMilli;

}
