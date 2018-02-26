package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.util.List;
import java.util.Map;

@Getter
public class CampaignMessageViewDTO {

    private final String uid;
    private final String message;
    private final CampaignActionType actionType;
    private final String createdDateTime;
    private final String assignment;
    private final String language;
    private final List<String> tags;
    private final Map<String, CampaignActionType> nextMessages;

    public CampaignMessageViewDTO(CampaignMessage message) {
        this.uid = message.getUid();
        this.message = message.getMessage();
        this.actionType = message.getActionType();
        this.assignment = message.getVariation() == null ? MessageVariationAssignment.DEFAULT.name() : message.getVariation().name();
        this.tags = message.getTagList();
        this.createdDateTime = message.getCreatedDateTime().toString();
        this.language = message.getLocale() != null ? message.getLocale().getLanguage() : null;
        this.nextMessages = message.getNextMessages();
    }

}
