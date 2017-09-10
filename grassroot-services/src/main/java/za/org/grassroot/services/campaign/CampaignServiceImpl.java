package za.org.grassroot.services.campaign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.services.campaign.util.CampaignUtil;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Service
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;

    @Autowired
    public CampaignServiceImpl(CampaignRepository campaignRepository){
        this.campaignRepository = campaignRepository;
    }

    @Override
    public Campaign getCampaignDetailsByCode(String campaignCode){
        return getCampaignByCampaignCode(campaignCode);
    }

    @Override
    public Campaign getCampaignDetailsByName(String campaignName){
        return getCampaignByCampaignName(campaignName);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment){
        return findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment){
        return findMessagesByCampaignNameAndVariation(campaignName,assignment);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
        return  CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignName, MessageVariationAssignment assignment, String locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage>messageSet = findMessagesByCampaignNameAndVariation(campaignName,assignment);
        return CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet,messageTag);
    }

    @Override
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariation(campaignName, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet, messageTag);
    }

    @Override
    public Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Instant startDate, Instant endDate){
        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,createUser, startDate, endDate);
        return campaignRepository.saveAndFlush(newCampaign);
    }



    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        return campaignRepository.findByCampaignCodeAndEndDateTimeBefore(campaignCode, Instant.now());
    }

    private Campaign getCampaignByCampaignName(String campaignName){
        Objects.requireNonNull(campaignName);
        return campaignRepository.findBycampaignNameAndEndDateTimeBefore(campaignName, Instant.now());
    }

    private Set<CampaignMessage> findMessagesByCampaignCodeAndVariation(String campaignCode, MessageVariationAssignment assignment){
        Objects.requireNonNull(assignment);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        return CampaignUtil.processCampaignMessageByAssignmentVariation(campaign,assignment);
    }

    private Set<CampaignMessage> findMessagesByCampaignNameAndVariation(String campaignName, MessageVariationAssignment assignment){
        Objects.requireNonNull(assignment);
        Campaign campaign = getCampaignByCampaignName(campaignName);
        return CampaignUtil.processCampaignMessageByAssignmentVariation(campaign, assignment);
    }

}
