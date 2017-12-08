package za.org.grassroot.webapp.model.rest;

import java.io.Serializable;


public class CampaignActionViewDTO implements Serializable {

    private String actionType;
    private String uid;
    private CampaignMessageViewDTO actionMessage;

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public CampaignMessageViewDTO getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CampaignMessageViewDTO actionMessage) {
        this.actionMessage = actionMessage;
    }
}
