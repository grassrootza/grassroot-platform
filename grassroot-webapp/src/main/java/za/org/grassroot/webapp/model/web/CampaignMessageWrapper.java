package za.org.grassroot.webapp.model.web;


import org.hibernate.validator.constraints.NotBlank;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.io.Serializable;
import java.util.Set;

public class CampaignMessageWrapper implements Serializable{

    private static final long serialVersionUID = -3071489047972056911L;
    private String message;
    private String userUid;
    private UserInterfaceType channel;
    private String language;
    private String assignment;
    private String campaignCode;
    private Set<String> tags;

    @NotBlank(message = "{campaign.message.required}")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @NotBlank(message = "{campaign.message.user.uid.required}")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotBlank(message = "{campaign.message.sequence.required}")
    public UserInterfaceType getChannel() {
        return channel;
    }

    public void setChannel(UserInterfaceType channel) {
        this.channel = channel;
    }

    @NotBlank(message = "{campaign.message.language.required}")
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @NotBlank(message = "{campaign.message.assignment.required}")
    public String getAssignment() {
        return assignment;
    }

    public void setAssignment(String assignment) {
        this.assignment = assignment;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    @NotBlank(message = "{campaign.message.code.required}")
    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }
}
