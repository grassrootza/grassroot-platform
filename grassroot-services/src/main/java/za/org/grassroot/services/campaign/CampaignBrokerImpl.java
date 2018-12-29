package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.Notification_;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.Role_;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignLog_;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.campaign.Campaign_;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Group_;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.group.Membership_;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.notification.CampaignSharingNotification;
import za.org.grassroot.core.domain.notification.NotificationStatus;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignMessageRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.specifications.CampaignMessageSpecifications;
import za.org.grassroot.core.specifications.NotificationSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.criteria.Join;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    // use this a lot in campaign message handling
    private static final String LOCALE_SEP = "___";

    private static final List<String> SYSTEM_CODES = Arrays.asList("123", "911");

    private final CampaignRepository campaignRepository;

    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignStatsBroker campaignStatsBroker;

    private final GroupBroker groupBroker;
    private final AccountFeaturesBroker accountFeaturesBroker;

    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final MediaFileBroker mediaFileBroker;

    private final ApplicationEventPublisher eventPublisher;

    private CacheManager cacheManager;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, CampaignMessageRepository campaignMessageRepository, CampaignStatsBroker campaignStatsBroker, GroupBroker groupBroker, AccountFeaturesBroker accountFeaturesBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, MediaFileBroker mediaFileBroker, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.campaignMessageRepository = campaignMessageRepository;
        this.campaignStatsBroker = campaignStatsBroker;
        this.groupBroker = groupBroker;
        this.accountFeaturesBroker = accountFeaturesBroker;
        this.userManager = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.permissionBroker = permissionBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign load(String campaignUid) {
        return campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateUserCanViewFull(String userUid, String campaignUid) {
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        validateUserCanModifyCampaign(user, campaign);
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
        Locale safeLocale = locale == null ? new Locale("eng") : locale;
        UserInterfaceType safeChannel = channel == null ? UserInterfaceType.USSD : channel;
        MessageVariationAssignment safeVariation = variation == null ? MessageVariationAssignment.DEFAULT: variation;

        final String cacheKey = String.format("%s-%s-%s-%s", campaignUid, safeLocale, safeChannel, safeVariation);
        final Cache openingMsgCache = cacheManager.getCache("campaign_opening_message");

        if (openingMsgCache.isKeyInCache(cacheKey)) {
            Element cacheElement = openingMsgCache.get(cacheKey);
            if (cacheElement != null && cacheElement.getObjectValue() != null) {
                log.info("Opening message in cache, avoiding DB, retrieving and exiting");
                return (CampaignMessage) openingMsgCache.get(cacheKey).getObjectValue();
            }
        }

        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        log.debug("getting opening message for {}, with input locale: {}, safe locale: {}, input channel: {}, safe channel: {}, " +
                "input variation: {}, safe variation: {}", campaign.getName(), locale, safeLocale, channel, safeChannel, variation, safeVariation);

        List<CampaignMessage> messages = campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, CampaignActionType.OPENING, safeLocale, safeChannel, safeVariation));
        CampaignMessage message = selectFromList(messages);
        if (message != null)
            openingMsgCache.put(new Element(cacheKey, message));
        return message;
    }

    private CampaignMessage selectFromList(List<CampaignMessage> messages) {
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
    @Transactional
    public void recordEngagement(String campaignUid, String userUid, UserInterfaceType channel, String logDesc) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        User user = userManager.load(Objects.requireNonNull(userUid));
        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_FOUND, campaign, channel, StringUtils.truncate(logDesc, 250)));
        campaignStatsBroker.clearCampaignStatsCache(campaign.getUid());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage loadCampaignMessage(String messageUid, String userUid) {
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(userUid);
        return campaignMessageRepository.findOneByUid(messageUid);
    }

    @Override
    public CampaignMessage findCampaignMessage(String campaignUid, String priorMsgUid, CampaignActionType takenAction) {
        CampaignMessage priorMsg = campaignMessageRepository.findOneByUid(priorMsgUid);
        log.info("prior message : {}, prior msg uid: {}, prior message next msgs: {}", priorMsg, priorMsgUid, priorMsg == null ? "null" : priorMsg.getNextMessages());
        Optional<String> thisMsgUid = priorMsg.getNextMessages().entrySet().stream().filter(entry -> entry.getValue().equals(takenAction))
                .findFirst().map(Map.Entry::getKey);
        return thisMsgUid.map(campaignMessageRepository::findOneByUid)
                .orElseThrow(() -> new IllegalArgumentException("Error! Prior message does not have taken action as one of its possible actions"));
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

    @Override
    public List<Campaign> getCampaignsOnAccount(String accountUid, boolean activeOnly) {
        Account account = accountFeaturesBroker.load(accountUid);
        Specification<Campaign> spec = (root, query, cb) -> cb.equal(root.get(Campaign_.account), account);
        if (activeOnly) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get(Campaign_.endDateTime), Instant.now()));
        }
        return campaignRepository.findAll(spec);
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

        return campaignRepository.findAll(Specification.where(createdByUser).or(userIsOrganizerInGroup), Sort.by("createdDateTime"));
    }

    @Override
    @Transactional
    public Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel){
        Objects.requireNonNull(campaignCode);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        if (campaign != null && storeLog) {
            recordEngagement(campaign.getUid(), userUid, channel, campaignCode);
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

        if (SYSTEM_CODES.contains(proposedCode))
            return true;

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
    public Campaign findCampaignByJoinWord(String joinWord, String userId, UserInterfaceType channel) {
        // first, just check for straight code (in case we use that in future)
        Campaign campaign = getCampaignByCampaignCode(joinWord);
        if (campaign != null) {
            recordEngagement(campaign.getUid(), userId, channel, joinWord);
            return campaign;
        }

        // todo: will definitely want to cache these soon
        Map<String, String> activeWords = getActiveCampaignJoinWords();
        final String trimmed = joinWord.trim().toLowerCase();
        campaign = activeWords.containsKey(trimmed) ? campaignRepository.findOneByUid(activeWords.get(trimmed)) : null;

        if (campaign != null) {
            recordEngagement(campaign.getUid(), userId, channel, joinWord);
            return campaign;
        }

        return null;
    }

    @Override
    @Transactional
    public List<Campaign> broadSearchForCampaign(String userId, String searchTerm) {
        log.info("Searching for campaigns with names that look like : {}", searchTerm);
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, false);
        return campaignRepository.findCampaignsWithNamesIncluding(tsQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getActiveCampaignJoinWords() {
        List<Object[]> tagsWithUids = campaignRepository.fetchAllActiveCampaignTags();
        return tagsWithUids.stream()
                .filter(object -> object[0] instanceof String && ((String) object[0]).startsWith(Campaign.PUBLIC_JOIN_WORD_PREFIX))
                .collect(Collectors.toMap(
                        object -> ((String) object[0]).substring(Campaign.PUBLIC_JOIN_WORD_PREFIX.length()).toLowerCase(),
                        object -> (String) object[1]));
    }

    @Override
    public boolean isTextJoinWordTaken(String joinWord, String campaignUid) {
        Map<String, String> activeWords = getActiveCampaignJoinWords();
        final String trimmed = joinWord.trim().toLowerCase();
        if (campaignUid != null && activeWords.containsKey(trimmed) && activeWords.get(trimmed).equals(campaignUid)) {
            return false; // since it is 'taken' but by this campaign
        } else {
            Set<String> campaignWords = getActiveCampaignJoinWords().keySet().stream().map(String::toLowerCase).collect(Collectors.toSet());
            return campaignWords.contains(joinWord.toLowerCase());
        }
    }

    @Override
    @Transactional
    public void signPetition(String campaignUid, String userUid, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(userUid);

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_PETITION_SIGNED, campaign, channel, null);
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

        log.info("Assembling sharing message");
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        User targetUser = userManager.loadOrCreateUser(sharingNumber, UserInterfaceType.USSD);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_SHARED, campaign, channel, sharingNumber);
        bundle.addLog(campaignLog);

        // we default to english, because even if sharing user is in another language, the person receiving might not be
        List<CampaignMessage> messages = findCampaignMessage(campaignUid, CampaignActionType.SHARE_SEND, Locale.ENGLISH, channel);
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

    @Override
    public void recordUserSentMedia(String campaignUid, String userUid, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        User user = userManager.load(userUid);

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_SENT_MEDIA, campaign, channel, null);
        createAndStoreCampaignLog(campaignLog);
    }

    long countCampaignShares(Campaign campaign) {
        Specification<Notification> spec = Specification.where(NotificationSpecifications.sharedForCampaign(campaign))
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

        Campaign newCampaign = new Campaign(campaignName, campaignCode, description, user, startDate, endDate,campaignType, url, user.getPrimaryAccount());
        newCampaign.setMasterGroup(masterGroup);

        if (smsShare) {
            newCampaign.setOutboundTextEnabled(true);
            newCampaign.setOutboundBudget(smsLimit * user.getPrimaryAccount().getFreeFormCost());
        }

        if(joinTopics != null && !joinTopics.isEmpty()){
            newCampaign.setJoinTopics(joinTopics.stream().map(String::trim).collect(Collectors.toList()));
            log.info("set campaign join topics ... {}", newCampaign.getJoinTopics());
        }

        log.info("Persisting new campaign: {}", newCampaign);
        Campaign persistedCampaign = campaignRepository.saveAndFlush(newCampaign);
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CREATED_IN_DB, newCampaign, null, null);
        persistCampaignLog(campaignLog);
        return persistedCampaign;
    }

    @Override
    public Campaign loadForModification(String userUid, String campaignUid) {
        Campaign campaign = load(campaignUid);
        User user = userManager.load(userUid);
        validateUserCanModifyCampaign(user, campaign);
        return campaign;
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
    public List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        // note: Java locale handling is horrific. 2-digit and 3-digit ISO strings generate locales that fail on equals
        // hence all of the below were failing, because we are using 3-digit (much more robust for several SA languages)
        // but almost everything passed in, constructed via Locale string constructor, was 2-digit in origin. Hence what follows.
        Locale safeLocale = locale == null ? Locale.ENGLISH : locale;
        Set<Locale> locales = campaignMessageRepository.selectLocalesForCampaign(campaign);
        Optional<Locale> maybeContains = locales.stream().filter(lang -> lang.getISO3Language().equals(safeLocale.getISO3Language())).findAny();
        final Locale gettingOverJavaLocaleHorror = maybeContains.orElse(safeLocale);
        List<CampaignMessage> campaignMessages = campaignMessageRepository.findAll(CampaignMessageSpecifications
                .ofTypeForCampaign(campaign, linkedAction, gettingOverJavaLocaleHorror));
        log.debug("Prior to channel filter, have messages: {}", campaignMessages);
        return filterForChannelOrDefault(campaignMessages, channel, UserInterfaceType.USSD);
    }

    private List<CampaignMessage> filterForChannelOrDefault(List<CampaignMessage> messages, UserInterfaceType preferredChannel, UserInterfaceType defaultChannel) {
        List<CampaignMessage> filteredMessages = messages.stream().filter(message ->
                preferredChannel == null || preferredChannel.equals(message.getChannel())).collect(Collectors.toList());
        if (!filteredMessages.isEmpty()) {
            log.debug("Found messages for channel {}, returning: {}", preferredChannel, filteredMessages);
            return filteredMessages;
        } else {
            List<CampaignMessage> defaultMsgs = messages.stream()
                    .filter(message -> defaultChannel == null || defaultChannel.equals(message.getChannel())).collect(Collectors.toList());
            log.debug("found for default channel {}, messages {}", defaultChannel, defaultMsgs);
            return defaultMsgs;
        }
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
    public void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid, boolean removeImage, Instant endDate, String newCode, String newTextWord, String landingUrl, String petitionApi, List<String> joinTopics) {
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

        if (!Objects.equals(landingUrl, campaign.getLandingUrl()) || !Objects.equals(petitionApi, campaign.getPetitionApiUrl())) {
            campaign.setLandingUrl(landingUrl);
            campaign.setPetitionApiUrl(petitionApi);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_URLS_CHANGED, campaign, null,
                    landingUrl + "; " + petitionApi));
        }

        if (joinTopics != null) {
            campaign.setJoinTopics(joinTopics);
        }

        if (!StringUtils.isEmpty(newTextWord) && !isTextJoinWordTaken(newTextWord, campaignUid)) {
            campaign.setPublicJoinWord(newTextWord);
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
    public void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudgetNumberTexts, Set<CampaignMessageDTO> sharingMessages) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        validateUserCanModifyCampaign(user, campaign);

        boolean noSharingMsgs = sharingMessages.stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getLinkedActionType()));
        boolean noSharingMsgInCampaign = campaign.getCampaignMessages().stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getActionType()));
        if (smsEnabled && noSharingMsgs && noSharingMsgInCampaign) {
            throw new IllegalArgumentException("Attempting to enable with no prior or given sharing prompts");
        }

        campaign.setOutboundTextEnabled(smsEnabled);
        campaign.setOutboundBudget(smsBudgetNumberTexts * campaign.getAccount().getFreeFormCost());

        campaign.addCampaignMessages(transformMessageDTOs(sharingMessages, campaign, user));

        persistCampaignLog(new CampaignLog(user, CampaignLogType.SHARING_SETTINGS_ALTERED, campaign, null,
                "Enabled: " + smsEnabled + ", budget = " + smsBudgetNumberTexts));
    }

    @Override
    @Transactional
    public Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(userUid);

        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        final String masterGroupUid = campaign.getMasterGroup().getUid();

        if (accountFeaturesBroker.hasGroupWelcomeMessages(masterGroupUid))
            haltCampaignWelcome(campaign, user);

        groupBroker.addMemberViaCampaign(user.getUid(), masterGroupUid, campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP, campaign, channel, null);
        persistCampaignLog(campaignLog);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);

        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean doesGroupHaveActiveCampaign(String groupUid) {
        return campaignRepository.countByMasterGroupUidAndEndDateTimeAfter(groupUid, Instant.now()) > 0;
    }

    private void haltCampaignWelcome(final Campaign campaign, final User user) {
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
        logsAndNotificationsBroker.abortNotificationSend(Specification.where(notSent).and(logFind));
    }

    @Override
    @Transactional
    public void setUserJoinTopic(String campaignUid, String userUid, String joinTopic, UserInterfaceType channel) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_TAGGED, campaign, channel,
                Campaign.JOIN_TOPIC_PREFIX + joinTopic);

        groupBroker.assignMembershipTopics(userUid, campaign.getMasterGroup().getUid(), false, Collections.singleton(userUid), Collections.singleton(joinTopic), true);
        persistCampaignLog(campaignLog);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserEngaged(String campaignUid, String userUid) {
        final User user = userManager.load(Objects.requireNonNull(userUid));
        Specification<CampaignLog> forUser = (root, query, cb) -> cb.equal(root.get(CampaignLog_.user), user);
        Specification<CampaignLog> specs = Specification.where(forUser)
                .and((root, query, cb) -> cb.equal(root.get(CampaignLog_.campaignLogType), CampaignLogType.CAMPAIGN_FOUND));
        return logsAndNotificationsBroker.countCampaignLogs(specs) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserShared(String campaignUid, String userUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Specification<CampaignLog> forUser = (root, query, cb) -> cb.equal(root.get(CampaignLog_.user), user);
        Specification<CampaignLog> ofTypeSharing = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaignLogType),
                CampaignLogType.CAMPAIGN_SHARED);
        return logsAndNotificationsBroker.countCampaignLogs(Specification.where(forUser).and(ofTypeSharing)) > 0;
    }

    @Override
    public boolean hasUserSentMedia(String campaignUid, String userUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Specification<CampaignLog> forUser = (root, query, cb) -> cb.equal(root.get(CampaignLog_.user), user);
        Specification<CampaignLog> ofTypeSharing = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaignLogType),
                CampaignLogType.CAMPAIGN_USER_SENT_MEDIA);
        return logsAndNotificationsBroker.countCampaignLogs(Specification.where(forUser).and(ofTypeSharing)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public String getMessageOfType(String campaignUid, CampaignActionType actionType, String userUid, UserInterfaceType channel) {
        final User user = userManager.load(userUid);
        List<CampaignMessage> messages = findCampaignMessage(campaignUid, actionType, user.getLocale(), UserInterfaceType.USSD);
        log.info("found a message? : {}", messages);
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

    @Override
    @Transactional
    public void updateCampaignDefaultLanguage(String userUid, String campaignUid, Locale defaultLanguage) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        validateUserCanModifyCampaign(user, campaign);

        log.info("Setting campaign {} to language: {}", campaign.getName(), defaultLanguage);

        campaign.setDefaultLanguage(defaultLanguage);

        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_LANG_CHANGED, campaign, null,
                defaultLanguage.getLanguage()));
    }

    @Override
    public List<MediaFileRecord> fetchInboundCampaignMediaDetails(String userUid, String campaignUid) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign);

        return mediaFileBroker.fetchInboundMediaRecordsForCampaign(campaignUid);
    }

    // Hibernate / Hikari should be caching this but because of sheer volume on peak, adding an extra layer of caching
    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);

        Cache campaignCache = cacheManager.getCache("campaign_lookup_codes");
        if (campaignCache != null && campaignCache.isKeyInCache(campaignCode)) {
            Element campaignStored = campaignCache.get(campaignCode);
            if (campaignStored != null && campaignStored.getObjectValue() != null) {
                log.info("Found campaign code in cache, returning without hitting DB");
                return (Campaign) campaignStored.getObjectValue();
            }
        }

        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode, Instant.now());
        if (campaign != null && campaignCache != null) {
            log.info("Found a campaign, sticking it in cache for next day");
            campaignCache.put(new Element(campaignCode, campaign));
        }
        return campaign;
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
