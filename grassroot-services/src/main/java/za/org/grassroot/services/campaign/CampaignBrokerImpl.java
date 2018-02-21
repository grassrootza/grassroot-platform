package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.domain.notification.CampaignSharingNotification;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.CampaignMessageRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.specifications.CampaignMessageSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);

    // use this a lot in campaign message handling
    private static final String LOCALE_SEP = "___";

    private static final List<String> SYSTEM_CODES = Arrays.asList("123", "911");

    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found.error";

    private final CampaignRepository campaignRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignLogRepository campaignLogRepository;

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final MediaFileBroker mediaFileBroker;

    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, CampaignMessageRepository campaignMessageRepository, CampaignLogRepository campaignLogRepository, GroupBroker groupBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, MediaFileBroker mediaFileBroker, CacheManager cacheManager, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.campaignMessageRepository = campaignMessageRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.groupBroker = groupBroker;
        this.userManager = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.permissionBroker = permissionBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.cacheManager = cacheManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign load(String campaignUid) {
        return campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
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
    public Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel){
        Objects.requireNonNull(campaignCode);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        if (campaign != null && storeLog) {
            Objects.requireNonNull(userUid);
            User user = userManager.load(userUid);
            persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_FOUND, campaign, channel, campaignCode));
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
    public Set<String> getActiveCampaignJoinTopics() {
        return campaignRepository.fetchAllActiveCampaignTags();
    }

    @Override
    @Transactional
    public void signPetition(String campaignUid, String userUid, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(userUid);

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_PETITION_SIGNED, campaign, channel, null);
        if (!StringUtils.isEmpty(campaign.getPetitionApi())) {
            log.info("firing at the petition API!", campaign.getPetitionApi());
        }
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(campaignLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Async
    @Override
    @Transactional
    public void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(sharingUserUid));

        // todo: check against budget, also increase amount spent
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        User targetUser = userManager.loadOrCreateUser(sharingNumber);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_SHARED, campaign, channel, sharingNumber);
        bundle.addLog(campaignLog);

        // we default to english, because even if sharing user is in another language, the person receiving might not be
        List<CampaignMessage> messages = findCampaignMessage(campaignUid, CampaignActionType.SHARE_SEND, Locale.ENGLISH);
        final String msg = !messages.isEmpty() ? messages.get(0).getMessage() : defaultTemplate;
        final String template = msg.replace(Broadcast.NAME_FIELD_TEMPLATE, "%1$s")
                .replace(Broadcast.INBOUND_FIELD_TEMPLATE, "%2$s");

        final String mergedMsg = String.format(template, user.getName(), "Dial *134*...");
        CampaignSharingNotification sharingNotification = new CampaignSharingNotification(targetUser, mergedMsg, campaignLog);

        log.info("alright, we're storing this notification: {}", sharingNotification);
        bundle.addNotification(sharingNotification);
        log.info("and here is the bundle: {}", bundle);

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserInCampaignMasterGroup(String campaignUid, String userUid) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(userUid));

        Group masterGroup = campaign.getMasterGroup();

        return masterGroup.hasMember(user);
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
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CREATED_IN_DB, newCampaign, null, null);
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

        CampaignMessage message = new CampaignMessage(createUser, campaign, CampaignActionType.OPENING, "testing_123", messageLocale, campaignMessage, interfaceType, assignment);
        if(messageTags != null) {
            message.addTags(messageTags);
        }
        campaign.getCampaignMessages().add(message);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(campaign.getCreatedByUser(), CampaignLogType.CAMPAIGN_MESSAGE_ADDED, campaign, null, null);
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

        // step one: find and map all the existing messages, if they exist
        Map<String, CampaignMessage> existingMessages = campaign.getCampaignMessages().stream()
                .collect(Collectors.toMap(cm -> cm.getMessageGroupId() + LOCALE_SEP + cm.getLocale(), cm -> cm));
        log.info("campaign already has: {}", existingMessages);

        // step two: explode each of the message DTOs into their separate locale-based messages, mapped by incoming ID and lang
        Map<String, CampaignMessage> explodedMessages = new LinkedHashMap<>();
        campaignMessages.forEach(cm -> explodedMessages.putAll(cm.getMessages().stream().collect(Collectors.toMap(
                    msg -> cm.getMessageId() + LOCALE_SEP + msg.getLanguage(),
                    msg -> updateExistingOrCreateNew(user, campaign, cm, msg, existingMessages)))));

        log.info("exploded message set: {}", explodedMessages);
        log.info("campaign messages look like: {}", campaignMessages);
        Map<String, CampaignMessageDTO> mappedMessages = campaignMessages.stream().collect(Collectors.toMap(
                CampaignMessageDTO::getMessageId, cm -> cm));

        // step two: go through each message, and wire up where to go next (better than earlier iterations, but can be cleaned)
        campaignMessages.forEach(cdto -> {
            cdto.getLanguages().forEach(lang -> {
                CampaignMessage cm = explodedMessages.get(cdto.getMessageId() + LOCALE_SEP + lang);
                log.info("wiring up message: {}", cm);
                cdto.getNextMsgIds().forEach(nextMsgId -> {
                    CampaignMessage nextMsg = explodedMessages.get(nextMsgId + LOCALE_SEP + lang);
                    log.info("for next msg {}, found CM: {}", nextMsgId + LOCALE_SEP + lang, nextMsg);
                    cm.addNextMessage(nextMsg.getUid(), mappedMessages.get(nextMsgId).getLinkedActionType());
                });
            });
        });

        Set<CampaignMessage> messages = new HashSet<>(explodedMessages.values());

        log.info("completed transformation, unpacked {} messages", messages.size());

        campaign.setCampaignMessages(messages);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGES_SET, campaign, null, null);
        persistCampaignLog(campaignLog);

        return updatedCampaign;
    }

    @Override
    public List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        return campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, linkedAction, locale)
        );
    }

    private CampaignMessage updateExistingOrCreateNew(User user, Campaign campaign, CampaignMessageDTO cm,
                                                      MessageLanguagePair msg, Map<String, CampaignMessage> existingMsgs) {
        boolean newMessage = existingMsgs.isEmpty()
                || !existingMsgs.containsKey(cm.getMessageId() + LOCALE_SEP + msg.getLanguage());
        log.info("does message with this key: {}, exist? {}", cm.getMessageId(), !newMessage);

        if (newMessage) {
            return new CampaignMessage(user, campaign, cm.getLinkedActionType(), cm.getMessageId(),
                    msg.getLanguage(), msg.getMessage(), cm.getChannel(), cm.getVariation());
        } else {
            CampaignMessage message = existingMsgs.get(cm.getMessageId() + LOCALE_SEP + msg.getLanguage());
            message.setActionType(cm.getLinkedActionType());
            message.setMessage(msg.getMessage());
            return message;
        }
    }

    @Override
    @Transactional
    public Campaign updateMasterGroup(String campaignUid, String groupUid, String userUid){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(groupUid);
        Group group = groupBroker.load(groupUid);
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        if(group != null && campaign != null){
            campaign.setMasterGroup(group);
            Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign, null,
                    groupUid);
            persistCampaignLog(campaignLog);
            return updatedCampaign;
        }
        LOG.error("No Campaign found for code = {}" + campaignUid);
        throw new CampaignNotFoundException(CAMPAIGN_NOT_FOUND_CODE);
    }

    @Override
    @Transactional
    public void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid, Instant endDate, String landingUrl, String petitionApi) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        validateUserCanModifyCampaign(user, campaign);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        if (!StringUtils.isEmpty(name) && !campaign.getName().trim().equalsIgnoreCase(name)) {
            campaign.setName(name);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_NAME_CHANGED, campaign, null, name));
        }

        if (!StringUtils.isEmpty(description)) {
            campaign.setDescription(description);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_DESC_CHANGED, campaign, null, description));
        }

        if (!StringUtils.isEmpty(mediaFileUid)) {
            campaign.setCampaignImage(mediaFileBroker.load(mediaFileUid));
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_IMG_CHANGED, campaign, null, mediaFileUid));
        }

        if (endDate != null) {
            campaign.setEndDateTime(endDate);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_END_CHANGED, campaign, null,
                    endDate.toString()));
        }

        if (!Objects.equals(landingUrl, campaign.getLandingUrl()) || !Objects.equals(petitionApi, campaign.getPetitionApi())) {
            campaign.setLandingUrl(landingUrl);
            campaign.setPetitionApi(petitionApi);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_URLS_CHANGED, campaign, null,
                    landingUrl + "; " + petitionApi));
        }

        AfterTxCommitTask task = () -> logsAndNotificationsBroker.storeBundle(bundle);
        eventPublisher.publishEvent(task);
    }

    @Override
    @Transactional
    public void reactivateCampaign(String userUid, String campaignUid, Instant newEndDate, String newCode) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        validateUserCanModifyCampaign(user, campaign);

        if (campaign.isActive()) {
            throw new IllegalArgumentException("Error! Campaign already active");
        }

        Set<String> activeCodes = getActiveCampaignCodes();
        if (!StringUtils.isEmpty(newCode) && !activeCodes.contains(newCode)) {
            campaign.setCampaignCode(newCode);
        } else if (activeCodes.contains(campaign.getCampaignCode())) {
            campaign.setCampaignCode(null);
        }

        campaign.setEndDateTime(newEndDate);

        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_REACTIVATED, campaign,
                null, newEndDate + ";" + newCode));
    }

    @Override
    @Transactional
    public void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudget, Set<CampaignMessage> sharingMessages) {

    }

    @Override
    @Transactional
    public Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        groupBroker.addMemberViaCampaign(user.getUid(),campaign.getMasterGroup().getUid(),campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP, campaign, channel, null);
        persistCampaignLog(campaignLog);
        return campaign;
    }

    @Override
    @Transactional
    public void changeCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessage> revisedMessages) {

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
