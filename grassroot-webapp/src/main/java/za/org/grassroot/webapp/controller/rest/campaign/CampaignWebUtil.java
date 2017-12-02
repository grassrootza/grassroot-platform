package za.org.grassroot.webapp.controller.rest.campaign;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.webapp.model.rest.CampaignActionViewDTO;
import za.org.grassroot.webapp.model.rest.CampaignMessageViewDTO;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;

import java.util.ArrayList;
import java.util.List;

public class CampaignWebUtil {

    private CampaignWebUtil(){}

    public static List<CampaignViewDTO> createCampaignViewDtoList(List<Campaign> campaigns){
        List<CampaignViewDTO> campaignList = new ArrayList<>();
        if(campaigns == null || campaigns.isEmpty()){
            return campaignList;
        }
        for(Campaign campaign : campaigns){
            campaignList.add(createCampaignViewDTO(campaign));
        }
        return campaignList;
    }

    public static CampaignViewDTO createCampaignViewDTO(Campaign campaign){
        CampaignViewDTO campaignDto = new CampaignViewDTO(campaign.getCampaignCode(), campaign.getCampaignName(),campaign.getCampaignDescription(),campaign.getCampaignType().name(),campaign.getCreatedByUser().getUid(),
                campaign.getCreatedByUser().getDisplayName(),campaign.getStartDateTime().toString(),campaign.getEndDateTime().toString(), campaign.getUrl(),campaign.getUid(),campaign.getTagList());
        campaignDto.setCampaignMasterGroupName(campaign.getMasterGroup() != null ? campaign.getMasterGroup().getGroupName() : null);
        campaignDto.setCampaignMasterGroupUid(campaign.getMasterGroup() != null ? campaign.getMasterGroup().getUid() : null);
        campaignDto.setTotalUsers((campaign.getMasterGroup() != null && campaign.getMasterGroup().getMembers() != null)? campaign.getMasterGroup().getMembers().size() : 0);
        if(campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            List<CampaignMessageViewDTO> messageViewDTOList = new ArrayList<>();
            for(CampaignMessage message: campaign.getCampaignMessages()){
                messageViewDTOList.add(createMessageDTO(message));
            }
            campaignDto.setCampaignMessages(messageViewDTOList);
        }
        return campaignDto;
    }

    private static CampaignMessageViewDTO createMessageDTO(CampaignMessage message){
        CampaignMessageViewDTO messageViewDTO = new CampaignMessageViewDTO();
        messageViewDTO.setUid(message.getUid());
        messageViewDTO.setAssignment(message.getVariation().name());
        messageViewDTO.setTags(message.getTagList());
        messageViewDTO.setCreatedDateTime(message.getCreatedDateTime().toString());
        messageViewDTO.setMessage(message.getMessage());
        messageViewDTO.setCreateUser(message.getCreatedByUser()!=null ? message.getCreatedByUser().getUid() : null);
        messageViewDTO.setLanguage(message.getLocale() != null ? message.getLocale().getLanguage() : null);
        if(message.getCampaignMessageActionSet() == null || message.getCampaignMessageActionSet().isEmpty()){
            return  messageViewDTO;
        }
        for(CampaignMessageAction action : message.getCampaignMessageActionSet()){
            CampaignActionViewDTO actionViewDTO = new CampaignActionViewDTO();
            actionViewDTO.setUid(action.getUid());
            actionViewDTO.setActionType(action.getActionType().name());
            actionViewDTO.setActionMessage(createMessageDTO(action.getActionMessage()));
        }
        return messageViewDTO;
    }
}
