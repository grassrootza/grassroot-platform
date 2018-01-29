package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.User;
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

    Campaign load(String userUid, String campaignUid);
    Set<Locale> getCampaignLanguages(String campaignUid);

    // passing null to and of the last three will set them to defaults
    CampaignMessage getOpeningMessage(String campaignUid, Locale locale, UserInterfaceType interfaceType, MessageVariationAssignment variation);
    CampaignMessage loadCampaignMessage(String messageUid, String userUid);

    List<Campaign> getCampaignsCreatedByUser(String userUid);
    List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid);

    Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog);

    Set<String> getActiveCampaignCodes();
    Set<String> getCampaignTags();

//    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type);

    Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate,
                    Instant endDate, List<String> campaignTags, CampaignType campaignType, String url);

    Campaign addCampaignMessage(String campaignUid, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags);

    Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages);

    Campaign addCampaignTags(String campaignCode, List<String> tags);

    Campaign updateMasterGroup(String campaignCode, String groupUid, String userUid);

    Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid);

    Campaign addActionToCampaignMessage(String campaignUid, String parentMessageUid,CampaignActionType actionType, String actionMessage, Locale actionMessageLocale, MessageVariationAssignment actionMessageAssignment, UserInterfaceType interfaceType, User createUser, Set<String> actionMessageTags);
}
