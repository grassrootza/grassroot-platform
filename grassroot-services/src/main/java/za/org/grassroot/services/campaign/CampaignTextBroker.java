package za.org.grassroot.services.campaign;

import za.org.grassroot.core.enums.UserInterfaceType;

public interface CampaignTextBroker {

    void setCampaignMessageText(String userUid, String campaignUid, String message);

    void clearCampaignMessageText(String userUid, String campaignUid);

    String getCampaignMessageText(String userUid, String campaignUid);

    void checkForAndTriggerCampaignText(String campaignUid, String userUid, String callBackNumber, UserInterfaceType channel);

    String handleCampaignTextResponse(String campaignUid, String userUid, String reply, UserInterfaceType channel);

}
