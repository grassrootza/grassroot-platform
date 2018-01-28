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
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.CampaignMessageNotFoundException;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);

    private static final List<String> SYSTEM_CODES = Arrays.asList("123", "911");

    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found.error";
    private static final String CAMPAIGN_MESSAGE_NOT_FOUND_CODE = "campaign.message.not.found.error";

    private final CampaignRepository campaignRepository;

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, GroupBroker groupBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.groupBroker = groupBroker;
        this.userManager = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.permissionBroker = permissionBroker;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign load(String userUid, String campaignUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(campaignUid);

        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        if (!campaign.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, campaign.getMasterGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }

        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsCreatedByUser(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
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
            User user = userManager.load(userUid);
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
            User user = userManager.load(userUid);
            CampaignLogType campaignLogType = (campaign != null) ? CampaignLogType.CAMPAIGN_FOUND : CampaignLogType.CAMPAIGN_NOT_FOUND;
            persistCampaignLog(new CampaignLog(null, campaignLogType, campaign, campaignName));
        }
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getActiveCampaignCodes() {
        Set<String> campaignCodes = campaignRepository.fetchAllActiveCampaignCodes();
        campaignCodes.addAll(SYSTEM_CODES);
        return campaignCodes;
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
        User user = userManager.findByInputNumber(phoneNumber);
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
    public Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate, Instant endDate, List<String> campaignTags, CampaignType campaignType, String url){
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(masterGroupUid);
        Objects.requireNonNull(campaignType);

        User user = userManager.load(userUid);
        Group masterGroup = groupBroker.load(masterGroupUid);

        if (getActiveCampaignCodes().contains(campaignCode)) {
            throw new CampaignCodeTakenException();
        }

        if (masterGroup == null) {
            throw new GroupNotFoundException();
        }

        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,user, startDate, endDate,campaignType, url);
        newCampaign.setMasterGroup(masterGroup);

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
    public Campaign addCampaignMessage(String campaignUid, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, UserInterfaceType interfaceType, User createUser, List<String> messageTags){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(campaignMessage);
        Objects.requireNonNull(messageLocale);
        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(createUser);
        Objects.requireNonNull(assignment);

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        CampaignMessage message = new CampaignMessage(createUser, campaign, messageLocale, campaignMessage, interfaceType, assignment, CampaignActionType.OPENING);
        if(messageTags != null) {
            message.addTags(messageTags);
        }
        campaign.getCampaignMessages().add(message);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED, campaign);
        persistCampaignLog(campaignLog);
        return updatedCampaign;
    }

    @Override
    @Transactional
    public Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(campaignMessages);

        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        validateUserCanModifyCampaign(user, campaign);

        // step one: explode each of the message DTOs into their separate locale-based messages, mapped by incoming ID and lang
        Map<String, CampaignMessage> explodedMessages = new LinkedHashMap<>();
        campaignMessages.forEach(cm -> explodedMessages.putAll(cm.getMessages().stream().collect(Collectors.toMap(
                    msg -> cm.getMessageId() + "___" + msg.getLanguage(),
                    msg -> new CampaignMessage(user, campaign, msg.getLanguage(), msg.getMessage(),
                            cm.getChannel(), cm.getVariation(), cm.getLinkedActionType())))));

        log.info("exploded message set: ", explodedMessages);

        Map<String, CampaignMessageDTO> mappedMessages = campaignMessages.stream().collect(Collectors.toMap(
                CampaignMessageDTO::getMessageId, cm -> cm));

        // step two: go through each message, and wire up where to go next (better than earlier iterations, but can be cleaned)
        campaignMessages.forEach(cdto -> {
            cdto.getLanguages().forEach(lang -> {
                CampaignMessage cm = explodedMessages.get(cdto.getMessageId() + "___" + lang);
                log.info("wiring up message: {}", cm);
                cdto.getNextMsgIds().forEach(nextMsgId -> {
                    CampaignMessage nextMsg = explodedMessages.get(nextMsgId + "___" + lang);
                    log.info("for next msg {}, found CM: {}", nextMsgId + "___" + lang, nextMsg);
                    cm.addNextMessage(nextMsg.getUid(), mappedMessages.get(nextMsgId).getLinkedActionType());
                });
            });
        });

        Set<CampaignMessage> messages = new HashSet<>(explodedMessages.values());

        log.info("completed transformation, unpacked {} messages", messages.size());

        campaign.setCampaignMessages(messages);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGES_SET, campaign);
        persistCampaignLog(campaignLog);

        return updatedCampaign;
    }

    private Set<CampaignMessage> transformFromDTO(CampaignMessageDTO messageDTO, Campaign campaign, User user) {
        return messageDTO.getMessages().stream().map(msg ->
            new CampaignMessage(user, campaign, msg.getLanguage(), msg.getMessage(), messageDTO.getChannel(), messageDTO.getVariation(),
                    messageDTO.getLinkedActionType())
        ).collect(Collectors.toSet());
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
    public Campaign updateMasterGroup(String campaignCode, String groupUid, String userUid){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(groupUid);
        Group group = groupBroker.load(groupUid);
        User user = userManager.load(userUid);
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
    public Campaign addUserToCampaignMasterGroup(String campaignCode,String phoneNumber){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(phoneNumber);
        User user = userManager.loadOrCreateUser(phoneNumber);
        Campaign campaign = getCampaignDetailsByCode(campaignCode, user.getUid(), false);
        groupBroker.addMemberViaCampaign(user.getUid(),campaign.getMasterGroup().getUid(),campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,campaign);
        persistCampaignLog(campaignLog);
        return campaign;
    }


    @Override
    @Transactional
    public Campaign addActionToCampaignMessage(String campaignUid, String parentMessageUid,CampaignActionType actionType, String actionMessage, Locale actionMessageLocale, MessageVariationAssignment actionMessageAssignment, UserInterfaceType interfaceType, User createUser, Set<String> actionMessageTags){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(parentMessageUid);
        Objects.requireNonNull(actionType);
        Objects.requireNonNull(actionMessage);
        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(createUser);
        Objects.requireNonNull(actionMessageLocale);

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        if(campaign != null && campaign.getCampaignMessages() != null && !campaign.getCampaignMessages().isEmpty()){
            boolean messageFound = false;
            for(CampaignMessage parentMessage : campaign.getCampaignMessages()) {
                if (parentMessage.getUid().trim().equalsIgnoreCase(parentMessageUid.trim())) {
                    CampaignMessage messageForAction = new CampaignMessage(createUser, campaign, actionMessageLocale, actionMessage, interfaceType, actionMessageAssignment, CampaignActionType.OPENING);
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
        LOG.error("No Campaign found for code = {}" + campaignUid);
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

    private void validateUserCanModifyCampaign(User user, Campaign campaign) {
        // for the moment
        if (!campaign.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, campaign.getMasterGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

}
