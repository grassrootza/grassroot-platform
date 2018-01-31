package za.org.grassroot.services.broadcasts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.notification.CampaignBroadcastNotification;
import za.org.grassroot.core.domain.notification.GroupBroadcastNotification;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.socialmedia.*;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.NotificationSpecifications.*;

@Service @Slf4j
public class BroadcastBrokerImpl implements BroadcastBroker {

    @Value("${grassroot.broadcast.mocksm.enabled:false}")
    private boolean mockSocialMediaBroadcasts;

    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;

    private final GroupRepository groupRepository;
    private final CampaignRepository campaignRepository;
    private final MembershipRepository membershipRepository;
    private final GroupJoinCodeRepository groupJoinCodeRepository;

    private final MessagingServiceBroker messagingServiceBroker;
    private final SocialMediaBroker socialMediaBroker;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final AccountLogRepository accountLogRepository;

    @Autowired
    public BroadcastBrokerImpl(BroadcastRepository broadcastRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignRepository campaignRepository, MembershipRepository membershipRepository, GroupJoinCodeRepository groupJoinCodeRepository, MessagingServiceBroker messagingServiceBroker, SocialMediaBroker socialMediaBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, AccountLogRepository accountLogRepository) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.groupJoinCodeRepository = groupJoinCodeRepository;
        this.messagingServiceBroker = messagingServiceBroker;
        this.socialMediaBroker = socialMediaBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.accountLogRepository = accountLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BroadcastInfo fetchGroupBroadcastParams(String userUid, String groupUid) {
        User user = userRepository.findOneByUid(userUid);
        Account account = user.getPrimaryAccount();

        BroadcastInfo.BroadcastInfoBuilder builder = BroadcastInfo.builder();
        if (account !=null && account.getFreeFormCost() > 0) {
            builder.isSmsAllowed(true).smsCostCents(account.getFreeFormCost());
        } else {
            builder.isSmsAllowed(false);
        }

        builder.joinLinks(groupJoinCodeRepository.findByGroupUidAndActiveTrue(groupUid).stream()
                .map(GroupJoinCode::getShortUrl).collect(Collectors.toList()));

        if (mockSocialMediaBroadcasts) {
            builder.isFbConnected(true).facebookPages(mockFbPages());
            builder.isTwitterConnected(true).twitterAccount(mockTwitterAccount());
        } else {
            ManagedPagesResponse fbStatus = socialMediaBroker.getManagedFacebookPages(userUid);
            builder.isFbConnected(fbStatus.isUserConnectionValid())
                    .facebookPages(fbStatus.getManagedPages());

            ManagedPage twitterAccount = socialMediaBroker.isTwitterAccountConnected(userUid);
            builder.isTwitterConnected(twitterAccount != null)
                    .twitterAccount(twitterAccount);
        }

        builder.campaignLinks(campaignRepository.findByMasterGroupUid(groupUid, new Sort("createdDateTime"))
                .stream().filter(Campaign::isActive).map(Campaign::getUrl).collect(Collectors.toList()));

        // or for campaign, extract somehow
        Group group = groupRepository.findOneByUid(groupUid);
        builder.allMemberCount(membershipRepository.count((root, query, cb) -> cb.equal(root.get("group"), (group))));

        return builder.build();
    }

    // using this while we are still in alpha - as else a time drag to boot integration service locally etc - remove when done
    private List<ManagedPage> mockFbPages() {
        ManagedPage mockPage = new ManagedPage();
        mockPage.setDisplayName("User FB page");
        mockPage.setProviderUserId("user");
        ManagedPage mockPage2 = new ManagedPage();
        mockPage2.setDisplayName("Org FB page");
        mockPage2.setProviderUserId("org");
        return Arrays.asList(mockPage, mockPage2);
    }

    private ManagedPage mockTwitterAccount() {
        ManagedPage mockAccount = new ManagedPage();
        mockAccount.setDisplayName("@testing");
        mockAccount.setProviderUserId("testing");
        return mockAccount;
    }

    @Override
    @Transactional
    public String sendGroupBroadcast(BroadcastComponents bc) {
        User user = userRepository.findOneByUid(bc.getUserUid());
        Account account = user.getPrimaryAccount();

        if (account == null) {
            throw new NoPaidAccountException();
        }

        Broadcast broadcast = Broadcast.builder()
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

        recordProvincesAndTopics(bc, broadcast);

        LogsAndNotificationsBundle bundle = bc.isImmediateBroadcast() ?
                sendImmediateBroadcast(bc, broadcast) :
                storeScheduledBroadcast(bc, broadcast);

        broadcastRepository.saveAndFlush(broadcast);

        logsAndNotificationsBroker.storeBundle(bundle);

        return broadcast.getUid();
    }

