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
import za.org.grassroot.core.repository.CampaignMessageRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.specifications.CampaignMessageSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
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
    private final CampaignMessageRepository campaignMessageRepository;

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, CampaignMessageRepository campaignMessageRepository, GroupBroker groupBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.campaignMessageRepository = campaignMessageRepository;
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
    public Set<Locale> getCampaignLanguages(String campaignUid) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        return new HashSet<>(campaignMessageRepository.selectLocalesForCampaign(campaign));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage getOpeningMessage(String campaignUid, Locale locale, UserInterfaceType channel, MessageVariationAssignment variation) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        Locale safeLocale = locale == null ? Locale.ENGLISH : locale;
        UserInterfaceType safeChannel = channel == null ? UserInterfaceType.USSD : channel;
        MessageVariationAssignment safeVariation = variation == null ? MessageVariationAssignment.DEFAULT: variation;
        List<CampaignMessage> messages = campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, CampaignActionType.OPENING, safeLocale, safeChannel, safeVariation));
        if (messages.isEmpty()) {
            log.error("Error! Cannot find opening message for campaign");
            return null;
        } else if (messages.size() > 1) {
            log.error("Error! More than one opening message active for campaign");
            return messages.get(0);
        } else {
            return messages.get(0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage loadCampaignMessage(String messageUid, String userUid) {
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(userUid); // todo: add in logging here
        return campaignMessageRepository.findOneByUid(messageUid);
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
        if (campaign != null && storeLog) {
            Objects.requireNonNull(userUid);
            User user = userManager.load(userUid);
            persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_FOUND, campaign, campaignCode));
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
    public Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
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

    // leave this here for a while as may come in handy in future, although not quite yet
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
