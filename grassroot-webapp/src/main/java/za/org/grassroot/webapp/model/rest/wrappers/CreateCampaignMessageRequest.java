package za.org.grassroot.webapp.model.rest.wrappers;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Locale;
import java.util.Set;

@ApiModel(value = "CreateCampaignMessageRequest")
public class CreateCampaignMessageRequest implements Serializable{

    private static final long serialVersionUID = -3071489047972056911L;
    @ApiModelProperty(value = "message content", required = true)
    private String message;
    @ApiModelProperty(value = "channel type", required = true, allowableValues = "UNKNOWN,USSD,WEB,ANDROID,SYSTEM,INCOMING_SMS")
    private UserInterfaceType channelType;
    @ApiModelProperty(value = "language code for message", required = true)
    private Locale language;
    @ApiModelProperty(value = "assignment of message", required = false, allowableValues = "DEFAULT,EXPERIMENT,CONTROL,UNASSIGNED")
    private MessageVariationAssignment assignmentType;
    @ApiModelProperty(value = "campaign code to link message to", required = false)
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

    @NotNull(message = "campaign.message.channel.required")
    public UserInterfaceType getChannelType() {
        return channelType;
    }

    public void setChannelType(UserInterfaceType channelType) {
        this.channelType = channelType;
    }

    @NotNull(message = "campaign.message.language.required")
    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = language;
    }

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

    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }
}