    private LogsAndNotificationsBundle sendImmediateBroadcast(BroadcastComponents bc, Broadcast broadcast) {
        recordShortMessageContent(bc, broadcast);
        recordEmailContent(bc.getEmail(), broadcast);

        if (bc.getFacebookPost() != null) {
            log.info("sending an FB post, from builder: {}", bc.getFacebookPost());
            recordFbPost(bc.getFacebookPost(), broadcast);
            GenericPostResponse fbResponse = socialMediaBroker.postToFacebook(bc.getFacebookPost());
            if ((fbResponse != null && fbResponse.isPostSuccessful()) || mockSocialMediaBroadcasts) {
                broadcast.setFbPostSucceeded(true);
            }
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
        recordShortMessageContent(bc, broadcast);
        recordEmailContent(bc.getEmail(), broadcast);
        recordFbPost(bc.getFacebookPost(), broadcast);
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

    private LogsAndNotificationsBundle sendScheduledBroadcast(Broadcast bc) {
        if (bc.hasFbPost()) {
            GenericPostResponse fbResponse = socialMediaBroker.postToFacebook(extractFbFromBroadcast(bc));
            if (fbResponse.isPostSuccessful()) {
                bc.setFbPostSucceeded(true);
            }
        }

        if (bc.hasTwitterPost()) {
            GenericPostResponse twResponse = socialMediaBroker.postToTwitter(extractTweetFromBroadcast(bc));
            if (twResponse.isPostSuccessful()) {
                bc.setTwitterSucceeded(true);
            }
        }

        return bc.getCampaign() == null ? generateGroupBroadcastBundle(bc) : generateCampaignBroadcastBundle(bc);
    }

    private void recordProvincesAndTopics(BroadcastComponents bc, Broadcast broadcast) {
        if (bc.getTopics() != null && !bc.getTopics().isEmpty()) {
            broadcast.setTopics(new HashSet<>(bc.getTopics()));
        }
        if (bc.getProvinces() != null && !bc.getProvinces().isEmpty()) {
            broadcast.setProvinces(new HashSet<>(bc.getProvinces()));
        }
    }

    private void recordShortMessageContent(BroadcastComponents bc, Broadcast broadcast) {
        broadcast.setSmsTemplate1(bc.getShortMessage());
        broadcast.setOnlyUseFreeChannels(bc.isUseOnlyFreeChannels());
        broadcast.setSkipSmsIfEmail(bc.getEmail() != null && bc.isSkipSmsIfEmail());
    }

    private void recordEmailContent(EmailBroadcast email, Broadcast broadcast) {
        if (email != null) {
            broadcast.setEmailContent(email.getContent());
            broadcast.setEmailImageKey(email.getImageUid());
            log.info("email delivery route: {}", email.getDeliveryRoute());
            broadcast.setEmailDeliveryRoute(email.getDeliveryRoute());
        }
    }

    private EmailBroadcast extractEmailFromBroadcast(Broadcast broadcast) {
        return EmailBroadcast.builder()
                .subject(broadcast.getTitle())
                .content(broadcast.getEmailContent())
                .deliveryRoute(broadcast.getEmailDeliveryRoute())
                .imageUid(broadcast.getEmailImageKey())
                .build();
    }

    private void handleBroadcastEmails(Broadcast broadcast, ActionLog actionLog, Set<User> recipients,
                                       LogsAndNotificationsBundle bundle) {
        EmailBroadcast emailBroadcast = extractEmailFromBroadcast(broadcast);
        Set<Notification> emailNotifications = new HashSet<>();

        recipients.forEach(u -> {
            if (broadcast.getCampaign() == null) {
                emailNotifications.add(new GroupBroadcastNotification(u, broadcast.getEmailContent(),
                        broadcast.getEmailDeliveryRoute(), (GroupLog) actionLog));
            } else {
                emailNotifications.add(new CampaignBroadcastNotification(u, broadcast.getEmailContent(),
                        broadcast.getEmailDeliveryRoute(), (CampaignLog) actionLog));
            }
        });

        log.info("handling delivery, route = {}", emailBroadcast.getDeliveryRoute());
        if (!DeliveryRoute.EMAIL_GRASSROOT.equals(emailBroadcast.getDeliveryRoute())) {
            emailBroadcast.setFromFieldsIfEmpty(broadcast.getCreatedByUser());
            GrassrootEmail email = emailBroadcast.toGrassrootEmail();
            List<String> addresses = recipients.stream().filter(User::hasEmailAddress)
                    .map(User::getEmailAddress).collect(Collectors.toList());
            // note: for now we are going to use this method to do a rest call to cut
            // what will probably be heavy load on persistence layer (and these getting in way of short messages etc)
            // but re-evaluate in the future). also, storing notifications for counts, status update, etc.
            // in future we will scan for bounced emails etc to flag some as non-delivered
            messagingServiceBroker.sendEmail(addresses, email);
            emailNotifications.forEach(n -> {
                n.setSendAttempts(1);
                n.setStatus(NotificationStatus.SENT);
            });
        }

        bundle.addNotifications(emailNotifications);
    }

    private void recordFbPost(FBPostBuilder post, Broadcast broadcast) {
        if (post != null) {
            broadcast.setFacebookPageId(post.getFacebookPageId());
            broadcast.setFacebookPost(post.getMessage());
            broadcast.setFacebookImageCaption(post.getImageCaption());
            broadcast.setFacebookImageKey(post.getImageKey());
            broadcast.setFacebookLinkName(post.getLinkName());
            broadcast.setFacebookLinkUrl(post.getLinkUrl());
        }
    }

    private FBPostBuilder extractFbFromBroadcast(Broadcast broadcast) {
        return FBPostBuilder.builder()
                .postingUserUid(broadcast.getCreatedByUser().getUid())
                .facebookPageId(broadcast.getFacebookPageId())
                .message(broadcast.getFacebookPost())
                .linkUrl(broadcast.getFacebookLinkUrl())
                .linkName(broadcast.getFacebookLinkName())
                .imageKey(broadcast.getFacebookImageKey())
                .imageCaption(broadcast.getFacebookImageCaption())
                .build();
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
        log.info("generating notifications for group broadcast ...");
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        Group group = bc.getGroup();

        GroupLog groupLog = new GroupLog(group, bc.getCreatedByUser(), GroupLogType.BROADCAST_SENT,
                null, null, bc.getAccount(), null);
        groupLog.setBroadcast(bc);
        bundle.addLog(groupLog);

        List<String> topicRestrictions = bc.getTopics();
        List<Province> provinceResrictions = bc.getProvinces();

        // todo : just get the users via a join, the stream & map below will kill us on big groups
        List<Membership> membersToReceive;
        if (!topicRestrictions.isEmpty() && !provinceResrictions.isEmpty()) {
            membersToReceive = membershipRepository.findByGroupProvinceTopicsAndJoinedDate(
                    group.getId(), provinceResrictions, TagHolder.convertTopicsToTags(topicRestrictions),
                    DateTimeUtil.getEarliestInstant());
        } else if (!topicRestrictions.isEmpty()) {
            // no province restrictions, so just get by topics
            membersToReceive = membershipRepository.findByGroupTagsAndJoinedDateAfter(group.getId(),
                    TagHolder.convertTopicsToTags(topicRestrictions), DateTimeUtil.getEarliestInstant());
        } else if (!provinceResrictions.isEmpty()) {
            // reverse of the above
            membersToReceive = membershipRepository.findAll(MembershipSpecifications.groupMembersInProvincesJoinedAfter(
                    group, provinceResrictions, DateTimeUtil.getEarliestInstant()));
        } else {
            // get everyone, since we have no restrictions
            membersToReceive = membershipRepository.findAll(MembershipSpecifications.forGroup(group));
        }
        log.info("finished fetching members by topic and province, found {} members", membersToReceive.size());

        if (bc.hasEmail()) {
            Set<User> emailUsers = membersToReceive.stream().map(Membership::getUser)
                    .filter(User::hasEmailAddress).collect(Collectors.toSet());
            handleBroadcastEmails(bc, groupLog, emailUsers, bundle);
        }

        long smsCount = 0;
        if (bc.hasShortMessage()) {
            boolean skipUsersWithEmail = bc.isSkipSmsIfEmail() && bc.hasEmail();
            log.info("sending short message ... skipping emails? {}", skipUsersWithEmail);
            Set<User> shortMessageUsers = skipUsersWithEmail ?
                    membersToReceive.stream().map(Membership::getUser).filter(u -> !u.hasEmailAddress()).collect(Collectors.toSet()) :
                    membersToReceive.stream().map(Membership::getUser).collect(Collectors.toSet());
            log.info("now generating {} notifications", shortMessageUsers.size());
            Set<Notification> shortMessageNotifications = new HashSet<>();
            shortMessageUsers.forEach(u -> {
                GroupBroadcastNotification notification = new GroupBroadcastNotification(u,
                        bc.getSmsTemplate1(), u.getMessagingPreference(), groupLog);
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
    public Page<BroadcastDTO> fetchSentGroupBroadcasts(String groupUid, Pageable pageable) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Page<Broadcast> broadcasts = broadcastRepository.findByGroupUidAndSentTimeNotNull(group.getUid(), pageable);
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

    private BroadcastDTO assembleDto(Broadcast broadcast) {
        // todo: clean this up and speed it up. currently ~5 queries per broadcast, not good when broadcasts start to multiply
        Specifications<Notification> smsSpecs = Specifications.where(wasDelivered())
                .and(forDeliveryChannel(DeliveryRoute.SMS))
                .and(forGroupBroadcast(broadcast));

        Specifications<Notification> emailSpecs = Specifications.where(wasDelivered())
                .and(forDeliveryChannel(DeliveryRoute.EMAIL_GRASSROOT))
                .and(forGroupBroadcast(broadcast));

        long smsCount = logsAndNotificationsBroker.countNotifications(smsSpecs);
        long emailCount = logsAndNotificationsBroker.countNotifications(emailSpecs);

        long totalCost = accountLogRepository
                .findByBroadcastAndAccountLogType(broadcast, AccountLogType.BROADCAST_MESSAGE_SENT)
                .stream().mapToLong(AccountLog::getAmountBilledOrPaid).sum();

        return new BroadcastDTO(broadcast, smsCount, emailCount, (float) totalCost / 100);
    }



}
