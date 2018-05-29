package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.campaign.CampaignType;
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
    public void checkForAndTriggerCampaignText(String campaignUid, String userUid) {
        Campaign campaign = campaignBroker.load(campaignUid);
        User user = userManager.load(Objects.requireNonNull(userUid));
        Broadcast template = broadcastRepository.findTopByCampaignAndBroadcastScheduleAndActiveTrue(campaign, BroadcastSchedule.ENGAGED_CAMPAIGN);
        log.info("checked for welcome message, found? : {}", template);
        if (template != null && !StringUtils.isEmpty(template.getSmsTemplate1())
                && campaign.outboundBudgetLeft() > 0) {
            triggerCampaignText(campaign, user, template, template.getSmsTemplate1());
        }
    }

    private void triggerCampaignText(Campaign campaign, User user, Broadcast template, String message) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_WELCOME_MESSAGE,
                campaign, null, "Outbound engagement SMS");
        bundle.addLog(campaignLog);

        CampaignBroadcastNotification notification = new CampaignBroadcastNotification(
                user, message, template, null, campaignLog);
        notification.setSendOnlyAfter(Instant.now().plus(1, ChronoUnit.MINUTES));
        bundle.addNotification(notification);

        CampaignType campaignType = campaign.getCampaignType();
        if (userCanJoin(campaignType)) {
            final String messageKey = "text.campaign.opening." + campaignType.name().toLowerCase();
            final String respondText = messageSource.getMessage(messageKey, new String[] { MORE_INFO_STRING });
            CampaignResponseNotification sendMoreInfo = new CampaignResponseNotification(user, respondText, campaignLog);
            bundle.addNotification(sendMoreInfo);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        campaign.addToOutboundSpent(campaign.getAccount().getFreeFormCost());
    }

    @Override
    @Transactional
    public String handleCampaignTextResponse(String campaignUid, String userUid, String reply, UserInterfaceType channel) {
        User user = userManager.load(userUid);
        Campaign campaign = campaignBroker.load(campaignUid);

        String returnMsg = "";
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if (MORE_INFO_STRING.equals(reply)) {
            final String mainMsg = campaignBroker.getMessageOfType(campaignUid, CampaignActionType.MORE_INFO, userUid,
                    UserInterfaceType.USSD); // since we are actually reusing the USSD channel
            final String suffix = userCanJoin(campaign.getCampaignType()) ? messageSource.getMessage("text.campaign.moreinfo.respond") : "";
            returnMsg =  !StringUtils.isEmpty(mainMsg) ? mainMsg.trim() + " " + suffix.trim() : suffix.trim();
        } else if (MISTAKEN_JOIN_STRING.equals(reply)) {
            // no message, just remove them from group
            groupBroker.unsubscribeMember(userUid, campaign.getMasterGroup().getUid());
            returnMsg = messageSource.getMessage("text.campaign.response.reversed", new String[] { campaign.getCampaignCode() });
            logsAndNotificationsBroker.removeCampaignLog(user, campaign, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP);
        } else {
            log.info("Okay, adding user to group ...");
            returnMsg = messageSource.getMessage("text.campaign.response.success", new String[] { MISTAKEN_JOIN_STRING });
            campaignBroker.addUserToCampaignMasterGroup(campaignUid, userUid, channel);
        }

        if (!StringUtils.isEmpty(returnMsg)) {
            final String logMsg = StringUtils.truncate(String.format("Responded to user, number %s, with info %s", user.getName(), returnMsg), 250);
            CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_REPLIED, campaign, channel, logMsg);
            bundle.addLog(campaignLog);
            bundle.addNotification(new CampaignResponseNotification(user, returnMsg, campaignLog));
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        return returnMsg;
    }

    private boolean userCanJoin(CampaignType campaignType) {
        return CampaignType.ACQUISITION.equals(campaignType) || CampaignType.INFORMATION.equals(campaignType);
    }

}
