package za.org.grassroot.webapp.controller.rest.whatsapp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.campaign.CampaignActionType;

import java.util.LinkedHashMap;
import java.util.List;

@Getter @Setter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CampaignResponse {

    private String campaignUid;
    private String campaignName;
    private List<String> messages; // in future can include media links etc
    private LinkedHashMap<CampaignActionType, String> menu;

}
