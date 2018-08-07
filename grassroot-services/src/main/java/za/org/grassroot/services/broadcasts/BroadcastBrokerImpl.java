package za.org.grassroot.services.broadcasts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.User_;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignLog_;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.domain.notification.CampaignBroadcastNotification;
import za.org.grassroot.core.domain.notification.GroupBroadcastNotification;
import za.org.grassroot.core.domain.notification.NotificationStatus;
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
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.enums.CampaignLogType.*;
import static za.org.grassroot.core.specifications.NotificationSpecifications.*;

@Service @Slf4j
public class BroadcastBrokerImpl implements BroadcastBroker {

    private static final String RESEND_PREFIX = "[RESENT] ";

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
    private final CampaignLogRepository campaignLogRepository;

    private final Environment environment;

    private PasswordTokenService tokenService;

    @Autowired
    public BroadcastBrokerImpl(BroadcastRepository broadcastRepository, UserRepository userRepository, GroupRepository groupRepository,
                               CampaignRepository campaignRepository, MembershipRepository membershipRepository, GroupFetchBroker groupFetchBroker,
                               TaskBroker taskBroker, MessagingServiceBroker messagingServiceBroker, SocialMediaBroker socialMediaBroker,
                               LogsAndNotificationsBroker logsAndNotificationsBroker, AccountLogRepository accountLogRepository,
                               CampaignLogRepository campaignLogRepository, Environment environment) {
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
        this.campaignLogRepository = campaignLogRepository;
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
        Group group = groupRepository.findOneByUid(groupUid);
        Account account = findAccountToChargeBroadcast(user, group.getUid());

        BroadcastInfo.BroadcastInfoBuilder builder = BroadcastInfo.builder();
        builder.broadcastId(UIDGenerator.generateId());

        builder.mergeFields(Arrays.asList(Broadcast.NAME_FIELD_TEMPLATE, Broadcast.CONTACT_FIELD_TEMPALTE,
                Broadcast.PROVINCE_FIELD_TEMPLATE, Broadcast.DATE_FIELD_TEMPLATE));

        if (account != null && account.isEnabled()) {
            builder.isSmsAllowed(true).smsCostCents(25);
        } else {
            builder.isSmsAllowed(false);
        }

        List<FacebookAccount> fbAccounts = socialMediaBroker.getFacebookPages(userUid);
        log.info("fb accounts retrieved: {}", fbAccounts);

        builder.isFbConnected(!fbAccounts.isEmpty())
                .facebookPages(fbAccounts);

        TwitterAccount twitterAccount = socialMediaBroker.isTwitterAccountConnected(userUid);
        builder.isTwitterConnected(twitterAccount != null)
                .twitterAccount(twitterAccount);

        List<Campaign> associatedCampaigns = campaignRepository.findByMasterGroupUid(groupUid, Sort.by("createdDateTime"));
        if (associatedCampaigns != null) {
            builder.campaignNamesUrls(associatedCampaigns
                .stream().filter(Campaign::isActiveWithUrl).collect(Collectors.toMap(Campaign::getName, Campaign::getLandingUrl)));
        }

        builder.allMemberCount(membershipRepository.count((root, query, cb) -> cb.equal(root.get("group"), (group))));

        return builder.build();
    }

