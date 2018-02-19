package za.org.grassroot.webapp.controller.rest.campaign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.campaign.MessageLanguagePair;
import za.org.grassroot.webapp.model.rest.CampaignViewDTO;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
        CampaignViewDTO campaignDto = new CampaignViewDTO(campaign.getUid(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getCampaignType(),
                campaign.getCreatedByUser().getUid(),
                campaign.getCreatedByUser().getName(),
                campaign.getCreatedDateTime(),
                campaign.getStartDateTime(),
                campaign.getEndDateTime(),
                campaign.getLandingUrl(),
                campaign.getCampaignCode(),
                campaign.getTagList());

        campaignDto.setMasterGroupName(campaign.getMasterGroup() != null ? campaign.getMasterGroup().getGroupName() : null);
        campaignDto.setMasterGroupUid(campaign.getMasterGroup() != null ? campaign.getMasterGroup().getUid() : null);
        campaignDto.setTotalUsers((campaign.getMasterGroup() != null && campaign.getMasterGroup().getMembers() != null)? campaign.getMasterGroup().getMembers().size() : 0);
        campaignDto.setNewUsers(getMemberJoinedViaCampaign(campaign));

        log.info("do we have messages? number: {}, values: {}", campaign.getCampaignMessages().size(), campaign.getCampaignMessages());
        if(!campaign.getCampaignMessages().isEmpty()){
            campaignDto.setCampaignMessages(groupCampaignMessages(campaign));
            log.info("campaign DTO message list: {}", campaign.getCampaignMessages());
        }

        return campaignDto;
    }

    private static List<CampaignMessageDTO> groupCampaignMessages(Campaign campaign) {
        List<String> messageGroupIds = campaign.getCampaignMessages().stream().map(CampaignMessage::getMessageGroupId)
                .distinct().collect(Collectors.toList());
        MultiValueMap<String, Locale> msgLocales = new LinkedMultiValueMap<>();
        Map<String, CampaignMessage> mappedMsgs = new HashMap<>();

        campaign.getCampaignMessages().forEach(cm -> {
            msgLocales.add(cm.getMessageGroupId(), cm.getLocale());
            mappedMsgs.put(cm.getMessageGroupId() + "__" + cm.getLocale(), cm);
        });

        return messageGroupIds.stream().filter(msgLocales::containsKey).map(msgId -> {
            CampaignMessageDTO cmDto = new CampaignMessageDTO();
            cmDto.setMessageId(msgId);
            Locale refLocale = msgLocales.get(msgId).get(0);
            CampaignMessage refMsg = mappedMsgs.get(msgId + "__" + refLocale);

            cmDto.setLinkedActionType(refMsg.getActionType());
            cmDto.setChannel(refMsg.getChannel());
            cmDto.setTags(refMsg.getTagList());
            cmDto.setVariation(refMsg.getVariation());

            cmDto.setMessages(msgLocales.get(msgId).stream().map(locale -> new MessageLanguagePair(locale, mappedMsgs.get(msgId + "__" + locale).getMessage()))
                    .collect(Collectors.toList()));

            return cmDto;
        }).collect(Collectors.toList());
    }

    private static Integer getMemberJoinedViaCampaign(Campaign campaign){
        Integer count = 0;
        if(campaign.getCampaignLogs() == null || campaign.getCampaignLogs().isEmpty()){
            return count;
        }
        for(CampaignLog campaignLog: campaign.getCampaignLogs()){
            if(campaignLog.getCampaignLogType().equals(CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP)){
                count ++;
            }
        }
        return count;
    }
}
