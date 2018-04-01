package za.org.grassroot.services.broadcasts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.notification.CampaignBroadcastNotification;
import za.org.grassroot.core.domain.notification.GroupBroadcastNotification;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.socialmedia.*;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.NotificationSpecifications.*;

@Service @Slf4j
public class BroadcastBrokerImpl implements BroadcastBroker {

    @Value("${grassroot.broadcast.mocksm.enabled:false}")
    private boolean mockSocialMediaBroadcasts;

    // note, make configurable, possibly, and/or use i18n
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;

    private final GroupRepository groupRepository;
    private final CampaignRepository campaignRepository;
    private final MembershipRepository membershipRepository;

    // for heavy lifting on filtering, and for getting event members
    private final GroupFetchBroker groupFetchBroker;
    private final TaskBroker taskBroker;

    private final MessagingServiceBroker messagingServiceBroker;
    private final SocialMediaBroker socialMediaBroker;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final AccountLogRepository accountLogRepository;

    private final Environment environment;

    private PasswordTokenService tokenService;

    @Autowired
    public BroadcastBrokerImpl(BroadcastRepository broadcastRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignRepository campaignRepository, MembershipRepository membershipRepository, GroupFetchBroker groupFetchBroker, TaskBroker taskBroker, MessagingServiceBroker messagingServiceBroker, SocialMediaBroker socialMediaBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, AccountLogRepository accountLogRepository, Environment environment) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.groupFetchBroker = groupFetchBroker;
        this.taskBroker = taskBroker;
        this.messagingServiceBroker = messagingServiceBroker;
        this.socialMediaBroker = socialMediaBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.accountLogRepository = accountLogRepository;
        this.environment = environment;
    }

    @Autowired
    public void setTokenService(PasswordTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public BroadcastInfo fetchGroupBroadcastParams(String userUid, String groupUid) {
        User user = userRepository.findOneByUid(userUid);
        Account account = user.getPrimaryAccount();

        BroadcastInfo.BroadcastInfoBuilder builder = BroadcastInfo.builder();
        builder.broadcastId(UIDGenerator.generateId());

        builder.mergeFields(Arrays.asList(Broadcast.NAME_FIELD_TEMPLATE, Broadcast.CONTACT_FIELD_TEMPALTE,
                Broadcast.PROVINCE_FIELD_TEMPLATE, Broadcast.DATE_FIELD_TEMPLATE));

        if (account !=null && account.getFreeFormCost() > 0) {
            builder.isSmsAllowed(true).smsCostCents(account.getFreeFormCost());
        } else {
            builder.isSmsAllowed(false);
        }

        ManagedPagesResponse fbStatus = socialMediaBroker.getManagedFacebookPages(userUid);
        builder.isFbConnected(fbStatus.isUserConnectionValid())
                .facebookPages(fbStatus.getManagedPages());

        ManagedPage twitterAccount = socialMediaBroker.isTwitterAccountConnected(userUid);
        builder.isTwitterConnected(twitterAccount != null)
                .twitterAccount(twitterAccount);

        List<Campaign> associatedCampaigns = campaignRepository.findByMasterGroupUid(groupUid, new Sort("createdDateTime"));
        if (associatedCampaigns != null) {
            builder.campaignNamesUrls(associatedCampaigns
                .stream().filter(Campaign::isActiveWithUrl).collect(Collectors.toMap(Campaign::getName, Campaign::getLandingUrl)));
        }

        // or for campaign, extract somehow
        Group group = groupRepository.findOneByUid(groupUid);
        builder.allMemberCount(membershipRepository.count((root, query, cb) -> cb.equal(root.get("group"), (group))));

        return builder.build();
    }

    @Override
    @Transactional
    public String sendGroupBroadcast(BroadcastComponents bc) {
        User user = userRepository.findOneByUid(bc.getUserUid());
        Account account = user.getPrimaryAccount();

        if (account == null) {
            throw new NoPaidAccountException();
        }

        // prepersist is not working as reliably as hoped (or is happening in wrong sequence), to generate this if blank, hence
        final String uid = StringUtils.isEmpty(bc.getBroadcastId()) ? UIDGenerator.generateId() : bc.getBroadcastId();
        log.info("creating broadcast, incoming ID {}, set id: {}", bc.getBroadcastId(), uid);

        Broadcast broadcast = Broadcast.builder()
                .uid(uid)
                .createdByUser(user)
                .account(account)
                .title(bc.getTitle())
                .broadcastSchedule(bc.getBroadcastSchedule())
                .scheduledSendTime(bc.getScheduledSendTime())
                .build();

        if (bc.isCampaignBroadcast()) {
            broadcast.setCampaign(campaignRepository.findOneByUid(bc.getCampaignUid()));
        } else {
            broadcast.setGroup(groupRepository.findOneByUid(bc.getGroupUid()));
        }

        wireUpFilters(bc, broadcast);

        LogsAndNotificationsBundle bundle = bc.isImmediateBroadcast() ?
                sendImmediateBroadcast(bc, broadcast) :
                storeScheduledBroadcast(bc, broadcast);

        broadcastRepository.saveAndFlush(broadcast);

        logsAndNotificationsBroker.storeBundle(bundle);

        return broadcast.getUid();
    }

    @Override
    @Transactional
    public String sendTaskBroadcast(String userUid, String taskUid, TaskType taskType, boolean onlyPositiveResponders, String message) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        TaskDTO task = taskBroker.load(userUid, taskUid, taskType);

        Broadcast broadcast = Broadcast.builder()
                .createdByUser(user)
                .account(user.getPrimaryAccount())
                .title(taskType + ": " + task.getTitle())
                .broadcastSchedule(BroadcastSchedule.IMMEDIATE)
                .smsTemplate1(message)
                .scheduledSendTime(Instant.now())
                .build();

        broadcast.setGroup(groupRepository.findOneByUid(task.getAncestorGroupUid()));
        broadcast.setTask(taskUid, taskType, onlyPositiveResponders);

        broadcastRepository.saveAndFlush(broadcast);

        logsAndNotificationsBroker.storeBundle(generateGroupBroadcastBundle(broadcast));

        return broadcast.getUid();
    }

    private void wireUpFilters(BroadcastComponents bc, Broadcast broadcast) {
        if (!StringUtils.isEmpty(bc.getFilterNamePhoneOrEmail()))
            broadcast.setNameFilter(bc.getFilterNamePhoneOrEmail());

        if (bc.getProvinces() != null && !bc.getProvinces().isEmpty())
            broadcast.setProvinces(bc.getProvinces());

        if (bc.getTopics() != null && !bc.getTopics().isEmpty())
            broadcast.setTopics(new HashSet<>(bc.getTopics()));

        if (bc.getTaskTeams() != null && !bc.getTaskTeams().isEmpty())
            broadcast.setTaskTeams(bc.getTaskTeams());

        if (bc.getAffiliations() != null && !bc.getAffiliations().isEmpty())
            broadcast.setAffiliations(bc.getAffiliations());

        if (bc.getJoinMethods() != null && !bc.getJoinMethods().isEmpty())
            broadcast.setJoinMethods(bc.getJoinMethods());

        if (bc.getJoinDateCondition() != null) {
            broadcast.setJoinDateCondition(bc.getJoinDateCondition());
            broadcast.setJoinDateValue(bc.getJoinDate());
        }

        if (bc.getFilterLanguages() != null && !bc.getFilterLanguages().isEmpty()) {
            broadcast.setLanguages(bc.getFilterLanguages());
        }
    }

    private LogsAndNotificationsBundle sendImmediateBroadcast(BroadcastComponents bc, Broadcast broadcast) {
        recordShortMessageContent(bc, broadcast);
        recordEmailContent(bc.getEmail(), broadcast);

        if (bc.getFacebookPosts() != null) {
            log.info("sending an FB post, from builder: {}", bc.getFacebookPosts());
            recordFbPosts(bc.getFacebookPosts(), broadcast);
            List<GenericPostResponse> fbResponses = socialMediaBroker.postToFacebook(bc.getFacebookPosts());
            broadcast.setFbPostSucceeded(mockSocialMediaBroadcasts || fbResponses.stream().anyMatch(GenericPostResponse::isPostSuccessful));
        }

        if (bc.getTwitterPostBuilder() != null) {
            recordTwitterPost(bc.getTwitterPostBuilder(), broadcast);
            GenericPostResponse twResponse = socialMediaBroker.postToTwitter(bc.getTwitterPostBuilder());
            if ((twResponse != null && twResponse.isPostSuccessful()) || mockSocialMediaBroadcasts) {
                broadcast.setTwitterSucceeded(true);
            }
        }

        broadcast.setSentTime(Instant.now());

        return bc.isCampaignBroadcast() ?
                generateCampaignBroadcastBundle(broadcast) :
                generateGroupBroadcastBundle(broadcast);
    }

    private LogsAndNotificationsBundle storeScheduledBroadcast(BroadcastComponents bc, Broadcast broadcast) {
        log.info("storing a broadcast, scheduled for: {}, time: {}", broadcast.getBroadcastSchedule(),
                broadcast.getScheduledSendTime());

        recordShortMessageContent(bc, broadcast);
        recordEmailContent(bc.getEmail(), broadcast);
        recordFbPosts(bc.getFacebookPosts(), broadcast);
        recordTwitterPost(bc.getTwitterPostBuilder(), broadcast);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        AccountLog accountLog = new AccountLog.Builder(broadcast.getAccount())
                .user(broadcast.getCreatedByUser())
                .group(broadcast.getGroup())
                .broadcast(broadcast)
                .accountLogType(AccountLogType.BROADCAST_SCHEDULED)
                .build();
        bundle.addLog(accountLog);
        return bundle;
    }

    private void sendScheduledBroadcast(Broadcast bc) {

        try {
            if (bc.hasFbPost() && !bc.getFbPostSucceeded()) {
                List<GenericPostResponse> fbResponse = socialMediaBroker.postToFacebook(extractFbFromBroadcast(bc));
                bc.setFbPostSucceeded(fbResponse.stream().anyMatch(GenericPostResponse::isPostSuccessful));
            }

            if (bc.hasTwitterPost() && !bc.getTwitterSucceeded()) {
                GenericPostResponse twResponse = socialMediaBroker.postToTwitter(extractTweetFromBroadcast(bc));
                bc.setTwitterSucceeded(twResponse.isPostSuccessful());
            }
        } catch (NullPointerException e) {
            log.error("Strange null point error in sending scedhuled broadcast");
        }

        try {
            LogsAndNotificationsBundle bundle = bc.getCampaign() == null ? generateGroupBroadcastBundle(bc) : generateCampaignBroadcastBundle(bc);
            logsAndNotificationsBroker.storeBundle(bundle);
        } catch (Exception e) {
            // todo : mail broadcast creator?
            log.error("Exception in storing scheduled broadcast notifications!", e);
        }

        bc.setSentTime(Instant.now());
    }

    private void recordShortMessageContent(BroadcastComponents bc, Broadcast broadcast) {
        broadcast.setSmsTemplate1(bc.getShortMessage());
        broadcast.setOnlyUseFreeChannels(bc.isUseOnlyFreeChannels());
        broadcast.setSkipSmsIfEmail(bc.getEmail() != null && bc.isSkipSmsIfEmail());
    }

    private void recordEmailContent(EmailBroadcast email, Broadcast broadcast) {
        if (email != null) {
            broadcast.setEmailContent(email.getContent());
            log.info("email delivery route: {}", email.getDeliveryRoute());
            broadcast.setEmailDeliveryRoute(email.getDeliveryRoute());
            broadcast.setEmailAttachments(email.getAttachmentFileRecordUids());
        }
    }

    private void handleBroadcastEmails(Broadcast broadcast, ActionLog actionLog, Set<User> recipients,
                                       LogsAndNotificationsBundle bundle) {
        if (!recipients.isEmpty()) {

            Set<String> recipientUids = recipients.stream().map(User::getUid).collect(Collectors.toSet());
            EmailBroadcast emailBc = EmailBroadcast.builder()
                    .broadcastUid(broadcast.getUid())
                    .subject(broadcast.getTitle())
                    .content(broadcast.getEmailContent())
                    .deliveryRoute(broadcast.getEmailDeliveryRoute())
                    .attachmentFileRecordUids(broadcast.getEmailAttachments())
                    .build();

            emailBc.setFromFieldsIfEmpty(broadcast.getCreatedByUser());
            GrassrootEmail email = emailBc.toGrassrootEmail();
            email.setToUserUids(recipientUids);
            email.setGroupUid(broadcast.getGroup().getUid());
            log.info("email, with base ID = {}", email.getBaseId());

            log.info("generating unsubscribe tokens ....");
            tokenService.generateResponseTokens(recipientUids, broadcast.getGroup().getUid(), null);

            messagingServiceBroker.sendEmail(email);

            bundle.addNotifications(recordEmailNotifications(broadcast, recipients, emailBc, actionLog));
        }
    }

    // for counting / auditing purposes
    private Set<Notification> recordEmailNotifications(Broadcast broadcast, Set<User> recipients, EmailBroadcast emailBc, ActionLog actionLog) {
        boolean isCampaign = broadcast.getCampaign() != null;
        return recipients.stream().map(user -> {
            Notification n = isCampaign ? new CampaignBroadcastNotification(user, emailBc.getContent(), broadcast, broadcast.getEmailDeliveryRoute(), (CampaignLog) actionLog) :
                    new GroupBroadcastNotification(user, emailBc.getContent(), broadcast, broadcast.getEmailDeliveryRoute(), (GroupLog) actionLog);
            n.setSendAttempts(1);
            n.setStatus(NotificationStatus.DELIVERED);
            return n;
        }).collect(Collectors.toSet());
    }

    private void recordFbPosts(List<FBPostBuilder> posts, Broadcast broadcast) {
        if (posts != null && !posts.isEmpty()) {
            FBPostBuilder post = posts.iterator().next();
            String fbIds = posts.stream().map(FBPostBuilder::getFacebookPageId).collect(Collectors.joining(","));
            broadcast.setFacebookPageId(fbIds);
            broadcast.setFacebookPost(post.getMessage());
            broadcast.setFacebookImageCaption(post.getImageCaption());
            broadcast.setFacebookImageKey(post.getImageKey());
            broadcast.setFacebookLinkName(post.getLinkName());
            broadcast.setFacebookLinkUrl(post.getLinkUrl());
        }
    }

    private List<FBPostBuilder> extractFbFromBroadcast(Broadcast broadcast) {
        List<String> fbIds = Arrays.asList(broadcast.getFacebookPageId().split(","));
        return fbIds.stream().map(fbId -> FBPostBuilder.builder()
                .postingUserUid(broadcast.getCreatedByUser().getUid())
                .facebookPageId(fbId)
                .message(broadcast.getFacebookPost())
                .linkUrl(broadcast.getFacebookLinkUrl())
                .linkName(broadcast.getFacebookLinkName())
                .imageKey(broadcast.getFacebookImageKey())
                .imageCaption(broadcast.getFacebookImageCaption())
                .build()).collect(Collectors.toList());
    }

    private void recordTwitterPost(TwitterPostBuilder post, Broadcast bc) {
        if (post != null) {
            bc.setTwitterPost(post.getMessage());
            bc.setTwitterImageKey(post.getImageKey());
        }
    }

    private TwitterPostBuilder extractTweetFromBroadcast(Broadcast broadcast) {
        return TwitterPostBuilder.builder()
                .postingUserUid(broadcast.getCreatedByUser().getUid())
                .message(broadcast.getTwitterPost())
                .imageKey(broadcast.getTwitterImageKey())
                .build();
    }

    private LogsAndNotificationsBundle generateGroupBroadcastBundle(Broadcast bc) {
        log.debug("generating notifications for group broadcast ...");
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        Group group = bc.getGroup();

        GroupLog groupLog = new GroupLog(group, bc.getCreatedByUser(), GroupLogType.BROADCAST_SENT,
                null, null, bc.getAccount(), null);
        groupLog.setBroadcast(bc);
        bundle.addLog(groupLog);

        List<String> topicRestrictions = bc.getTopics();
        List<Province> provinceResrictions = bc.getProvinces();
        List<String> taskTeamUids = bc.getTaskTeams();
        List<String> affiliations = bc.getAffiliations();
        JoinDateCondition joinDateCondition = bc.getJoinDateCondition().orElse(null);
        LocalDate joinDate = bc.getJoinDate().orElse(null);

        List<Membership> membersToReceive;

        if (bc.hasFilter()) {
            membersToReceive = groupFetchBroker.filterGroupMembers(bc.getCreatedByUser(), group.getUid(), provinceResrictions, taskTeamUids,
                    topicRestrictions, affiliations, bc.getJoinMethods(), null, null, joinDate, joinDateCondition,
                    bc.getNamePhoneEmailFilter(), bc.getFilterLanguages());
        } else if (bc.hasTask()) {
            membersToReceive = taskBroker.fetchMembersAssignedToTask(bc.getCreatedByUser().getUid(),
                    bc.getTaskUid(), bc.getTaskType(), bc.taskOnlyPositive());
        } else {
            membersToReceive = membershipRepository.findByGroupUid(group.getUid());
        }

        log.info("finished fetching members by topic, province, etc., found {} members", membersToReceive.size());

        if (bc.hasEmail()) {
            Set<User> emailUsers = membersToReceive.stream().map(Membership::getUser)
                    .filter(User::hasEmailAddress).collect(Collectors.toSet());
            log.info("broadcast has an email, sending it to {} users, bc ID = {}", emailUsers.size(), bc.getUid());
            handleBroadcastEmails(bc, groupLog, emailUsers, bundle);
        }

        long smsCount = 0;
        if (bc.hasShortMessage()) {
            boolean skipUsersWithEmail = bc.isSkipSmsIfEmail() && bc.hasEmail();
            log.info("sending short message ... skipping emails? {}", skipUsersWithEmail);
            Set<User> shortMessageUsers = skipUsersWithEmail ?
                    membersToReceive.stream().map(Membership::getUser).filter(u -> !u.hasEmailAddress() && u.hasPhoneNumber()).collect(Collectors.toSet()) :
                    membersToReceive.stream().map(Membership::getUser).filter(User::hasPhoneNumber).collect(Collectors.toSet());
            log.info("now generating {} notifications", shortMessageUsers.size());
            Set<Notification> shortMessageNotifications = new HashSet<>();
            shortMessageUsers.forEach(u -> {
                GroupBroadcastNotification notification = new GroupBroadcastNotification(u,
                        bc.getShortMsgIncludingMerge(u), bc,
                        u.getMessagingPreference(), groupLog);
                notification.setUseOnlyFreeChannels(bc.isOnlyUseFreeChannels());
                shortMessageNotifications.add(notification);
            });
            bundle.addNotifications(shortMessageNotifications);
            smsCount = shortMessageUsers.size();
        }

        AccountLog accountLog = new AccountLog.Builder(bc.getAccount())
                .user(bc.getCreatedByUser())
                .group(bc.getGroup())
                .broadcast(bc)
                .accountLogType(AccountLogType.BROADCAST_MESSAGE_SENT)
                .billedOrPaid(bc.getAccount().getFreeFormCost() * smsCount).build();
        bundle.addLog(accountLog);

        return bundle;
    }

    private LogsAndNotificationsBundle generateCampaignBroadcastBundle(Broadcast bc) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        return bundle;
    }

    @Override
    @Transactional(readOnly = true)
    public BroadcastDTO fetchBroadcast(String broadcastUid) {
        return assembleDto(broadcastRepository.findOneByUid(broadcastUid));
    }

    @Override
    @Transactional(readOnly = true)
    public Broadcast getBroadcast(String broadcastUid) {
        return broadcastRepository.findOneByUid(broadcastUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BroadcastDTO> fetchSentGroupBroadcasts(String groupUid, Pageable pageable) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Page<Broadcast> broadcasts = broadcastRepository.findByGroupUidAndSentTimeNotNull(group.getUid(), pageable);
        log.info("fetched these broadcasts: {}", broadcasts);
        return broadcasts.map(this::assembleDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BroadcastDTO> fetchScheduledGroupBroadcasts(String groupUid, Pageable pageable) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Page<Broadcast> broadcasts = broadcastRepository.findByGroupUidAndSentTimeIsNullAndBroadcastScheduleNot(group.getUid(), BroadcastSchedule.IMMEDIATE, pageable);
        return broadcasts.map(this::assembleDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastDTO> fetchCampaignBroadcasts(String campaignUid) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        return broadcastRepository.findByCampaign(campaign)
                .stream().map(this::assembleDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 300000)
    public void sendScheduledBroadcasts() {
        log.info("finding and fetching scheduled broadcasts");
        DebugUtil.transactionRequired("Need transaction context in here");
        Specification<Broadcast> isScheduled = (root, query, cb) -> cb.equal(root.get("broadcastSchedule"), BroadcastSchedule.FUTURE);
        Specification<Broadcast> scheduledTimePast = (root, query, cb) -> cb.lessThan(root.get("scheduledSendTime"), Instant.now());
        Specification<Broadcast> notSent = (root, query, cb) -> cb.isNull(root.get("sentTime"));
        List<Broadcast> scheduledBroadcasts = broadcastRepository.findAll(Specifications.where(isScheduled)
                .and(scheduledTimePast).and(notSent));
        log.info("found {} broadcasts to send", scheduledBroadcasts.size());
        // avoiding send for now, juuuust in case ...
        if (!scheduledBroadcasts.isEmpty()) {
            scheduledBroadcasts.forEach(this::sendScheduledBroadcast);
        }
    }

    private BroadcastDTO assembleDto(Broadcast broadcast) {
        Specifications<Notification> smsSpecs = Specifications
                .where(forDeliveryChannels(Arrays.asList(DeliveryRoute.SMS, DeliveryRoute.SHORT_MESSAGE, DeliveryRoute.ANDROID_APP, DeliveryRoute.WHATSAPP)))
                .and(forGroupBroadcast(broadcast));

        Specifications<Notification> emailSpecs = Specifications
                .where(forDeliveryChannels(Arrays.asList(DeliveryRoute.EMAIL_GRASSROOT, DeliveryRoute.EMAIL_3RDPARTY, DeliveryRoute.EMAIL_USERACCOUNT)))
                .and(forGroupBroadcast(broadcast));

        if (environment.acceptsProfiles("production")) {
            smsSpecs = smsSpecs.and(wasDelivered());
            emailSpecs = emailSpecs.and(wasDelivered());
        }

        long smsCount = logsAndNotificationsBroker.countNotifications(smsSpecs);
        long emailCount = logsAndNotificationsBroker.countNotifications(emailSpecs);

        long totalCost = accountLogRepository
                .findByBroadcastAndAccountLogType(broadcast, AccountLogType.BROADCAST_MESSAGE_SENT)
                .stream().mapToLong(AccountLog::getAmountBilledOrPaid).sum();

        List<String> fbPages = null;
        if (broadcast.hasFbPost()) {
            ManagedPagesResponse pages = socialMediaBroker.getManagedFacebookPages(broadcast.getCreatedByUser().getUid());
            fbPages = Arrays.stream(broadcast.getFacebookPageId().split(", "))
                    .filter(pageId -> pages.getPageNameForId(pageId).isPresent())
                    .map(pageId -> pages.getPageNameForId(pageId).orElse(null)).collect(Collectors.toList());
        }

        String twitterAccount = null;
        if (broadcast.hasTwitterPost()) {
            ManagedPagesResponse pages = socialMediaBroker.getManagedPages(broadcast.getCreatedByUser().getUid(), "twitter");
            twitterAccount = pages.getPageNameForId(broadcast.getTwitterImageKey()).orElse(null);
        }

        return new BroadcastDTO(broadcast, smsCount, emailCount, (float) totalCost / 100, fbPages, twitterAccount);
    }



}
