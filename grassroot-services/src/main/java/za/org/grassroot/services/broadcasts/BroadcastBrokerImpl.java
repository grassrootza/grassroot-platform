package za.org.grassroot.services.broadcasts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.socialmedia.*;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.NotificationSpecifications.*;

@Service @Slf4j
public class BroadcastBrokerImpl implements BroadcastBroker {

    @Value("${grassroot.broadcast.mocksm.enabled:false}")
    private boolean mockSocialMediaBroadcasts;

    // note, make configurable, possibly, and/or use i18n
    private static final DateTimeFormatter SDF = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final String NO_PROVINCE = "your province";

    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;

    private final GroupRepository groupRepository;
    private final CampaignRepository campaignRepository;
    private final MembershipRepository membershipRepository;

    // for heavy lifting on filtering
    private final GroupFetchBroker groupFetchBroker;

    private final MessagingServiceBroker messagingServiceBroker;
    private final SocialMediaBroker socialMediaBroker;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final AccountLogRepository accountLogRepository;

    private final Environment environment;

    @Autowired
    public BroadcastBrokerImpl(BroadcastRepository broadcastRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignRepository campaignRepository, MembershipRepository membershipRepository, GroupFetchBroker groupFetchBroker, MessagingServiceBroker messagingServiceBroker, SocialMediaBroker socialMediaBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, AccountLogRepository accountLogRepository, Environment environment) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.groupFetchBroker = groupFetchBroker;
        this.messagingServiceBroker = messagingServiceBroker;
        this.socialMediaBroker = socialMediaBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.accountLogRepository = accountLogRepository;
        this.environment = environment;
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

        builder.campaignNamesUrls(campaignRepository.findByMasterGroupUid(groupUid, new Sort("createdDateTime"))
                .stream().filter(Campaign::isActive).collect(Collectors.toMap(Campaign::getName, Campaign::getUrl)));

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

        log.info("creating broadcast with Id: {}", bc.getBroadcastId());
        Broadcast broadcast = Broadcast.builder()
                .uid(bc.getBroadcastId())
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

    private void wireUpFilters(BroadcastComponents bc, Broadcast broadcast) {
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

    private LogsAndNotificationsBundle sendScheduledBroadcast(Broadcast bc) {
        if (bc.hasFbPost()) {
            List<GenericPostResponse> fbResponse = socialMediaBroker.postToFacebook(extractFbFromBroadcast(bc));
            bc.setFbPostSucceeded(fbResponse.stream().anyMatch(GenericPostResponse::isPostSuccessful));
        }

        if (bc.hasTwitterPost()) {
            GenericPostResponse twResponse = socialMediaBroker.postToTwitter(extractTweetFromBroadcast(bc));
            bc.setTwitterSucceeded(twResponse.isPostSuccessful());
        }

        return bc.getCampaign() == null ? generateGroupBroadcastBundle(bc) : generateCampaignBroadcastBundle(bc);
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

    private EmailBroadcast extractEmailFromBroadcast(Broadcast broadcast, User recipient) {
        return EmailBroadcast.builder()
                .subject(broadcast.getTitle())
                .content(broadcast.getEmailIncludingMerge(recipient, SDF, NO_PROVINCE, Province.CANONICAL_NAMES_ZA))
                .deliveryRoute(broadcast.getEmailDeliveryRoute())
                .imageUid(broadcast.getEmailImageKey())
                .build();
    }

    private void handleBroadcastEmails(Broadcast broadcast, ActionLog actionLog, Set<User> recipients,
                                       LogsAndNotificationsBundle bundle) {
        Set<Notification> emailNotifications = new HashSet<>();

        recipients.stream().filter(User::hasEmailAddress).forEach(u -> {
            EmailBroadcast emailBroadcast = extractEmailFromBroadcast(broadcast, u);

            if (broadcast.getCampaign() == null) {
                emailNotifications.add(new GroupBroadcastNotification(u, emailBroadcast.getContent(),
                        broadcast.getEmailDeliveryRoute(), (GroupLog) actionLog));
            } else {
                emailNotifications.add(new CampaignBroadcastNotification(u, emailBroadcast.getContent(),
                        broadcast.getEmailDeliveryRoute(), (CampaignLog) actionLog));
            }


            log.info("handling delivery, route = {}", emailBroadcast.getDeliveryRoute());
            if (!DeliveryRoute.EMAIL_GRASSROOT.equals(emailBroadcast.getDeliveryRoute())) {

                emailBroadcast.setFromFieldsIfEmpty(broadcast.getCreatedByUser());
                GrassrootEmail email = emailBroadcast.toGrassrootEmail();
                // note: for now we are going to use this method to do a rest call to cut
                // what will probably be heavy load on persistence layer (and these getting in way of short messages etc)
                // but re-evaluate in the future). also, storing notifications for counts, status update, etc.
                // in future we will scan for bounced emails etc to flag some as non-delivered
                messagingServiceBroker.sendEmail(Collections.singletonList(u.getEmailAddress()), email);
                emailNotifications.forEach(n -> {
                    n.setSendAttempts(1);
                    n.setStatus(NotificationStatus.DELIVERED);
                });

                try {
                    Thread.sleep(100); // adding some friction so we don't overload messaging (or here)
                } catch (InterruptedException e) {
                    log.info("strange, thread exception in email sending");
                }
            }

        });

        bundle.addNotifications(emailNotifications);
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
        log.info("generating notifications for group broadcast ...");
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

        boolean filtersPresent = !bc.getTaskTeams().isEmpty() || !bc.getProvinces().isEmpty() || !bc.getTaskTeams().isEmpty()
                || !bc.getTopics().isEmpty() || !bc.getAffiliations().isEmpty() || !bc.getJoinMethods().isEmpty() || joinDate != null;
        log.info("do we have filters? : {}, taskTeamUids = {}, all tags: {}", filtersPresent, taskTeamUids, bc.getTagList());
        List<Membership> membersToReceive = filtersPresent ? groupFetchBroker.filterGroupMembers(bc.getCreatedByUser(), group.getUid(),
                provinceResrictions, taskTeamUids, topicRestrictions, affiliations, bc.getJoinMethods(), null, null,
                joinDate, joinDateCondition, null) : new ArrayList<>(group.getMemberships());

        log.info("finished fetching members by topic, province, etc., found {} members", membersToReceive.size());

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
                        bc.getShortMsgIncludingMerge(u, SDF, NO_PROVINCE, Province.CANONICAL_NAMES_ZA),
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
