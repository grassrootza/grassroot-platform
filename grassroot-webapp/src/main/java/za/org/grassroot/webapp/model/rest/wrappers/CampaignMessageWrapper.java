package za.org.grassroot.webapp.model.rest.wrappers;


import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

public class CampaignMessageWrapper implements Serializable{

    private static final long serialVersionUID = -3071489047972056911L;
    private String message;
    private String userUid;
    private UserInterfaceType channelType;
    private String languageCode;
    private MessageVariationAssignment assignmentType;
    private String campaignCode;
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
