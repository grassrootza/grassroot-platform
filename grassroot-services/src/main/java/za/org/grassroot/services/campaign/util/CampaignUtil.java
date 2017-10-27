package za.org.grassroot.services.campaign.util;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.util.HashSet;
import java.util.List;
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

    public static CampaignMessage getCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocale(Campaign campaign, MessageVariationAssignment variationAssignment, UserInterfaceType channel, Locale locale){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getVariation().equals(variationAssignment) && message.getChannel().equals(channel) && message.getLocale().equals(locale)){
                    return message;
                }
            }
        }
        return null;
    }

    public static CampaignMessage getNextCampaignMessageByActionType(Campaign campaign, CampaignActionType action, String messageUid){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getUid().trim().equalsIgnoreCase(messageUid.trim())){
                    if(message.getCampaignMessageActionSet() != null && !message.getCampaignMessageActionSet().isEmpty()){
                        for(CampaignMessageAction campaignMessageAction: message.getCampaignMessageActionSet()){
                            if(campaignMessageAction.getActionType().equals(action)){
                                return  campaignMessageAction.getActionMessage();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    //To do. look at recursive function
    public static CampaignMessage getCampaignMessageFromCampaignByMessageUid(Campaign campaign, String messageUid){
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getUid().equalsIgnoreCase(messageUid)){
                    return message;
                }
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
