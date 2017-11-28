package za.org.grassroot.webapp.model.rest.wrappers;


import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class CampaignMessageActionWrapper implements Serializable {

    private String campaignCode;
    private String messageUid;
    private String action;
    private String userUid;
    private CampaignMessageWrapper actionMessage;

    @NotEmpty(message = "campaign.message.action.message.uid.required")
    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    @NotEmpty(message = "campaign.message.action.required")
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @NotNull(message = "campaign.message.action.message.required")
    public CampaignMessageWrapper getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CampaignMessageWrapper actionMessage) {
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
