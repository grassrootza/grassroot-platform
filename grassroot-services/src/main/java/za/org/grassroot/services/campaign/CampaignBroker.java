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

    List<Campaign> getCampaignsCreatedByUser(String userUid);
    List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid);

    Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog);
    Campaign getCampaignDetailsByName(String campaignName, String userUid, boolean storeLog);

    Set<String> getActiveCampaignCodes();
    Set<String> getCampaignTags();

    /**
     * Get Campaign message using campaign name
     * @param campaignName - name
     * @param assignment - message use case,eg. test
     * @return - set of messages linked to campaign
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment, UserInterfaceType type, Locale locale);

    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type);

    /**
     * Get campaign message using campaign code and language
     * @param campaignCode - campaign code
     * @param assignment - message use case test, production etc
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type);

    Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate,
                    Instant endDate, List<String> campaignTags, CampaignType campaignType, String url);

    Campaign addCampaignMessage(String campaignUid, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags);

    Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages);

    Campaign addCampaignTags(String campaignCode, List<String> tags);

    Campaign updateMasterGroup(String campaignCode, String groupUid, String userUid);

    CampaignMessage getCampaignMessageByCampaignCodeAndActionType(String campaignCode, MessageVariationAssignment assignment,UserInterfaceType channel, CampaignActionType actionType, String inputNumber, Locale locale);

    Campaign addUserToCampaignMasterGroup(String campaignCode,String phoneNumber);

    Campaign addActionToCampaignMessage(String campaignUid, String parentMessageUid,CampaignActionType actionType, String actionMessage, Locale actionMessageLocale, MessageVariationAssignment actionMessageAssignment, UserInterfaceType interfaceType, User createUser, Set<String> actionMessageTags);
}
