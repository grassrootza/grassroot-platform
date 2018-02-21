package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.annotation.Nullable;
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
    CampaignMessage loadCampaignMessage(String messageUid, String userUid);
    List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale);

    List<Campaign> getCampaignsCreatedByUser(String userUid);

    List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid);

    Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel);

    Set<String> getActiveCampaignCodes();

    Set<String> getActiveCampaignJoinTopics();

    void signPetition(String campaignUid, String userUid, UserInterfaceType channel);

    void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel);

    boolean isUserInCampaignMasterGroup(String campaignUid, String userUid);

    Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel);

    // modifying and adding
    Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate,
                    Instant endDate, List<String> campaignTags, CampaignType campaignType, String url);

    Campaign addCampaignMessage(String campaignUid, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags);

    Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages);

    Campaign updateMasterGroup(String campaignUid, String groupUid, String userUid);

    void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid,
                               Instant endDate, String landingUrl, String petitionApi);

    // new code is in case the old one has been taken in the meantime
    void reactivateCampaign(String userUid, String campaignUid, Instant newEndDate, String newCode);

    void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudget,
                                 Set<CampaignMessage> sharingMessages);

    void changeCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessage> revisedMessages);

}
