package za.org.grassroot.services.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.user.UserManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);
    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found";
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final UserManager userManager;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignLogRepository campaignLogRepository, UserManager userManager){
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.userManager = userManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsCreatedByUser(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        return campaignRepository.findByCreatedByUser(user, new Sort("createdDateTime"));
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByCode(String campaignCode){
        return getCampaignByCampaignCode(campaignCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByName(String campaignName){
        return getCampaignByCampaignName(campaignName);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment){
        return findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment){
        return findMessagesByCampaignNameAndVariation(campaignName,assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
        return  CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignName, MessageVariationAssignment assignment, Locale locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage>messageSet = findMessagesByCampaignNameAndVariation(campaignName,assignment);
        return CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet,messageTag);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariation(campaignName, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet, messageTag);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, String userUid, Instant startDate, Instant endDate, List<String> campaignTags){
        User user = userManager.load(userUid);
        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,user, startDate, endDate);
        if(campaignTags != null && !campaignTags.isEmpty()){
            newCampaign.getTagList().addAll(campaignTags);
        }
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser().getUid(), CampaignLogType.CREATED_IN_DB,newCampaign);
        campaignLogRepository.saveAndFlush(campaignLog);
        return campaignRepository.saveAndFlush(newCampaign);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Long groupId, Instant startDate, Instant endDate, List<String> campaignTags){
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        if(group != null){
            Campaign newCampaign = new Campaign(campaignName, campaignCode, description,createUser, startDate, endDate);
            newCampaign.setMasterGroup(group);
            if(campaignTags != null && !campaignTags.isEmpty()){
                newCampaign.getTagList().addAll(campaignTags);
            }
            CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED,newCampaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(newCampaign);
        }
        throw new GroupNotFoundException();
    }

    @Override
    @Transactional
    public Campaign addCampaignMessage(String campaignCode, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(campaignMessage);
        Objects.requireNonNull(messageLocale);
        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(createUser);
        Objects.requireNonNull(assignment);
        Objects.requireNonNull(messageTags);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null){
            CampaignMessage message = new CampaignMessage(campaignMessage, createUser, assignment, messageLocale,interfaceType);
            message.getTagList().addAll(messageTags);
            campaign.getCampaignMessages().add(message);
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED,campaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(campaign);
        }
        LOG.error("No Campaign found for code {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign addCampaignTags(String campaignCode, List<String> tags){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(tags);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null){
            campaign.getTagList().addAll(tags);
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_TAG_ADDED,campaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(campaign);
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign linkCampaignToMasterGroup(String campaignCode, Long groupId){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(group != null && campaign != null){
            campaign.setMasterGroup(group);
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(campaign);
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign addActionsToCampaignMessage(String campaignCode, String messageUid, List<CampaignActionType> campaignActionTypes){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(campaignActionTypes);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getUid().equalsIgnoreCase(messageUid)){
                    message.getCampaignMessageActionSet().addAll(CampaignUtil.createCampaignMessageActionSet(message,campaignActionTypes));
                    break;
                }
            }
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_MESSAGE_ACTION_ADDED,campaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(campaign);
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign addMessageActionsToCampaignMessage(String campaignCode, String messageUid, List<CampaignMessageAction> campaignMessageActionList){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(campaignMessageActionList);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            for(CampaignMessage message: campaign.getCampaignMessages()){
                if(message.getUid().equalsIgnoreCase(messageUid)){
                    message.getCampaignMessageActionSet().addAll(campaignMessageActionList);
                    break;
                }
            }
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser().getUid(), CampaignLogType.CAMPAIGN_MESSAGE_ACTION_ADDED,campaign);
            campaignLogRepository.saveAndFlush(campaignLog);
            return campaignRepository.saveAndFlush(campaign);
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }


    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignByTag(String tag){
        Objects.requireNonNull(tag);
        return campaignRepository.findActiveCampaignByTag(tag);
    }

    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        return campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode, Instant.now());
    }

    private Campaign getCampaignByCampaignName(String campaignName){
        Objects.requireNonNull(campaignName);
        return campaignRepository.findByCampaignNameAndEndDateTimeAfter(campaignName, Instant.now());
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
