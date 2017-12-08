package za.org.grassroot.webapp.model.rest.wrappers;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

@ApiModel(value = "CreateCampaignMessageRequest")
public class CreateCampaignMessageRequest implements Serializable{

    private static final long serialVersionUID = -3071489047972056911L;
    @ApiModelProperty(value = "message content", required = true)
    private String message;
    @ApiModelProperty(value = "message creater's user uid", required = true)
    private String userUid;
    @ApiModelProperty(value = "channel type", required = true, allowableValues = "UNKNOWN,USSD,WEB,ANDROID,SYSTEM,INCOMING_SMS")
    private UserInterfaceType channelType;
    @ApiModelProperty(value = "language code for message", required = true)
    private String languageCode;
    @ApiModelProperty(value = "assignment of message", required = true, allowableValues = "DEFAULT,EXPERIMENT,CONTROL,UNASSIGNED")
    private MessageVariationAssignment assignmentType;
    @ApiModelProperty(value = "campaign code to link message to", required = true)
    private String campaignCode;
    @ApiModelProperty(value = "tags for message", required = false)
    private Set<String> tags;

    @NotNull(message = "campaign.message.required")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @NotNull(message = "campaign.message.user.uid.required")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotNull(message = "campaign.message.channel.required")
    public UserInterfaceType getChannelType() {
        return channelType;
    }

    public void setChannelType(UserInterfaceType channelType) {
        this.channelType = channelType;
    }

    @NotNull(message = "campaign.message.language.required")
    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    @NotNull(message = "campaign.message.assignment.required")
    public MessageVariationAssignment getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(MessageVariationAssignment assignmentType) {
        this.assignmentType = assignmentType;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    @NotNull(message = "campaign.message.campaign.code.required")
    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }
}
