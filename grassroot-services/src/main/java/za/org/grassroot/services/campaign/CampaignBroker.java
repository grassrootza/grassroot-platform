package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface CampaignBroker {

    Campaign load(String campaignUid);

    Set<Locale> getCampaignLanguages(String campaignUid);

    // passing null to and of the last three will set them to defaults
    CampaignMessage getOpeningMessage(String campaignUid, Locale locale, UserInterfaceType interfaceType, MessageVariationAssignment variation);

    CampaignMessage loadCampaignMessage(String messageUid, String userUid);

    List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale);

    List<Campaign> getCampaignsManagedByUser(String userUid);

    List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid);

    Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel);

    Set<String> getActiveCampaignCodes();

    boolean isCodeTaken(String proposedCode, String campaignUid);

    Set<String> getActiveCampaignJoinTopics();

    void signPetition(String campaignUid, String userUid, UserInterfaceType channel);

    void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel);

    boolean isUserInCampaignMasterGroup(String campaignUid, String userUid);

    Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel);

    void setUserJoinTopic(String campaignUid, String userUid, String joinTopic, UserInterfaceType channel);

    // modifying and adding
    Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate,
                    Instant endDate, List<String> joinTopics, CampaignType campaignType, String url, boolean smsShare, long smsLimit, String imageKey);

    Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages);

    Campaign updateMasterGroup(String campaignUid, String groupUid, String userUid);

    void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid,
                               boolean removeImage, Instant endDate, String newCode, String landingUrl, String petitionApi, List<String> joinTopics);

    void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudget,
                                 Set<CampaignMessageDTO> sharingMessages);

    void updateCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessageDTO> revisedMessages);

    void setCampaignImage(String userUid, String campaignUid, String mediaFileKey);

}
