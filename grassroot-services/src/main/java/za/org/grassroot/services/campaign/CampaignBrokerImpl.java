package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.notification.CampaignBroadcastNotification;
import za.org.grassroot.core.domain.notification.CampaignResponseNotification;
import za.org.grassroot.core.domain.notification.CampaignSharingNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.BroadcastRepository;
import za.org.grassroot.core.repository.CampaignMessageRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.specifications.CampaignMessageSpecifications;
import za.org.grassroot.core.specifications.NotificationSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.criteria.Join;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    // for handling responses
    private static final String MORE_INFO_STRING = "1";
    private static final String MISTAKEN_JOIN_STRING = ""; // probably want to do this using

    // use this a lot in campaign message handling
    private static final String LOCALE_SEP = "___";

    private static final List<String> SYSTEM_CODES = Arrays.asList("123", "911");

    private final CampaignRepository campaignRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignStatsBroker campaignStatsBroker;

    private final GroupBroker groupBroker;
    private final AccountGroupBroker accountGroupBroker;

    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final MediaFileBroker mediaFileBroker;

    private final ApplicationEventPublisher eventPublisher;

    private final BroadcastRepository broadcastRepository;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, CampaignMessageRepository campaignMessageRepository, CampaignStatsBroker campaignStatsBroker, GroupBroker groupBroker, AccountGroupBroker accountGroupBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, MediaFileBroker mediaFileBroker, ApplicationEventPublisher eventPublisher, BroadcastRepository broadcastRepository){
        this.campaignRepository = campaignRepository;
        this.campaignMessageRepository = campaignMessageRepository;
        this.campaignStatsBroker = campaignStatsBroker;
        this.groupBroker = groupBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.userManager = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.permissionBroker = permissionBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.eventPublisher = eventPublisher;
        this.broadcastRepository = broadcastRepository;
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
        log.info("getting opening message, with input locale: {}, safe locale: {}", locale, safeLocale);
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

    @Async
    @Override
    @Transactional
    public void checkForAndTriggerCampaignText(String campaignUid, String userUid) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(userUid));
        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        log.info("checked for welcome message, found? : {}", template);
        if (template != null && !StringUtils.isEmpty(template.getSmsTemplate1())
                && campaign.outboundBudgetLeft() > 0) {
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_WELCOME_MESSAGE,
                    campaign, null, "Outbound engagement SMS");
            CampaignBroadcastNotification notification = new CampaignBroadcastNotification(
                    user, template.getSmsTemplate1(), template, null, campaignLog);
            notification.setSendOnlyAfter(Instant.now().plus(1, ChronoUnit.MINUTES));
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addLog(campaignLog);
            bundle.addNotification(notification);
            logsAndNotificationsBroker.storeBundle(bundle);
            campaign.addToOutboundSpent(campaign.getAccount().getFreeFormCost());
        }
    }

    @Override
    @Transactional
    public void haltCampaignWelcomeText(String campaignUid, String userUid) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(userUid));
        Specification<Notification> logFind = (root, query, cb) -> {
            Join<Notification, CampaignLog> logJoin = root.join(Notification_.campaignLog);
            query.distinct(true);
            return cb.and(
                    cb.equal(logJoin.get(CampaignLog_.campaign), campaign),
                    cb.equal(logJoin.get(CampaignLog_.user), user),
                    cb.equal(logJoin.get(CampaignLog_.campaignLogType), CampaignLogType.CAMPAIGN_WELCOME_MESSAGE));
        };

        Specification<Notification> notSent = (root, query, cb) -> cb.equal(root.get(Notification_.status),
                NotificationStatus.READY_FOR_SENDING);
        logsAndNotificationsBroker.abortNotificationSend(Specifications.where(notSent).and(logFind));
    }


    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsManagedByUser(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
        long startTime = System.currentTimeMillis();
        List<Campaign> campaigns = fetchCampaignsUserCanManage(user);
        log.info("time to execute cmapaign fetch: {} msecs", System.currentTimeMillis() - startTime);
        return campaigns;
    }

    private List<Campaign> fetchCampaignsUserCanManage(User user) {
        Specification<Campaign> createdByUser = (root, query, cb) -> cb.equal(root.get(Campaign_.createdByUser), user);
        Specification<Campaign> userIsOrganizerInGroup = (root, query, cb) -> {
            Join<Campaign, Group> masterGroupJoin = root.join(Campaign_.masterGroup);
            Join<Group, Membership> memberJoin = masterGroupJoin.join(Group_.memberships);
            Join<Membership, Role> roleJoin = memberJoin.join(Membership_.role);
            query.distinct(true);
            return cb.and(cb.equal(memberJoin.get(Membership_.user), user),
                    cb.equal(roleJoin.get(Role_.name), BaseRoles.ROLE_GROUP_ORGANIZER));
        };

        return campaignRepository.findAll(Specifications.where(createdByUser).or(userIsOrganizerInGroup), new Sort("createdDateTime"));
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
            campaignStatsBroker.clearCampaignStatsCache(campaign.getUid());
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
    public boolean isCodeTaken(String proposedCode, String campaignUid) {
        Objects.requireNonNull(proposedCode);
        if (campaignUid != null) {
            Campaign campaign = campaignRepository.findOneByUid(campaignUid);
            log.info("well, we're looking for campaign, it has code = {}, proposed = {}, is active = {}",
                    campaign.getCampaignCode(), proposedCode, campaign.isActive());
            if (campaign.isActive() && proposedCode.equals(campaign.getCampaignCode())) {
                return false; // because it's not taken by another campaign
            }
        }
        return campaignRepository.countByCampaignCodeAndEndDateTimeAfter(proposedCode, Instant.now()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getActiveCampaignJoinWords() {
        List<Object[]> tagsWithUids = campaignRepository.fetchAllActiveCampaignTags();
        return tagsWithUids.stream()
                .filter(object -> object[0] instanceof String && ((String) object[0]).startsWith(Campaign.PUBLIC_JOIN_WORD_PREFIX))
                .collect(Collectors.toMap(
                        object -> ((String) object[0]).substring(Campaign.PUBLIC_JOIN_WORD_PREFIX.length()),
                        object -> (String) object[1]));
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
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);
    }

    @Async
    @Override
    @Transactional
    public void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(sharingUserUid));

        if (campaign.getOutboundSpent() > campaign.getOutboundBudget()) {
            log.error("Error! Got to sharing even though campaign spent is above budget");
            return;
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        User targetUser = userManager.loadOrCreateUser(sharingNumber);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_SHARED, campaign, channel, sharingNumber);
        bundle.addLog(campaignLog);

        // we default to english, because even if sharing user is in another language, the person receiving might not be
        List<CampaignMessage> messages = findCampaignMessage(campaignUid, CampaignActionType.SHARE_SEND, Locale.ENGLISH);
        final String msg = !messages.isEmpty() ? messages.get(0).getMessage() : defaultTemplate;
        final String template = msg.replace(Broadcast.NAME_FIELD_TEMPLATE, "%1$s")
                .replace(Broadcast.ENTITY_FIELD_TEMPLATE, "%2$s")
                .replace(Broadcast.INBOUND_FIELD_TEMPLATE, "%3$s");

        final String mergedMsg = String.format(template, user.getName(), campaign.getName(), campaign.getCampaignCode());
        CampaignSharingNotification sharingNotification = new CampaignSharingNotification(targetUser, mergedMsg, campaignLog);

        log.info("alright, we're storing this notification: {}", sharingNotification);
        bundle.addNotification(sharingNotification);
        log.info("and here is the bundle: {}", bundle);

        logsAndNotificationsBroker.storeBundle(bundle);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);

        campaign.addToOutboundSpent(campaign.getAccount().getFreeFormCost());
    }

    long countCampaignShares(Campaign campaign) {
        Specifications<Notification> spec = Specifications.where(NotificationSpecifications.sharedForCampaign(campaign))
                .and(NotificationSpecifications.wasDelivered());
        return logsAndNotificationsBroker.countNotifications(spec);
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
    public Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate, Instant endDate, List<String> joinTopics, CampaignType campaignType, String url, boolean smsShare, long smsLimit, String imageKey){
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

        if (user.getPrimaryAccount() == null) {
            throw new NoPaidAccountException();
        }

        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,user, startDate, endDate,campaignType, url, user.getPrimaryAccount());
        newCampaign.setMasterGroup(masterGroup);

        if (smsShare) {
            newCampaign.setOutboundTextEnabled(true);
            newCampaign.setOutboundBudget(smsLimit * user.getPrimaryAccount().getFreeFormCost());
        }

        if(joinTopics != null && !joinTopics.isEmpty()){
            newCampaign.setJoinTopics(joinTopics.stream().map(String::trim).collect(Collectors.toList()));
            log.info("set campaign join topics ... {}", newCampaign.getJoinTopics());
        }

        Campaign persistedCampaign = campaignRepository.saveAndFlush(newCampaign);
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CREATED_IN_DB, newCampaign, null, null);
        persistCampaignLog(campaignLog);
        return persistedCampaign;
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

        campaign.setCampaignMessages(transformMessageDTOs(campaignMessages, campaign, user));

        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGES_SET, campaign, null, null);
        persistCampaignLog(campaignLog);

        return updatedCampaign;
    }

    private Set<CampaignMessage> transformMessageDTOs(Set<CampaignMessageDTO> campaignMessages, Campaign campaign, User user) {
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
        campaignMessages.forEach(cdto ->
                cdto.getLanguages().forEach(lang -> {
                    CampaignMessage cm = explodedMessages.get(cdto.getMessageId() + LOCALE_SEP + lang);
                    log.info("wiring up message: {}", cm);
                    cdto.getNextMsgIds().forEach(nextMsgId -> {
                        CampaignMessage nextMsg = explodedMessages.get(nextMsgId + LOCALE_SEP + lang);
                        log.info("for next msg {}, found CM: {}", nextMsgId + LOCALE_SEP + lang, nextMsg);
                        if (nextMsg != null) {
                            cm.addNextMessage(nextMsg.getUid(), mappedMessages.get(nextMsgId).getLinkedActionType());
                        }
                    });
                })
        );

        Set<CampaignMessage> messages = new HashSet<>(explodedMessages.values());
        log.info("completed transformation, unpacked {} messages", messages.size());
        return messages;
    }

    @Override
    public List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        // note: Java locale handling is horrific. 2-digit and 3-digit ISO strings generate locales that fail on equals
        // hence all of the below were failing, because we are using 3-digit (much more robust for several SA languages)
        // but almost everything passed in, constructed via Locale string constructor, was 2-digit in origin. Hence what follows.
        Locale safeLocale = locale == null ? Locale.ENGLISH : locale;
        Set<Locale> locales = campaignMessageRepository.selectLocalesForCampaign(campaign);
        Optional<Locale> maybeContains = locales.stream()
                .filter(lang -> lang.getISO3Language().equals(safeLocale.getISO3Language())).findAny();
        final Locale gettingOverJavaLocaleHorror = maybeContains.orElse(safeLocale);
        return campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, linkedAction, gettingOverJavaLocaleHorror)
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
        Group group = groupBroker.load(Objects.requireNonNull(groupUid));
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        if (group == null) {
            throw new IllegalArgumentException("Error! Group to set as master group cannot be null");
        }

        campaign.setMasterGroup(group);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign, null, groupUid);
        persistCampaignLog(campaignLog);
        return updatedCampaign;
    }

    @Override
    @Transactional
    public void setCampaignMessageText(String userUid, String campaignUid, String message) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign);

        log.info("setting a campaign welcome message ...");

        Account account = campaign.getAccount();
        if (account == null || !account.isBillPerMessage()) {
            throw new AccountLimitExceededException();
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        bundle.addLog(new AccountLog.Builder(campaign.getAccount())
                .accountLogType(AccountLogType.CAMPAIGN_WELCOME_ALTERED)
                .group(campaign.getMasterGroup())
                .user(user)
                .description(message)
                .build());

        bundle.addLog(new CampaignLog(user, CampaignLogType.WELCOME_MSG_ALTERED,
                campaign, null, message));

        Broadcast oldTemplate = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        log.info("did a welcome msg exist? : {}", oldTemplate);

        if (oldTemplate == null) {
            Broadcast template = Broadcast.builder()
                    .account(account)
                    .campaign(campaign)
                    .group(campaign.getMasterGroup())
                    .broadcastSchedule(BroadcastSchedule.ENGAGED_CAMPAIGN)
                    .createdByUser(user)
                    .active(true)
                    .creationTime(Instant.now())
                    .delayIntervalMillis(Duration.ofMinutes(1L).toMillis()) // maybe make it variable
                    .smsTemplate1(message)
                    .onlyUseFreeChannels(false)
                    .build();
            log.info("constructed welcome msg, saving to broadcasts ...");
            broadcastRepository.save(template);
        } else {
            log.info("old template exists, updating it ...");
            oldTemplate.setSmsTemplate1(message);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional
    public void clearCampaignMessageText(String userUid, String campaignUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign);

        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        log.info("halting welcome message ... did it exist? : {}", template);
        if (template != null) {
            template.setActive(false);

            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

            bundle.addLog(new AccountLog.Builder(campaign.getAccount())
                    .accountLogType(AccountLogType.CAMPAIGN_WELCOME_ALTERED)
                    .group(campaign.getMasterGroup())
                    .user(user)
                    .description("Removed message")
                    .build());

            bundle.addLog(new CampaignLog(user, CampaignLogType.WELCOME_MSG_ALTERED,
                    campaign, null, "Removed messaged"));
            logsAndNotificationsBroker.storeBundle(bundle);
        }
    }

    @Override
    public String getCampaignMessageText(String userUid, String campaignUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign); // since this is only used in modifying

        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);

        return template != null ? template.getSmsTemplate1() : null;
    }

    @Override
    @Transactional
    public void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid, boolean removeImage, Instant endDate, String newCode, String landingUrl, String petitionApi, List<String> joinTopics) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        validateUserCanModifyCampaign(user, campaign);

        log.info("campaign account: ", campaign.getAccount());

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
        } else if (removeImage) {
            campaign.setCampaignImage(null);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_IMG_REMOVED, campaign, null, null));
        }

        if (endDate != null) {
            campaign.setEndDateTime(endDate);
            if (campaign.isActive()) {
                bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_END_CHANGED, campaign, null, endDate.toString()));
            } else {
                log.info("campaign inactive, reactivating with end date: ", endDate);
                bundle.addLog(reactivateCampaign(campaign, user, endDate, newCode));
            }
        }

        if (!Objects.equals(landingUrl, campaign.getLandingUrl()) || !Objects.equals(petitionApi, campaign.getPetitionApi())) {
            campaign.setLandingUrl(landingUrl);
            campaign.setPetitionApi(petitionApi);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_URLS_CHANGED, campaign, null,
                    landingUrl + "; " + petitionApi));
        }

        if (joinTopics != null) {
            campaign.setJoinTopics(joinTopics);
        }

        log.info("campaign still has account? {}", campaign.getAccount());

        AfterTxCommitTask task = () -> logsAndNotificationsBroker.storeBundle(bundle);
        eventPublisher.publishEvent(task);
    }

    private CampaignLog reactivateCampaign(Campaign campaign, User user, Instant newEndDate, String newCode) {
        if (campaign.isActive()) {
            throw new IllegalArgumentException("Error! Campaign already active");
        }

        Set<String> activeCodes = getActiveCampaignCodes();
        if (!StringUtils.isEmpty(newCode) && !activeCodes.contains(newCode)) {
            campaign.setCampaignCode(newCode);
        } else if (activeCodes.contains(campaign.getCampaignCode())) {
            campaign.setCampaignCode(null);
        }

        return new CampaignLog(user, CampaignLogType.CAMPAIGN_REACTIVATED, campaign, null, newEndDate + ";" + newCode);
    }

    @Override
    @Transactional
    public void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudget, Set<CampaignMessageDTO> sharingMessages) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        validateUserCanModifyCampaign(user, campaign);

        boolean noSharingMsgs = sharingMessages.stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getLinkedActionType()));
        boolean noSharingMsgInCampaign = campaign.getCampaignMessages().stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getActionType()));
        if (smsEnabled && noSharingMsgs && noSharingMsgInCampaign) {
            throw new IllegalArgumentException("Attempting to enable with no prior or given sharing prompts");
        }

        campaign.setOutboundTextEnabled(smsEnabled);
        campaign.setOutboundBudget(smsBudget);

        campaign.addCampaignMessages(transformMessageDTOs(sharingMessages, campaign, user));

        persistCampaignLog(new CampaignLog(user, CampaignLogType.SHARING_SETTINGS_ALTERED, campaign, null,
                "Enabled: " + smsEnabled + ", budget = " + smsBudget));
    }

    @Override
    @Transactional
    public Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(userUid);

        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        final String masterGroupUid = campaign.getMasterGroup().getUid();

        if (accountGroupBroker.hasGroupWelcomeMessages(masterGroupUid))
            haltCampaignWelcomeText(campaignUid, userUid);

        groupBroker.addMemberViaCampaign(user.getUid(), masterGroupUid, campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP, campaign, channel, null);
        persistCampaignLog(campaignLog);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);

        return campaign;
    }

    @Override
    @Transactional
    public void setUserJoinTopic(String campaignUid, String userUid, String joinTopic, UserInterfaceType channel) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_TAGGED, campaign, channel,
                Campaign.JOIN_TOPIC_PREFIX + joinTopic);

        groupBroker.assignMembershipTopics(userUid, campaign.getMasterGroup().getUid(), userUid, Collections.singleton(joinTopic), true);
        persistCampaignLog(campaignLog);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserShared(String campaignUid, String userUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Specification<CampaignLog> forUser = (root, query, cb) -> cb.equal(root.get(CampaignLog_.user), user);
        Specification<CampaignLog> ofTypeSharing = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaignLogType),
                CampaignLogType.CAMPAIGN_SHARED);
        return logsAndNotificationsBroker.countCampaignLogs(Specifications.where(forUser).and(ofTypeSharing)) > 0;
    }

    @Override
    public String handleCampaignTextResponse(String campaignUid, String userUid, String reply, UserInterfaceType channel) {
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        String returnMsg = "";
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if (MORE_INFO_STRING.equals(reply)) {
            returnMsg = getTypeMessage(campaign, CampaignActionType.OPENING, user, channel)
                    + ". Reply with your name to join us";
        } else if (MISTAKEN_JOIN_STRING.equals(reply)) {
            // no message, just remove them from group
            groupBroker.unsubscribeMember(userUid, campaign.getMasterGroup().getUid());
            returnMsg = "Okay, we have reversed your join";
            logsAndNotificationsBroker.removeCampaignLog(user, campaign, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP);
        } else {
            log.info("Okay, adding user to group ...");
            returnMsg = getTypeMessage(campaign, CampaignActionType.EXIT_POSITIVE, user, channel)
                    + ". If this was a mistake, respond '0' and we will remove you";
            addUserToCampaignMasterGroup(campaignUid, userUid, channel);
        }

        if (!StringUtils.isEmpty(returnMsg)) {
            final String logMsg = String.format("Responded to user, number %s, with info %s", user.getName(), returnMsg).substring(0, 250);
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_REPLIED, campaign, channel, logMsg);
            bundle.addLog(campaignLog);
            bundle.addNotification(new CampaignResponseNotification(user, returnMsg, campaignLog));
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        return returnMsg;
    }

    private String getTypeMessage(Campaign campaign, CampaignActionType actionType, User user, UserInterfaceType channel) {
        List<CampaignMessage> messages = campaignMessageRepository.findAll(CampaignMessageSpecifications
                .ofTypeForCampaign(campaign, actionType, user.getLocale(), channel, MessageVariationAssignment.DEFAULT));
        return messages.isEmpty() ? "" : messages.get(0).getMessage();
    }

    @Override
    @Transactional
    public void updateCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessageDTO> revisedMessages) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign);

        CampaignType oldType = campaign.getCampaignType();
        campaign.setCampaignType(newType);

        if (revisedMessages != null && !revisedMessages.isEmpty()) {
            setCampaignMessages(userUid, campaignUid, revisedMessages);
        }

        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_TYPE_CHANGED, campaign, null,
                "From: " + oldType + ", to = " + newType));
    }

    @Override
    @Transactional
    public void setCampaignImage(String userUid, String campaignUid, String mediaFileKey) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        log.info("user = {}, campaign = {}", user, campaign);
        validateUserCanModifyCampaign(user, campaign);

        MediaFileRecord record = mediaFileBroker.load(MediaFunction.CAMPAIGN_IMAGE, mediaFileKey);
        campaign.setCampaignImage(record);
    }

    @Override
    @Transactional
    public void endCampaign(String userUid, String campaignUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        log.info("user = {}, campaign = {}", user, campaign);
        validateUserCanModifyCampaign(user, campaign);

        Instant priorEndDate = campaign.getEndDateTime();
        campaign.setEndDateTime(Instant.now());

        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_DEACTIVATED, campaign, null,
                "Prior end date: " + priorEndDate));
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
        if (!campaign.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, campaign.getMasterGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

}
