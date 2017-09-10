package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.util.Set;

public interface CampaignService {
    /**
     * Get Campaign by campaign code
     * @param campaignCode
     * @return
     */
    Campaign getCampaignDetailsByCode(String campaignCode);

    /**
     *
     * @param campaignName
     * @return
     */
    Campaign getCampaignDetailsByName(String campaignName);

    /**
     *
     * @param tag
     * @return
     */
    Campaign getCampaignByTag(String tag);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment);

    /**
     *
     * @param campaignName
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);
}
