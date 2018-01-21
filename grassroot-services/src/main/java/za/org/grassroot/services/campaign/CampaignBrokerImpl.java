package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.exception.CampaignMessageNotFoundException;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.*;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);
    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found.error";
    private static final String CAMPAIGN_MESSAGE_NOT_FOUND_CODE = "campaign.message.not.found.error";

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupBroker groupBroker;
    private final UserManagementService userManagementService;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, UserRepository userRepository, GroupRepository groupRepository,
                              GroupBroker groupBroker,UserManagementService userManagementService,LogsAndNotificationsBroker logsAndNotificationsBroker, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupBroker = groupBroker;
        this.userManagementService = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsCreatedByUser(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return campaignRepository.findByCreatedByUser(user, new Sort("createdDateTime"));
    }

    @Override
    public List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid) {
        return campaignRepository.findByMasterGroupUid(groupUid, new Sort("createdDateTime"));
    }

    @Override
    @Transactional
    public Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog){
        Objects.requireNonNull(campaignCode);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        if (storeLog) {
            Objects.requireNonNull(userUid);
            User user = userRepository.findOneByUid(userUid);
            CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
            persistCampaignLog(new CampaignLog(user, campaignLogType,campaign, campaignCode));
        }
        return campaign;
    }

    @Override
    @Transactional
    public Campaign getCampaignDetailsByName(String campaignName, String userUid, boolean storeLog){
        Campaign campaign = getCampaignByCampaignName(campaignName);
        if (storeLog) {
            Objects.requireNonNull(userUid);
            User user = userRepository.findOneByUid(userUid);
            CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
            persistCampaignLog(new CampaignLog(null, campaignLogType, campaign, campaignName));
        }
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getCampaignTags() {
        return campaignRepository.fetchAllActiveCampaignTags();
    }

    @Override
    @Transactional
    public CampaignMessage getCampaignMessageByCampaignCodeAndActionType(String campaignCode, MessageVariationAssignment assignment,UserInterfaceType channel, CampaignActionType actionType, String phoneNumber, Locale locale){
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode,assignment, channel, locale);
        String searchValue = createSearchValue(campaignCode,assignment,null,null);
        User user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
        if(messageSet != null && !messageSet.isEmpty()){
            for(CampaignMessage message: messageSet){
                if(message.getCampaignMessageActionSet() != null && !message.getCampaignMessageActionSet().isEmpty()){
                    for(CampaignMessageAction messageAction: message.getCampaignMessageActionSet()){
                        if(messageAction.getActionType().equals(actionType)){
                            persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGE_FOUND , searchValue));
                            return  messageAction.getActionMessage();
                        }
                    }
                }
            }
        }
        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND, searchValue));
        return null;
    }

    @Override
    @Transactional
    public Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(campaignName);
        Objects.requireNonNull(assignment);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName,assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,null,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, searchValue));
        return messageSet;
    }

    @Override
    @Transactional
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type){
        Objects.requireNonNull(locale);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode,assignment, type, locale);
        String searchValue = createSearchValue(campaignCode,assignment,locale,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, searchValue));
        return  messageSet;
    }

    @Override
    @Transactional
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignName, MessageVariationAssignment assignment, Locale locale, UserInterfaceType type){
        Objects.requireNonNull(locale);
        Set<CampaignMessage>messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName,assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,locale,null);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, searchValue));
        return messageSet;
    }

    @Override
    @Transactional
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariationAndUserInterfaceType(campaignCode, assignment, type, locale);
        String searchValue = createSearchValue(campaignCode,assignment,null,messageTag);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, searchValue));
        return CampaignUtil.processCampaignMessagesByTag(messageSet,messageTag);
    }

    @Override
    @Transactional
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag, UserInterfaceType type, Locale locale){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariationAndUserInterfaceType(campaignName, assignment, type, locale);
        String searchValue = createSearchValue(campaignName,assignment,null,messageTag);
        CampaignLogType campaignLogType = (messageSet != null && !messageSet.isEmpty()) ? CampaignLogType.CAMPAIGN_MESSAGE_FOUND : CampaignLogType.CAMPAIGN_MESSAGE_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, searchValue));
        return CampaignUtil.processCampaignMessagesByTag(messageSet, messageTag);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, String userUid, Instant startDate, Instant endDate, List<String> campaignTags, CampaignType campaignType, String url){
        User user = userRepository.findOneByUid(userUid);
        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,user, startDate, endDate,campaignType, url);
        if(campaignTags != null && !campaignTags.isEmpty()){
            log.info("setting campaign tags ... {}", campaignTags);
            newCampaign.setTags(campaignTags);
        }
        Campaign perstistedCampaign = campaignRepository.saveAndFlush(newCampaign);
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CREATED_IN_DB, newCampaign);
        persistCampaignLog(campaignLog);
        return perstistedCampaign;
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Long groupId, Instant startDate, Instant endDate, List<String> campaignTags, CampaignType campaignType){
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        if(group != null){
            Campaign newCampaign = new Campaign(campaignName, campaignCode, description,createUser, startDate, endDate, campaignType, null);
            newCampaign.setMasterGroup(group);
            if(campaignTags != null && !campaignTags.isEmpty()){
                newCampaign.getTagList().addAll(campaignTags);
            }
            Campaign createdCampaign = campaignRepository.saveAndFlush(newCampaign);
            CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED,newCampaign);
            persistCampaignLog(campaignLog);
            return createdCampaign;
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
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null){
            CampaignMessage message = new CampaignMessage(campaignMessage, createUser, assignment, messageLocale,interfaceType,campaign);
            if(messageTags != null && !messageTags.isEmpty()) {
                message.getTagList().addAll(messageTags);
            }
            campaign.getCampaignMessages().add(message);
            Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED,campaign);
            persistCampaignLog(campaignLog);
            return updatedCampaign;
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
            List<String> campaignTags = new ArrayList<>();
            campaignTags.addAll(campaign.getTagList());
            campaignTags.addAll(tags);
            campaign.setTags(campaignTags.toArray(new String[campaignTags.size()]));
            Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
            CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_TAG_ADDED,campaign);
            persistCampaignLog(campaignLog);
            return updatedCampaign;
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign linkCampaignToMasterGroup(String campaignCode, String groupUid, String userUid){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        User user = userRepository.findOneByUid(userUid);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(group != null && campaign != null){
            campaign.setMasterGroup(group);
            Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign);
            persistCampaignLog(campaignLog);
            return updatedCampaign;
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public Campaign createMasterGroupForCampaignAndLinkCampaign(String campaignCode, String groupName, String userUid){
        Objects.requireNonNull(groupName);
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(campaignCode);
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.save(new Group(groupName.trim(),user));
        return linkCampaignToMasterGroup(campaignCode,group.getUid(),userUid);
    }

    @Override
    @Transactional
    public Campaign addUserToCampaignMasterGroup(String campaignCode,String phoneNumber){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(phoneNumber);
        User user = userManagementService.loadOrCreateUser(phoneNumber);
        Campaign campaign = getCampaignDetailsByCode(campaignCode, user.getUid(), false);
        groupBroker.addMemberViaCampaign(user.getUid(),campaign.getMasterGroup().getUid(),campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,campaign);
        persistCampaignLog(campaignLog);
        return campaign;
    }


    @Override
    @Transactional
    public Campaign getCampaignByTag(String tag){
        Objects.requireNonNull(tag);
        Campaign campaign = campaignRepository.findActiveCampaignByTag(tag);
        CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
        persistCampaignLog(new CampaignLog(null, campaignLogType, tag));
        return campaign;
    }

    @Override
    @Transactional
    public Campaign addActionToCampaignMessage(String campaignCode, String parentMessageUid,CampaignActionType actionType, String actionMessage, Locale actionMessageLocale, MessageVariationAssignment actionMessageAssignment, UserInterfaceType interfaceType, User createUser, Set<String> actionMessageTags){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(parentMessageUid);
        Objects.requireNonNull(actionType);
        Objects.requireNonNull(actionMessage);
        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(createUser);
        Objects.requireNonNull(actionMessageLocale);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            boolean messageFound = false;
            for(CampaignMessage parentMessage : campaign.getCampaignMessages()) {
                if (parentMessage.getUid().trim().equalsIgnoreCase(parentMessageUid.trim())) {
                    CampaignMessage messageForAction = new CampaignMessage(actionMessage, createUser, actionMessageAssignment, actionMessageLocale, interfaceType, campaign);
                    if (actionMessageTags != null) {
                        messageForAction.setTags(new ArrayList<>(actionMessageTags));
                    }
                    CampaignMessageAction action = new CampaignMessageAction(parentMessage, messageForAction, actionType, createUser);
                    parentMessage.getCampaignMessageActionSet().add(action);
                    messageFound = true;
                    break;
                }
            }
            if(!messageFound){
                LOG.error("No Campaign message found for uid = {}" + parentMessageUid);
                throw  new CampaignMessageNotFoundException(CAMPAIGN_MESSAGE_NOT_FOUND_CODE);
            }
            Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
            CampaignLog campaignLog = new CampaignLog(createUser, CampaignLogType.CAMPAIGN_MESSAGE_ACTION_ADDED, campaign);
            persistCampaignLog(campaignLog);
            return updatedCampaign;
        }
        LOG.error("No Campaign found for code = {}" + campaignCode);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);

    }

    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        return campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode, Instant.now());
    }

    private Campaign getCampaignByCampaignName(String campaignName){
        Objects.requireNonNull(campaignName);
        return campaignRepository.findByNameAndEndDateTimeAfter(campaignName, Instant.now());
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
        stringBuilder.append((locale !=  null)? AND.concat(locale.getDisplayLanguage()):"");
        stringBuilder.append((tag != null)? AND.concat(tag):"");
        return stringBuilder.toString();
    }

    private void createAndStoreCampaignLog(CampaignLog campaignLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(campaignLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private void persistCampaignLog(CampaignLog accountLog) {
        AfterTxCommitTask task = () -> createAndStoreCampaignLog(accountLog);
        eventPublisher.publishEvent(task);
    }

}
