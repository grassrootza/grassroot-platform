package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.domain.notification.CampaignBroadcastNotification;
import za.org.grassroot.core.domain.notification.CampaignResponseNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.BroadcastRepository;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Service @Slf4j
public class CampaignTextBrokerImpl implements CampaignTextBroker {

    // for handling responses
    private static final String MORE_INFO_STRING = "1";
    private static final String MISTAKEN_JOIN_STRING = "0"; // probably want to do this using

    private final CampaignBroker campaignBroker;
    private final UserManagementService userManager;
    private final GroupBroker groupBroker;
    private final BroadcastRepository broadcastRepository;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private MessageSourceAccessor messageSource;

    public CampaignTextBrokerImpl(CampaignBroker campaignBroker, UserManagementService userManager, GroupBroker groupBroker, BroadcastRepository broadcastRepository, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.campaignBroker = campaignBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.broadcastRepository = broadcastRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Autowired
    public void setMessageSource(@Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    @Transactional
    public void setCampaignMessageText(String userUid, String campaignUid, String message) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignBroker.loadForModification(userUid, Objects.requireNonNull(campaignUid));

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
        Campaign campaign = campaignBroker.loadForModification(userUid, Objects.requireNonNull(campaignUid));

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
        Campaign campaign = campaignBroker.loadForModification(userUid, Objects.requireNonNull(campaignUid));
        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        return template != null ? template.getSmsTemplate1() : null;
    }

    @Async
    @Override
    @Transactional
    public void checkForAndTriggerCampaignText(String campaignUid, String userUid, String callBackNumber, UserInterfaceType channel) {
        Campaign campaign = campaignBroker.load(campaignUid);
        User user = userManager.load(Objects.requireNonNull(userUid));
        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        log.info("checked for welcome message, found? : {}", template);
        if (template != null && !StringUtils.isEmpty(template.getSmsTemplate1()) && campaign.outboundBudgetLeft() > 0) {
            triggerCampaignText(campaign, user, template, template.getSmsTemplate1(), channel, callBackNumber);
        }
    }

    private void triggerCampaignText(Campaign campaign, User user, Broadcast template, String message, UserInterfaceType channel, String callBackNumber) {
        // only send once, so if user has got in the past, don't send again
        if (countLogs(campaign, user, CampaignLogType.CAMPAIGN_WELCOME_MESSAGE) > 0) {
            log.info("Found a user with prior campaign welcome message, not sending");
            return;
        }


        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_WELCOME_MESSAGE, campaign, channel, "Outbound engagement SMS");
        bundle.addLog(campaignLog);

        CampaignBroadcastNotification notification = new CampaignBroadcastNotification(user, message, template, null, campaignLog);
        Instant sendTime = UserInterfaceType.USSD.equals(channel) ? Instant.now().plus(30, ChronoUnit.SECONDS) : Instant.now();
        notification.setSendOnlyAfter(sendTime);
        bundle.addNotification(notification);

        CampaignType campaignType = campaign.getCampaignType();
        if (userCanJoin(campaignType)) {
            Locale locale = StringUtils.isEmpty(user.getLanguageCode()) ? user.getLocale() : campaign.getDefaultLanguage();
            final String respondText = getNextStepMessage(campaignType, channel, locale, callBackNumber);
            CampaignResponseNotification sendMoreInfo = new CampaignResponseNotification(user, respondText, campaignLog);
            sendMoreInfo.setSendOnlyAfter(sendTime.plus(15, ChronoUnit.SECONDS)); // to make sure it comes in second
            bundle.addNotification(sendMoreInfo);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        campaign.addToOutboundSpent(campaign.getAccount().getFreeFormCost());
    }

    private String getNextStepMessage(CampaignType campaignType, UserInterfaceType channel, Locale locale, String callBack) {
        if (UserInterfaceType.PLEASE_CALL_ME.equals(channel)) {
            return messageSource.getMessage("text.campaign.pcm.respond", new String[] { callBack }, locale);
        } else {
            final String messageKey = "text.campaign.opening." + campaignType.name().toLowerCase();
            return messageSource.getMessage(messageKey, new String[]{MORE_INFO_STRING}, locale);
        }
    }

    private long countLogs(Campaign campaign, User user, CampaignLogType logType) {
        Specification<CampaignLog> logSpecifications = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaignLogType), logType);
        Specification<CampaignLog> userSpecifications = (root, query, cb) -> cb.equal(root.get(CampaignLog_.user), user);
        Specification<CampaignLog> campaignSpecs = (root, query, cb) -> cb.equal(root.get(CampaignLog_.campaign), campaign);

        return logsAndNotificationsBroker.countCampaignLogs(Specification.where(logSpecifications).and(userSpecifications)
                .and(campaignSpecs));
    }

    @Override
    @Transactional
    public String handleCampaignTextResponse(String campaignUid, String userUid, String reply, UserInterfaceType channel) {
        User user = userManager.load(userUid);
        Campaign campaign = campaignBroker.load(campaignUid);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        String returnMsg = channel.equals(UserInterfaceType.PLEASE_CALL_ME) ? handleInboundPcmReply(user, campaign, reply) :
                handleInboundSmsReply(user, campaign, reply);

        if (!StringUtils.isEmpty(returnMsg)) {
            final String logMsg = StringUtils.truncate(String.format("Responded to user, number %s, with info %s", user.getName(), returnMsg), 250);
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_REPLIED, campaign, channel, logMsg);
            bundle.addLog(campaignLog);
            bundle.addNotification(new CampaignResponseNotification(user, returnMsg, campaignLog));
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        return returnMsg;
    }

    private String handleInboundSmsReply(User user, Campaign campaign, String reply) {
        if (MORE_INFO_STRING.equals(reply)) {
            final String mainMsg = campaignBroker.getMessageOfType(campaign.getUid(), CampaignActionType.MORE_INFO, user.getUid(),
                    UserInterfaceType.USSD); // since we are actually reusing the USSD channel
            final String suffix = userCanJoin(campaign.getCampaignType()) ? messageSource.getMessage("text.campaign.moreinfo.respond") : "";
            return !StringUtils.isEmpty(mainMsg) ? mainMsg.trim() + " " + suffix.trim() : suffix.trim();
        } else if (MISTAKEN_JOIN_STRING.equals(reply)) {
            // no message, just remove them from group, as long as they aren't an organizer

            groupBroker.unsubscribeMember(user.getUid(), campaign.getMasterGroup().getUid());
            logsAndNotificationsBroker.removeCampaignLog(user, campaign, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP);
            return messageSource.getMessage("text.campaign.response.reversed", new String[] { campaign.getCampaignCode() });
        } else {
            log.info("Okay, adding user to group ...");
            campaignBroker.addUserToCampaignMasterGroup(campaign.getUid(), user.getUid(), UserInterfaceType.INCOMING_SMS);
            return messageSource.getMessage("text.campaign.response.success", new String[] { MISTAKEN_JOIN_STRING });
        }
    }

    private String handleInboundPcmReply(User user, Campaign campaign, String reply) {
        log.info("Okay, adding user to group via PCM ... reply from user: {}", reply);
        campaignBroker.addUserToCampaignMasterGroup(campaign.getUid(), user.getUid(), UserInterfaceType.PLEASE_CALL_ME);
        return messageSource.getMessage("text.campaign.response.success", new String[] { MISTAKEN_JOIN_STRING });
    }

    private boolean userCanJoin(CampaignType campaignType) {
        return CampaignType.ACQUISITION.equals(campaignType) || CampaignType.PETITION.equals(campaignType);
    }

}