    @Override
    @Transactional(readOnly = true)
    public BroadcastInfo fetchCampaignBroadcastParams(String userUid, String campaignUid) {
        User user = userRepository.findOneByUid(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        Account account = campaign.getAccount();

        if (account == null)
            throw new IllegalArgumentException("Only account-linked campaigns can send a broadcast");

        BroadcastInfo.BroadcastInfoBuilder builder = BroadcastInfo.builder();
        builder.broadcastId(UIDGenerator.generateId());

        builder.mergeFields(Arrays.asList(Broadcast.NAME_FIELD_TEMPLATE, Broadcast.CONTACT_FIELD_TEMPALTE,
                Broadcast.PROVINCE_FIELD_TEMPLATE, Broadcast.DATE_FIELD_TEMPLATE));

        if (account.getFreeFormCost() > 0) {
            builder.isSmsAllowed(true).smsCostCents(account.getFreeFormCost());
        } else {
            builder.isSmsAllowed(false);
        }

        List<FacebookAccount> fbAccounts = socialMediaBroker.getFacebookPages(userUid);

        builder.isFbConnected(!fbAccounts.isEmpty()).facebookPages(fbAccounts);

        TwitterAccount twitterAccount = socialMediaBroker.isTwitterAccountConnected(userUid);
        builder.isTwitterConnected(twitterAccount != null).twitterAccount(twitterAccount);

        int campaignMembersSize = getCampaignJoinedUsers(campaign).size();
        builder.allMemberCount(campaignMembersSize);
        log.info("count of engaged users fo campaign: {}", campaignMembersSize);

        return builder.build();
    }

    private Specification<CampaignLog> engagementLogsForCampaign(Campaign campaign) {
        Specification<CampaignLog> forCampaign = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaign), campaign);
        List<CampaignLogType> types = Arrays.asList(CAMPAIGN_PETITION_SIGNED, CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,
                CAMPAIGN_SHARED, CAMPAIGN_USER_TAGGED);
        Specification<CampaignLog> engaged = (root, query, cb) -> root.get(CampaignLog_.campaignLogType).in(types);
        return Specification.where(forCampaign).and(engaged);
    }

    private List<User> getCampaignJoinedUsers(Campaign campaign) {
        return campaignLogRepository.findAll(engagementLogsForCampaign(campaign))
                .stream().map(CampaignLog::getUser).distinct().collect(Collectors.toList());
    }

    private Account findAccountToChargeBroadcast(User user, String groupUid) {
        return user.getPrimaryAccount() != null ? user.getPrimaryAccount() : groupRepository.findOneByUid(groupUid).getAccount();
    }

    @Override
    @Transactional
    public String sendGroupBroadcast(BroadcastComponents bc) {
        User user = userRepository.findOneByUid(bc.getUserUid());
        Account account = findAccountToChargeBroadcast(user, bc.getGroupUid());

        if (account == null && !StringUtils.isEmpty(bc.getShortMessage())) {
            throw new NoPaidAccountException();
        }

        Broadcast broadcast = createWithStandardItems(bc, user, account);

        broadcast.setGroup(groupRepository.findOneByUid(bc.getGroupUid()));

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
    public String sendCampaignBroadcast(BroadcastComponents bc) {
        User user = userRepository.findOneByUid(bc.getUserUid());
        Campaign campaign = campaignRepository.findOneByUid(bc.getCampaignUid());

        if (campaign.getAccount() == null) {
            throw new NoPaidAccountException();
        }

        Broadcast broadcast = createWithStandardItems(bc, user, campaign.getAccount());

        broadcast.setCampaign(campaignRepository.findOneByUid(bc.getCampaignUid()));

        wireUpFilters(bc, broadcast);

        LogsAndNotificationsBundle bundle = bc.isImmediateBroadcast() ?
                sendImmediateBroadcast(bc, broadcast) :
                storeScheduledBroadcast(bc, broadcast);

        broadcastRepository.saveAndFlush(broadcast);

        logsAndNotificationsBroker.storeBundle(bundle);

        return broadcast.getUid();
    }

    private Broadcast createWithStandardItems(BroadcastComponents bc, User user, Account account) {
        // prepersist is not working as reliably as hoped (or is happening in wrong sequence), to generate this if blank, hence
        final String uid = StringUtils.isEmpty(bc.getBroadcastId()) ? UIDGenerator.generateId() : bc.getBroadcastId();
        return Broadcast.builder()
                .uid(uid)
                .createdByUser(user)
                .account(account)
                .title(bc.getTitle())
                .broadcastSchedule(bc.getBroadcastSchedule())
                .scheduledSendTime(bc.getScheduledSendTime())
                .build();
    }

    @Override
    @Transactional
    public String resendBroadcast(String userUid, String broadcastUid, boolean resendText, boolean resendEmail, boolean resendFb, boolean resendTwitter) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Broadcast original = broadcastRepository.findOneByUid(Objects.requireNonNull(broadcastUid));

        if (!original.getCreatedByUser().equals(user)) {
            throw new AccessDeniedException("Error! Only creating user can resend broadcast");
        }

        Broadcast broadcast = Broadcast.builder()
                .createdByUser(original.getCreatedByUser())
                .account(original.getAccount())
                .title(RESEND_PREFIX + original.getTitle())
                .group(original.getGroup())
                .campaign(original.getCampaign())
                .broadcastSchedule(BroadcastSchedule.IMMEDIATE)
                .onlyUseFreeChannels(original.isOnlyUseFreeChannels())
                .skipSmsIfEmail(original.isSkipSmsIfEmail()).build();


        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if ((resendText && original.hasShortMessage()) || (resendEmail && original.hasEmail())) {
            copyOverContent(original, broadcast);
            copyOverFilters(original, broadcast);

            bundle.addBundle(broadcast.getCampaign() == null ?
                    generateGroupBroadcastBundle(broadcast) : generateCampaignBroadcastBundle(broadcast));
        }

        if (resendFb && original.hasFbPost()) {
            List<FBPostBuilder> posts = extractFbFromBroadcast(original);
            recordFbPosts(posts, broadcast);
            broadcast.setFbPostSucceeded(postToFacebook(posts));
        }

        if (resendTwitter && original.hasTwitterPost()) {
            TwitterPostBuilder post = extractTweetFromBroadcast(original);
            recordTwitterPost(post, broadcast);
            broadcast.setTwitterSucceeded(postToTwitter(post));
        }

        broadcastRepository.saveAndFlush(broadcast);

        logsAndNotificationsBroker.storeBundle(bundle);

        return broadcast.getUid();
    }

    private void copyOverContent(Broadcast original, Broadcast broadcast) {
        broadcast.setSmsTemplate1(original.getSmsTemplate1());
        broadcast.setEmailContent(original.getEmailContent());
        broadcast.setEmailDeliveryRoute(original.getEmailDeliveryRoute());
        broadcast.setEmailImageKey(original.getEmailImageKey());
        broadcast.setEmailAttachments(original.getEmailAttachments());
    }

    private void copyOverFilters(Broadcast original, Broadcast broadcast) {
        if (broadcast.hasFilter()) {
            broadcast.setNameFilter(original.getNamePhoneEmailFilter());
            broadcast.setTaskTeams(original.getTaskTeams());
            broadcast.setJoinMethods(original.getJoinMethods());
            broadcast.setAffiliations(original.getAffiliations());

            original.getJoinDateCondition().ifPresent(broadcast::setJoinDateCondition);
            original.getJoinDate().ifPresent(broadcast::setJoinDateValue);

            broadcast.setProvinces(original.getProvinces());
            broadcast.setLanguages(original.getLanguages());
        }
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

        if (bc.getNoProvince() != null)
            broadcast.setNoProvince(bc.getNoProvince());

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
            broadcast.setFbPostSucceeded(postToFacebook(bc.getFacebookPosts()));
        }

        if (bc.getTwitterPostBuilder() != null) {
            recordTwitterPost(bc.getTwitterPostBuilder(), broadcast);
            broadcast.setTwitterSucceeded(postToTwitter(bc.getTwitterPostBuilder()));
        }

        broadcast.setSentTime(Instant.now());

        return bc.isCampaignBroadcast() ?
                generateCampaignBroadcastBundle(broadcast) :
                generateGroupBroadcastBundle(broadcast);
    }

    private boolean postToFacebook(List<FBPostBuilder> fbPosts) {
        List<GenericPostResponse> fbResponses = socialMediaBroker.postToFacebook(fbPosts);
        return mockSocialMediaBroadcasts || fbResponses.stream().anyMatch(GenericPostResponse::isPostSuccessful);
    }

    private boolean postToTwitter(TwitterPostBuilder twPost) {
        GenericPostResponse twResponse = socialMediaBroker.postToTwitter(twPost);
        return ((twResponse != null && twResponse.isPostSuccessful()) || mockSocialMediaBroadcasts);
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
                    .subject(broadcast.getTitle().replace(RESEND_PREFIX, ""))
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

            boolean deliverySucceeded = messagingServiceBroker.sendEmail(email);

            bundle.addNotifications(recordEmailNotifications(broadcast, recipients, emailBc, actionLog, deliverySucceeded));
        }
    }

    // for counting / auditing purposes
    private Set<Notification> recordEmailNotifications(Broadcast broadcast, Set<User> recipients, EmailBroadcast emailBc, ActionLog actionLog, boolean deliveryCompleted) {
        boolean isCampaign = broadcast.getCampaign() != null;
        return recipients.stream().map(user -> {
            Notification n = isCampaign ? new CampaignBroadcastNotification(user, emailBc.getContent(), broadcast, broadcast.getEmailDeliveryRoute(), (CampaignLog) actionLog) :
                    new GroupBroadcastNotification(user, emailBc.getContent(), broadcast, broadcast.getEmailDeliveryRoute(), (GroupLog) actionLog);
            n.setSendAttempts(1);
            n.setStatus(deliveryCompleted ? NotificationStatus.DELIVERED : NotificationStatus.DELIVERY_FAILED);
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
                .imageMediaFunction(MediaFunction.BROADCAST_IMAGE)
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

        log.info("Generating group broadcast, join date condition {}, join date {}", joinDateCondition, joinDate);

        if (bc.hasFilter()) {
            membersToReceive = groupFetchBroker.filterGroupMembers(
                    bc.getCreatedByUser(), group.getUid(),
                    provinceResrictions, bc.getNoProvinceRestriction().orElse(null),
                    taskTeamUids, topicRestrictions, affiliations, bc.getJoinMethods(),
                    null, null, joinDate, joinDateCondition, bc.getNamePhoneEmailFilter(), bc.getFilterLanguages());
        } else if (bc.hasTask()) {
            membersToReceive = taskBroker.fetchMembersAssignedToTask(bc.getCreatedByUser().getUid(),
                    bc.getTaskUid(), bc.getTaskType(), bc.taskOnlyPositive());
        } else {
            membersToReceive = membershipRepository.findByGroupUid(group.getUid());
        }

        log.info("finished fetching members by topic, province, join date, etc., found {} members", membersToReceive.size());

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

        if (bc.getAccount() != null) {
            bundle.addLog(costAccountLog(bc, smsCount));
        }

        return bundle;
    }

    private LogsAndNotificationsBundle generateCampaignBroadcastBundle(Broadcast bc) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        log.info("generating campaign broadcast bundle from broadcast: {}", bc);
        Campaign campaign = bc.getCampaign();
        CampaignLog campaignLog = new CampaignLog(bc.getCreatedByUser(), CampaignLogType.CAMPAIGN_BROADCAST_SENT,
                campaign, null, null);
        campaignLog.setBroadcast(bc);
        bundle.addLog(campaignLog);

        log.info("should be about to filter, has filter? : {}", bc.hasFilter());
        Set<User> usersToReceive = bc.hasFilter() ? filterCampaignUsers(bc, campaign) :
                new HashSet<>(getCampaignJoinedUsers(campaign));
        log.info("post-filtering, for campaign, {} many users", usersToReceive.size());

        if (bc.hasEmail()) {
            Set<User> emailUsers = usersToReceive.stream().filter(User::hasEmailAddress).collect(Collectors.toSet());
            log.info("campaign broadcast has an email, sending it to {} users, bc ID = {}", emailUsers.size(), bc.getUid());
            handleBroadcastEmails(bc, campaignLog, emailUsers, bundle);
        }

        long smsCount = 0;
        if (bc.hasShortMessage()) {
            boolean skipUsersWithEmail = bc.isSkipSmsIfEmail() && bc.hasEmail();
            log.info("sending short message ... skipping emails? {}", skipUsersWithEmail);
            Set<User> shortMessageUsers = skipUsersWithEmail ?
                    usersToReceive.stream().filter(u -> !u.hasEmailAddress() && u.hasPhoneNumber()).collect(Collectors.toSet()) :
                    usersToReceive.stream().filter(User::hasPhoneNumber).collect(Collectors.toSet());
            log.info("now generating {} notifications", shortMessageUsers.size());
            Set<Notification> shortMessageNotifications = new HashSet<>();
            shortMessageUsers.forEach(u -> {
                CampaignBroadcastNotification notification = new CampaignBroadcastNotification(u,
                        bc.getShortMsgIncludingMerge(u), bc,
                        u.getMessagingPreference(), campaignLog);
                notification.setUseOnlyFreeChannels(bc.isOnlyUseFreeChannels());
                shortMessageNotifications.add(notification);
            });
            bundle.addNotifications(shortMessageNotifications);
            smsCount = shortMessageUsers.size();
        }

        bundle.addLog(costAccountLog(bc, smsCount));
        return bundle;
    }

    private Set<User> filterCampaignUsers(Broadcast bc, Campaign campaign) {
        log.info("filtering campaign users, or should be, with provinces: {}", bc.getProvinces());

        Specification<CampaignLog> specs = engagementLogsForCampaign(campaign);

        if (!bc.getProvinces().isEmpty()) {
            log.info("wiring up for province: {}", bc.getProvinces());
            specs = specs.and((root, query, cb) -> root.get(CampaignLog_.user).get(User_.province).in(bc.getProvinces()));
        }

        if (bc.getJoinDateCondition().isPresent()) {
            log.info("join date condition present, setting");
            specs = specs.and(joinDateCondition(bc.getJoinDateCondition().get(), bc.getJoinDate()
                    .orElseThrow(() -> new IllegalArgumentException("Join date condition without join date"))));
        }

        if (!bc.getTopics().isEmpty()) {
            log.info("topics present, setting: {}", bc.getTopics());
            List<String> prefixedTopics = bc.getTopics().stream().map(s -> Campaign.JOIN_TOPIC_PREFIX + s).collect(Collectors.toList());
            specs = specs.and((root, query, cb) -> root.get(CampaignLog_.description).in(prefixedTopics));
        }

        return campaignLogRepository.findAll(specs).stream().distinct().map(CampaignLog::getUser).distinct()
                .collect(Collectors.toSet());
    }

    private Specification<CampaignLog> joinDateCondition(JoinDateCondition condition, LocalDate joinDate) {
        Instant startOfDay = joinDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = joinDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        switch (condition) {
            case EXACT:
                return (root, query, cb) -> cb.between(root.get(CampaignLog_.creationTime), startOfDay, endOfDay);
            case BEFORE:
                return (root, query, cb) -> cb.lessThan(root.get(CampaignLog_.creationTime), startOfDay);
            case AFTER: // doing as inclusive
                return (root, query, cb) -> cb.greaterThan(root.get(CampaignLog_.creationTime), startOfDay);
            default:
                throw new IllegalArgumentException("Unsupported join date condition");
        }
    }

    private AccountLog costAccountLog(Broadcast bc, long smsCount) {
        return new AccountLog.Builder(bc.getAccount())
                .user(bc.getCreatedByUser())
                .group(bc.getGroup())
                .broadcast(bc)
                .accountLogType(AccountLogType.BROADCAST_MESSAGE_SENT)
                .billedOrPaid(bc.getAccount().getFreeFormCost() * smsCount).build();
    }

    @Override
    @Transactional(readOnly = true)
    public BroadcastDTO fetchBroadcast(String broadcastUid, String fetchingUserId) {
        return assembleDto(broadcastRepository.findOneByUid(broadcastUid), userRepository.findOneByUid(fetchingUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public Broadcast getBroadcast(String broadcastUid) {
        return broadcastRepository.findOneByUid(broadcastUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BroadcastDTO> fetchSentGroupBroadcasts(String groupUid, String fetchingUserUid, Pageable pageable) {
        final User fetchingUser = userRepository.findOneByUid(Objects.requireNonNull(fetchingUserUid));
        Group group = groupRepository.findOneByUid(Objects.requireNonNull(groupUid));
        Page<Broadcast> broadcasts = broadcastRepository.findByGroupUidAndSentTimeNotNull(group.getUid(), pageable);
        log.info("fetched these broadcasts: {}", broadcasts);
        return broadcasts.map(broadcast -> assembleDto(broadcast, fetchingUser));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BroadcastDTO> fetchFutureGroupBroadcasts(String groupUid, String fetchingUserUid, Pageable pageable) {
        final User fetchingUser = userRepository.findOneByUid(Objects.requireNonNull(fetchingUserUid));
        final Group group = groupRepository.findOneByUid(Objects.requireNonNull(groupUid));
        Page<Broadcast> broadcasts = broadcastRepository.findByGroupUidAndSentTimeIsNullAndBroadcastSchedule(group.getUid(), BroadcastSchedule.FUTURE, pageable);
        return broadcasts.map(broadcast -> assembleDto(broadcast, fetchingUser));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastDTO> fetchCampaignBroadcasts(String campaignUid, String fetchingUserUid) {
        final User fetchingUser = userRepository.findOneByUid(Objects.requireNonNull(fetchingUserUid));
        final Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        return broadcastRepository.findByCampaign(campaign)
                .stream().map(broadcast -> assembleDto(broadcast, fetchingUser)).collect(Collectors.toList());
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
        List<Broadcast> scheduledBroadcasts = broadcastRepository.findAll(Specification.where(isScheduled)
                .and(scheduledTimePast).and(notSent));
        log.info("found {} broadcasts to send", scheduledBroadcasts.size());
        // avoiding send for now, juuuust in case ...
        if (!scheduledBroadcasts.isEmpty()) {
            scheduledBroadcasts.forEach(this::sendScheduledBroadcast);
        }
    }

    private BroadcastDTO assembleDto(Broadcast broadcast, User fetchingUser) {
        long smsCount = broadcast.hasShortMessage() ? countDeliveredSms(broadcast) : 0;
        long emailCount = broadcast.hasEmail() ? countDeliveredEmails(broadcast) : 0;

        long totalCost = accountLogRepository
                .findByBroadcastAndAccountLogType(broadcast, AccountLogType.BROADCAST_MESSAGE_SENT)
                .stream().mapToLong(AccountLog::getAmountBilledOrPaid).sum();

        List<String> fbPages = null;
        if (broadcast.hasFbPost()) {
            Map<String, String> userFbPages = socialMediaBroker.getFacebookPages(broadcast.getCreatedByUser().getUid())
                    .stream().collect(Collectors.toMap(FacebookAccount::getPageId, FacebookAccount::getPageName));

            fbPages = Arrays.stream(broadcast.getFacebookPageId().split(", "))
                    .filter(userFbPages::containsKey)
                    .map(userFbPages::get)
                    .collect(Collectors.toList());
        }

        String twitterAccount = null;
        if (broadcast.hasTwitterPost()) {
            TwitterAccount twAccount = socialMediaBroker.isTwitterAccountConnected(broadcast.getCreatedByUser().getUid());
            twitterAccount = twAccount.getTwitterUserId();
        }

        boolean broadcastSucceeded = !hasFailures(broadcast);

        BroadcastDTO dto = new BroadcastDTO(broadcast, broadcast.getCreatedByUser().equals(fetchingUser), broadcastSucceeded);

        dto.setSmsCount(smsCount);
        dto.setEmailCount(emailCount);
        dto.setCostEstimate((float) totalCost / 100);
        dto.setFbPages(fbPages);
        dto.setTwitterAccount(twitterAccount);

        return dto;
    }

    private long countDeliveredSms(Broadcast broadcast) {
        Specification<BroadcastNotification> smsSpecs = Specification
                .where(forBroadcast(broadcast)).and(forShortMessage());
        if (environment.acceptsProfiles("production")) {
            smsSpecs = smsSpecs.and(broadcastDelivered());
        }
        return logsAndNotificationsBroker.countNotifications(smsSpecs, BroadcastNotification.class);
    }

    private long countDeliveredEmails(Broadcast broadcast) {
        Specification<BroadcastNotification> emailSpecs = Specification.where(forBroadcast(broadcast)).and(forEmail());

        if (environment.acceptsProfiles("production")) {
            emailSpecs = emailSpecs.and(broadcastDelivered());
        }

        return logsAndNotificationsBroker.countNotifications(emailSpecs, BroadcastNotification.class);
    }

    private boolean hasFailures(Broadcast broadcast) {
        Specification<BroadcastNotification> failureSpecs = Specification
                .where(forBroadcast(broadcast)).and(broadcastFailure());
        long countFailures = logsAndNotificationsBroker.countNotifications(failureSpecs, BroadcastNotification.class);
        log.info("counting failures: {}, fb : {}, twitter: {}", countFailures, broadcast.hasFbPost(), broadcast.hasTwitterPost());
        return countFailures > 0 || (broadcast.hasFbPost() && !broadcast.getFbPostSucceeded()) ||
                (broadcast.hasTwitterPost() && !broadcast.getTwitterSucceeded());
    }



}
