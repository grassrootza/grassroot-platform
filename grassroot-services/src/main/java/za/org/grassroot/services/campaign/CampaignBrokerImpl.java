package za.org.grassroot.services.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.exception.CampaignMessageNotFoundException;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);
    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found.error";
    private static final String CAMPAIGN_MESSAGE_NOT_FOUND_CODE = "campaign.message.not.found.error";

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final CampaignLogRepository campaignLogRepository;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignLogRepository campaignLogRepository){
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignLogRepository = campaignLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsCreatedByUser(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return campaignRepository.findByCreatedByUser(user, new Sort("createdDateTime"));
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, campaignCode));
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByName(String campaignName){
        Campaign campaign = getCampaignByCampaignName(campaignName);
        CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, campaignName));
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage getCampaignMessageByCampaignCodeAndActionType(String campaignCode, MessageVariationAssignment assignment,UserInterfaceType channel, CampaignActionType actionType, String phoneNumber, Locale locale){
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode,assignment, channel, locale);
        String searchValue = createSearchValue(campaignCode,assignment,null,null);
        if(messageSet != null && !messageSet.isEmpty()){
            for(CampaignMessage message: messageSet){
                if(message.getCampaignMessageActionSet() != null && !message.getCampaignMessageActionSet().isEmpty()){
                    for(CampaignMessageAction messageAction: message.getCampaignMessageActionSet()){
                        if(messageAction.getActionType().equals(actionType)){
                            campaignLogRepository.saveAndFlush(new CampaignLog(phoneNumber, CampaignLogType.CAMPAIGN_MESSAGE_FOUND , searchValue));
                            return  messageAction.getActionMessage();
                        }
                    }
                }
            }
        }
        campaignLogRepository.saveAndFlush(new CampaignLog(phoneNumber, CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND, searchValue));
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(campaignName);
        Objects.requireNonNull(assignment);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName,assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,null,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, searchValue));
        return messageSet;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type){
        Objects.requireNonNull(locale);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode,assignment, type, locale);
        String searchValue = createSearchValue(campaignCode,assignment,locale,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, searchValue));
        return  messageSet;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignName, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type){
        Objects.requireNonNull(locale);
        Set<CampaignMessage>messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName,assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,locale,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, searchValue));
        return messageSet;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode, assignment, type, locale);
        String searchValue = createSearchValue(campaignCode,assignment,null,messageTag);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, searchValue));
        return CampaignUtil.processCampaignMessagesByTag(messageSet,messageTag);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName, assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,null,messageTag);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, searchValue));
        return CampaignUtil.processCampaignMessagesByTag(messageSet, messageTag);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, String userUid, Instant startDate, Instant endDate, List<String> campaignTags){
        User user = userRepository.findOneByUid(userUid);
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
        LOG.error("No Campaign found for code = {}" + campaignCode);
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
    public Campaign linkCampaignToMasterGroup(String campaignCode, Long groupId, String phoneNumber){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        User user = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(group != null && campaign != null){
            campaign.setMasterGroup(group);
            CampaignLog campaignLog = new CampaignLog(user.getUid(), CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign);
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
        if(campaign == null){
            LOG.error("No Campaign found for code = {}" + campaignCode);
            throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
        }
        if(campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
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
        LOG.error("No Campaign message found for uid = {}" + messageUid);
        throw new CampaignMessageNotFoundException(CAMPAIGN_MESSAGE_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign addMessageActionsToCampaignMessage(String campaignCode, String messageUid, List<CampaignMessageAction> campaignMessageActionList){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(campaignMessageActionList);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign == null){
            LOG.error("No Campaign found for code = {}" + campaignCode);
            throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
        }
        if(campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
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
        LOG.error("No Campaign message found for message uid = {}" + messageUid);
        throw new CampaignMessageNotFoundException(CAMPAIGN_MESSAGE_NOT_FOUND_CODE);
    }


    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignByTag(String tag){
        Objects.requireNonNull(tag);
        Campaign campaign = campaignRepository.findActiveCampaignByTag(tag);
        CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
        campaignLogRepository.saveAndFlush(new CampaignLog(null, campaignLogType, tag));
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

    private Set<CampaignMessage> findMessagesByCampaignCodeAndVariationAndUserInterfaceType(String campaignCode, MessageVariationAssignment assignment, UserInterfaceType channel, Locale locale){
        Objects.requireNonNull(assignment);
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(channel);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        return CampaignUtil.processCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocale(campaign,assignment, channel, locale);
    }

    private Set<CampaignMessage> findMessagesByCampaignNameAndVariationAndUserInterfaceType(String campaignName, MessageVariationAssignment assignment, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(assignment);
        Campaign campaign = getCampaignByCampaignName(campaignName);
        return CampaignUtil.processCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocale(campaign, assignment,type, locale);
    }

    private String createSearchValue(String value, MessageVariationAssignment assignment, Locale locale, String tag){
        String AND = " and ";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" search by ");
        stringBuilder.append((value != null)? value:"");
        stringBuilder.append((assignment != null)? AND.concat(assignment.name()):"");
        stringBuilder.append((locale != null)? AND.concat(locale.getDisplayLanguage()):"");
        stringBuilder.append((tag != null)? AND.concat(tag):"");
        return stringBuilder.toString();
    }

}
