package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface CampaignBroker {

    Campaign load(String campaignUid);

    Set<Locale> getCampaignLanguages(String campaignUid);

    // passing null to and of the last three will set them to defaults
    CampaignMessage getOpeningMessage(String campaignUid, Locale locale, UserInterfaceType interfaceType, MessageVariationAssignment variation);

    void recordEngagement(String campaignUid, String userUid, UserInterfaceType channel, String logDesc);

    CampaignMessage loadCampaignMessage(String messageUid, String userUid);

    CampaignMessage findCampaignMessage(String campaignUid, String priorMsgUid, CampaignActionType takenAction);

    List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType takenAction, Locale locale, UserInterfaceType channel);

    List<Campaign> getCampaignsManagedByUser(String userUid);

    List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid);

    Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel);

    Set<String> getActiveCampaignCodes();

    boolean isCodeTaken(String proposedCode, String campaignUid);

    Campaign findCampaignByJoinWord(String joinWord, String userUid, UserInterfaceType channel);

    List<Campaign> broadSearchForCampaign(String userId, String searchTerm);

    // returns all in lower case
    Map<String, String> getActiveCampaignJoinWords(); // todo : cache this

    boolean isTextJoinWordTaken(String joinWord, String campaignUid);

    void signPetition(String campaignUid, String userUid, UserInterfaceType channel);

    void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel);

    // note: at present we _do not_ record the ID of the media file because (a) it is purposely external and (b) this is just tracking numbers
    void recordUserSentMedia(String campaignUid, String userUid, UserInterfaceType channel);

    boolean isUserInCampaignMasterGroup(String campaignUid, String userUid);

    Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel);

    boolean doesGroupHaveActiveCampaign(String groupUid);

    void setUserJoinTopic(String campaignUid, String userUid, String joinTopic, UserInterfaceType channel);

    boolean hasUserEngaged(String campaignUid, String userUid);

    boolean hasUserShared(String campaignUid, String userUid);

    boolean hasUserSentMedia(String campaignUid, String userUid);

    String getMessageOfType(String campaignUid, CampaignActionType actionType, String userUid, UserInterfaceType channel);

    // modifying and adding
    Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate,
                    Instant endDate, List<String> joinTopics, CampaignType campaignType, String url, boolean smsShare, long smsLimit, String imageKey);

    Campaign loadForModification(String userUid, String campaignUid);

    Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages);

    Campaign updateMasterGroup(String campaignUid, String groupUid, String userUid);

    void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid,
                               boolean removeImage, Instant endDate, String newCode, String newTextWord, String landingUrl, String petitionApi, List<String> joinTopics);

    void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudgetNumberTexts,
                                 Set<CampaignMessageDTO> sharingMessages);

    void updateCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessageDTO> revisedMessages);

    void setCampaignImage(String userUid, String campaignUid, String mediaFileKey);

    void endCampaign(String userUid, String campaignUid);

    void updateCampaignDefaultLanguage(String userUid, String campaignUid, Locale defaultLanguage);

    List<MediaFileRecord> fetchInboundCampaignMediaDetails(String userUid, String campaignUid);

}
