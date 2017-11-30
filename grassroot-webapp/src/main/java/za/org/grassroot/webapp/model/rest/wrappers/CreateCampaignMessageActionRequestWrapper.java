package za.org.grassroot.webapp.model.rest.wrappers;


import org.hibernate.validator.constraints.NotEmpty;
import za.org.grassroot.core.domain.campaign.CampaignActionType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class CreateCampaignMessageActionRequestWrapper implements Serializable {

    private String campaignCode;
    private String messageUid;
    private CampaignActionType action;
    private String userUid;
    private CreateCampaignMessageRequestWrapper actionMessage;

    @NotEmpty(message = "campaign.message.action.message.uid.required")
    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    @NotNull(message = "campaign.message.action.required")
    public CampaignActionType getAction() {
        return action;
    }

    public void setAction(CampaignActionType action) {
        this.action = action;
    }

    @NotNull(message = "campaign.message.action.message.required")
    public CreateCampaignMessageRequestWrapper getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CreateCampaignMessageRequestWrapper actionMessage) {
        this.actionMessage = actionMessage;
    }

    @NotEmpty(message = "campaign.message.action.user.uid.required")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotEmpty(message = "campaign.message.action.campaign.code.required")
    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }
}
