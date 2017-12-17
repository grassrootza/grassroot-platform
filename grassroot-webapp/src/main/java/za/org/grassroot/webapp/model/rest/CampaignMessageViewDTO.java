package za.org.grassroot.webapp.model.rest;

import java.io.Serializable;
import java.util.List;


public class CampaignMessageViewDTO implements Serializable {

    private static final long serialVersionUID = -8566708934792370862L;
    private String message;
    private String createdDateTime;
    private String createUser;
    private String assignment;
    private String language;
    private String uid;
    private List<String> tags;
    private List<CampaignActionViewDTO> messageActions;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(String createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public String getAssignment() {
        return assignment;
    }

    public void setAssignment(String assignment) {
        this.assignment = assignment;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<CampaignActionViewDTO> getMessageActions() {
        return messageActions;
    }

    public void setMessageActions(List<CampaignActionViewDTO> messageActions) {
        this.messageActions = messageActions;
    }
}
