package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface CampaignBroker {
    /**
     * Get Campaign information by campaign code
     * @param campaignCode
     * @return
     */
    Campaign getCampaignDetailsByCode(String campaignCode);

    /**
     * Get Campaign information by name of the campaign
     * @param campaignName
     * @return
     */
    Campaign getCampaignDetailsByName(String campaignName);


    /**
     * Get Campaign message by campaign code
     * @param campaignCode
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment);

    /**
     * Get Campaign message using campaign name
     * @param campaignName
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment);

    /**
     * Get campaign message using campaign code and language
     * @param campaignCode
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);

    /**
     * Get campaign message using campaign code and language
     * @param campaignCode - campaign code
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);

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
     * @param campaignCode
     * @param campaignMessage
     * @param messageLocale
     * @param assignment
     * @param sequenceNumber
     * @param createUser
     * @return
     */
    Campaign addCampaignMessage(String campaignCode, String campaignMessage,String messageLocale,MessageVariationAssignment assignment, Integer sequenceNumber, User createUser, List<String> messageTags);

    /**
     *
     * @param campaignCode
     * @param tags
     * @return
     */
    Campaign addCampaignTags(String campaignCode, List<String> tags);

    void linkCampaigntoMasterGroup(String campaignCode, Integer groupId);

}
