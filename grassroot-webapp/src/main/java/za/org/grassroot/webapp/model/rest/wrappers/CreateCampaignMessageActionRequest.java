package za.org.grassroot.webapp.model.rest.wrappers;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.NotEmpty;
import za.org.grassroot.core.domain.campaign.CampaignActionType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@ApiModel(value = "CreateCampaignMessageActionRequest")
public class CreateCampaignMessageActionRequest implements Serializable {

    @ApiModelProperty(value="code of campaign")
    private String campaignCode;
    @ApiModelProperty(value="message uid an action links to")
    private String messageUid;
    @ApiModelProperty(value="type of action", allowableValues = "TAG_ME,JOIN_MASTER_GROUP,SIGN_PETITION,MORE_INFO,EXIT")
    private CampaignActionType action;
    @ApiModelProperty(value="uid of user creating action")
    private String userUid;
    @ApiModelProperty(value="message to send when action is chosen")
    private CreateCampaignMessageRequest actionMessage;

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
    public CreateCampaignMessageRequest getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CreateCampaignMessageRequest actionMessage) {
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
