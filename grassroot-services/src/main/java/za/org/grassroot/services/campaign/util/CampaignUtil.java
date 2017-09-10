package za.org.grassroot.services.campaign.util;


import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.util.HashSet;
import java.util.Set;

public class CampaignUtil {

    public static Set<CampaignMessage> processCampaignMessageByAssignmentVariation(Campaign campaign, MessageVariationAssignment variationAssignment){
        Set<CampaignMessage> messageSet = new HashSet<CampaignMessage>();
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getVariation().equals(variationAssignment)){
                    messageSet.add(message);
                }
            }
        }
        return messageSet;
    }

    public static Set<CampaignMessage> processCampaignMessagesByLocale(Set<CampaignMessage> messageSet,String locale){
        Set<CampaignMessage> campaignMessageSet = new HashSet<CampaignMessage>();
        if(messageSet != null && !messageSet.isEmpty()){
            for(CampaignMessage message : messageSet){
                if(message.getLocale().equalsIgnoreCase(locale)){
                    campaignMessageSet.add(message);
                }
            }
        }
        return campaignMessageSet;
    }
}
