package za.org.grassroot.webapp.controller.rest.campaign;


import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.webapp.model.web.CampaignWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CampaignWebUtil {

    private CampaignWebUtil(){}

    public static CampaignWrapper createCampaignWrapper(Campaign campaign){
        CampaignWrapper wrapper = new CampaignWrapper();
        wrapper.setName(campaign.getCampaignName());
        wrapper.setCode(campaign.getCampaignCode());
        wrapper.setDescription(campaign.getCampaignDescription());
        wrapper.setEndDate(campaign.getStartDateTime().toString()); //format
        wrapper.setStartDate(campaign.getStartDateTime().toString());//format
        wrapper.setGroupName((campaign.getMasterGroup() != null)? campaign.getMasterGroup().getGroupName(): null);
        wrapper.setType(campaign.getCampaignType().name());
        wrapper.setUrl(campaign.getUrl());
        wrapper.setTags((campaign.getTagList() != null) ? new HashSet<>(campaign.getTagList()): null);
        wrapper.setUserUid(campaign.getCreatedByUser().getDisplayName());
        wrapper.setTotalUsers((campaign.getMasterGroup() != null && campaign.getMasterGroup().getMembers() != null)? campaign.getMasterGroup().getMembers().size() : 0);
        ///wrapper.setUserRole(campaign.getCreatedByUser().); check with Luke. Which Role does create use
        return wrapper;

    }
    public static List<CampaignWrapper> createCampaignWrapperList(List<Campaign> campaigns){
        List<CampaignWrapper> campaignWrapperList = new ArrayList<>();
        if(campaigns == null || campaigns.isEmpty()){
            return campaignWrapperList;
        }
        for(Campaign campaign : campaigns){
            campaignWrapperList.add(createCampaignWrapper(campaign));
        }
        return campaignWrapperList;
    }
}
