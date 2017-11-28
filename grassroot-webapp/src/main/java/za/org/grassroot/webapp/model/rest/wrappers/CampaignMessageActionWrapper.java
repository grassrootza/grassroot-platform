package za.org.grassroot.webapp.model.rest.wrappers;


import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;

public class CampaignMessageActionWrapper implements Serializable {

    private String campaignCode;
    private String messageUid;
    private String action;
    private String userUid;
    private CampaignMessageWrapper actionMessage;

    @NotBlank(message = "{campaign.message.action.message.uid.required}")
    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }
    @NotBlank(message = "{campaign.message.action.required}")
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
    @NotBlank(message = "{campaign.message.action.message.required}")
    public CampaignMessageWrapper getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CampaignMessageWrapper actionMessage) {
        this.actionMessage = actionMessage;
    }

    @NotBlank(message = "{campaign.message.action.user.uid.required}")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotBlank(message = "{campaign.message.campaign.code.required}")
    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }
}
