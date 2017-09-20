package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface CampaignBroker {

    List<Campaign> getCampaignsCreatedByUser(String userUid);


    /**
     * Get Campaign information by campaign code
     * @param campaignCode
     * @return - Campaign details
     */
    Campaign getCampaignDetailsByCode(String campaignCode);

    /**
     * Get Campaign information by name of the campaign
     * @param campaignName -name ofcampaign
     * @return
     */
    Campaign getCampaignDetailsByName(String campaignName);


    /**
     * Get Campaign message by campaign code
     * @param campaignCode campign code
     * @param assignment - different message use cases
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment);

    /**
     * Get Campaign message using campaign name
     * @param campaignName - name
     * @param assignment - message use case,eg. test
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment);

    /**
     * Get campaign message using campaign code and language
     * @param campaignCode - campaign code
     * @param assignment - message use cases
     * @param locale - locale  for message
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale);

    /**
     * Get campaign message using campaign code and language
     * @param campaignCode - campaign code
     * @param assignment - message use case test, production etc
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @param messageTag
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag);

    /**
     *
     * @param campaignName
     * @param assignment
     * @param messageTag
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag);

    /**
     *
     * @param campaignName
     * @param campaignCode
     * @param description
     * @param createUser
     * @param startDate
     * @param endDate
     * @return
     */
    Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Instant startDate, Instant endDate, List<String> campaignTags);

    /**
     *
     * @param campaignName
     * @param campaignCode
     * @param description
     * @param createUser
     * @param groupId
     * @param startDate
     * @param endDate
     * @param campaignTags
     * @return
     */
    Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Long groupId, Instant startDate, Instant endDate, List<String> campaignTags);
    /**
     *
     * @param campaignCode
     * @param campaignMessage
     * @param messageLocale
     * @param assignment
     * @param interfaceType
     * @param createUser
     * @return
     */
    Campaign addCampaignMessage(String campaignCode, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags);

    /**
     *
     * @param campaignCode
     * @param messageUid
     * @param campaignActionTypes
     * @return
     */
    Campaign addActionsToCampaignMessage(String campaignCode, String messageUid, List<CampaignActionType> campaignActionTypes);

    /**
     *
     * @param campaignCode
     * @param tags
     * @return
     */
    Campaign addCampaignTags(String campaignCode, List<String> tags);

    /**
     *
     * @param tag
     * @return
     */
    Campaign getCampaignByTag(String tag);

    /**
     *
     * @param campaignCode
     * @param groupId
     * @return
     */
    Campaign linkCampaignToMasterGroup(String campaignCode, Long groupId);
}
