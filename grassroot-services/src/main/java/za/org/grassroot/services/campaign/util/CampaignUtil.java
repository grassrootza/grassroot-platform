package za.org.grassroot.services.campaign.util;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CampaignUtil {

    public static Set<CampaignMessage> processCampaignMessageByAssignmentVariation(Campaign campaign, MessageVariationAssignment variationAssignment){
        Set<CampaignMessage> messageSet = new HashSet<>();
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getVariation().equals(variationAssignment)){
                    messageSet.add(message);
                }
            }
        }
        return messageSet;
    }

    public static Set<CampaignMessage> processCampaignMessagesByLocale(Set<CampaignMessage> messageSet,Locale locale){
        Set<CampaignMessage> campaignMessageSet = new HashSet<>();
        if(messageSet != null && !messageSet.isEmpty()){
            for(CampaignMessage message : messageSet){
                if(message.getLocale().equals(locale)){
                    campaignMessageSet.add(message);
                }
            }
        }
        return campaignMessageSet;
    }

    public static Set<CampaignMessage> processCampaignMessagesByTag(Set<CampaignMessage> messageSet,String messageTag){
        Set<CampaignMessage> campaignMessageSet = new HashSet<>();
        if(messageSet != null && !messageSet.isEmpty()){
            for(CampaignMessage message : messageSet){
                if(message.getTagList() != null && message.getTagList().isEmpty()){
                    for(String tag : message.getTagList()){
                        if(tag.trim().equalsIgnoreCase(messageTag.trim())){
                            campaignMessageSet.add(message);
                        }
                    }
                }
            }
        }
        return campaignMessageSet;
    }

    public static Set<CampaignMessageAction> createCampaignMessageActionSet(CampaignMessage message, List<CampaignActionType> campaignActionTypes){
        Set<CampaignMessageAction> messageActions = new HashSet<>();
        if(campaignActionTypes != null && !campaignActionTypes.isEmpty()){
            for(CampaignActionType type: campaignActionTypes){
                CampaignMessageAction action = new CampaignMessageAction(message, null, type);
                messageActions.add(action);
            }
        }
        return messageActions;
    }
}
