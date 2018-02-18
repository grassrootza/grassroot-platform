package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.services.campaign.CampaignMessageDTO;

import java.time.Instant;
import java.util.List;

@Getter @Setter
public class CampaignViewDTO {

    private String campaignUid;
    private String name;
    private String description;
    private String creatingUserName;
    private String creatingUserUid;
    private Instant createdDate;
    private String campaignCode;
    private Instant campaignStartDate;
    private Instant campaignEndDate;
    private CampaignType campaignType;
    private String campaignUrl;
    private String masterGroupUid;
    private String masterGroupName;
    private Integer totalUsers;
    private Integer newUsers;
    private List<String> campaignTags;
    private List<CampaignMessageDTO> campaignMessages;

    public CampaignViewDTO(String campaignUid, String campaignName, String campaignDescription, CampaignType campaignType,
                           String createUserUid, String createUserName, Instant createdDateTime,
                           Instant campaignStartDate, Instant campaignEndDate, String campaignUrl, String campaignCode, List<String> campaignTags){
        this.campaignCode = campaignCode;
        this.name = campaignName;
        this.description = campaignDescription;
        this.campaignType = campaignType;
        this.creatingUserName = createUserName;
        this.creatingUserUid = createUserUid;
        this.createdDate = createdDateTime;
        this.campaignStartDate = campaignStartDate;
        this.campaignEndDate = campaignEndDate;
        this.campaignUid = campaignUid;
        this.campaignUrl = campaignUrl;
        this.campaignTags = campaignTags;
    }
}
