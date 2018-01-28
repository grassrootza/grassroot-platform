package za.org.grassroot.services.campaign.util;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CampaignUtil {

    public static Set<CampaignMessage> processCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocale(Campaign campaign, MessageVariationAssignment variationAssignment, UserInterfaceType channel, Locale locale){
        Set<CampaignMessage> messageSet = new HashSet<>();
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getVariation().equals(variationAssignment) && message.getChannel().equals(channel) && message.getLocale().equals(locale)){
                    messageSet.add(message);
                }
            }
        }
        return messageSet;
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


    public static CampaignMessage getCampaignOpeningMessage(Campaign campaign, MessageVariationAssignment variationAssignment, UserInterfaceType channel, Locale locale){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getActionType().equals(CampaignActionType.OPENING) &&
                        message.getVariation().equals(variationAssignment)
                        && message.getChannel().equals(channel)
                        && message.getLocale().equals(locale)){
                    return message;
                }
            }
        }
        return null;
    }

    public static CampaignMessage getNextCampaignMessageByActionTypeAndLocale(Campaign campaign, CampaignActionType action, String messageUid, Locale locale){
        CampaignMessage message = CampaignUtil.findCampaignMessageFromCampaignByMessageUid(campaign,messageUid);
        if(message != null && message.getCampaignMessageActionSet() != null && !message.getCampaignMessageActionSet().isEmpty()){
            return message.getCampaignMessageActionSet().stream()
                    .filter(act -> action.equals(act.getActionType()))
                    .map(CampaignMessageAction::getActionMessage).findFirst().orElse(null);
        }
        return null;
    }


    public static CampaignMessage findCampaignMessageFromCampaignByMessageUid(Campaign campaign, String messageUid){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                CampaignMessage campaignMessage = searchCampaignMessageRecursively(message, messageUid);
                if(campaignMessage != null){
                    return campaignMessage;
                }
            }
        }
        return null;
    }

    private static CampaignMessage searchCampaignMessageRecursively(CampaignMessage message, String messageUid){
        if(message.getUid().trim().equalsIgnoreCase(messageUid.trim())){
            return message;
        }
        for(CampaignMessageAction action : message.getCampaignMessageActionSet()){
            CampaignMessage campaignMessage = searchCampaignMessageRecursively(action.getActionMessage(), messageUid);
            if(campaignMessage != null){
                return campaignMessage;
            }
        }
        return null;
    }

    public static Set<Locale> getCampaignSupportedLanguages(Campaign campaign){
        Set<Locale> localeSet = new HashSet<>();
        for(CampaignMessage message : campaign.getCampaignMessages()){
            localeSet.add(message.getLocale());
        }
        return localeSet;
    }

    public static CampaignMessage getFirstCampaignMessageByLocale(Campaign campaign, String languageCode){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getLocale().equals(new Locale(languageCode))){
                    return message;
                }
            }
        }
        return null;
    }
}
