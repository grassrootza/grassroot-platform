package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignTextBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/02/17.
 */
@Api("/api/inbound/sms/")
@RestController @Slf4j
@RequestMapping("/api/inbound/sms/")
public class IncomingSMSController {

    // some prefixes used in logging inbound
    private static final String RECEIVED = "RECEIVED:";
    private static final String MATCHED = "MATCHED:";
    private static final String UNMATCHED = "UNMATCHED:";

    private final UserResponseBroker userResponseBroker;
    private final UserManagementService userManager;

    private final GroupBroker groupBroker;
    private final AccountGroupBroker accountGroupBroker;

    private final CampaignBroker campaignBroker;
    private final CampaignTextBroker campaignTextBroker;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private static final String FROM_PARAMETER_REPLY ="fn";
    private static final String MESSAGE_TEXT_PARAM_REPLY ="ms";

    private static final String FROM_PARAMETER_NEW = "num";
    private static final String MSG_TEXT_PARAM_NEW = "mesg";

    private static final Duration NOTIFICATION_WINDOW = Duration.of(1, ChronoUnit.DAYS);
    private static final int SEARCH_DEPTH = 5; // number of notifications to check

    @Autowired
    public IncomingSMSController(UserResponseBroker userResponseBroker, UserManagementService userManager, GroupBroker groupBroker,
                                 AccountGroupBroker accountGroupBroker, CampaignBroker campaignBroker,
                                 @Qualifier("messageSourceAccessor") MessageSourceAccessor messageSource, CampaignTextBroker campaignTextBroker, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userResponseBroker = userResponseBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.campaignTextBroker = campaignTextBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = "initiated/group", method = RequestMethod.GET)
    @ApiOperation(value = "Incoming SMS, under the 'group' short code", notes = "For when we receive an out of the blue SMS, on the group number")
    public @ResponseBody String receiveGroupSms(@RequestParam(value = FROM_PARAMETER_NEW) String phoneNumber,
                    @RequestParam(value = MSG_TEXT_PARAM_NEW) String message) {
        // temporary fix while we get second inbound number running
        Map<String, String> campaignTags = campaignBroker.getActiveCampaignJoinWords();
        if (campaignTags != null && campaignTags.keySet().contains(message.trim().toLowerCase())) {
            log.info("matched a campaign word, triggering campaign sequence, in precedence to group");
            return receiveNewlyInitiatedSms(phoneNumber, message);
        }

        log.info("Inside receiving a message on group list, received {} as message", message);
        // join word as key, uid as map
        User user = userManager.loadOrCreateUser(phoneNumber);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, RECEIVED + message, UserInterfaceType.INCOMING_SMS));

        Map<String, String> groupJoinWords = groupBroker.getJoinWordsWithGroupIds();

        Set<String> groupMatches = groupJoinWords.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().equalsIgnoreCase(message.trim()))
                .map(Map.Entry::getValue).collect(Collectors.toSet());

        final String reply;
        if (!groupMatches.isEmpty()) {
            // disambiguate somehow ... for the moment, just adding the first
            final String groupUid = groupMatches.iterator().next();
            Membership membership = groupBroker.addMemberViaJoinCode(user.getUid(), groupUid, message, UserInterfaceType.INCOMING_SMS);
            // if group has custom welcome messages those will be triggered for everyone, in group broker, so don't do it here
            reply = membership.getGroup().isPaidFor() && !accountGroupBroker.hasGroupWelcomeMessages(groupUid) ?
                    accountGroupBroker.generateGroupWelcomeReply(user.getUid(), groupUid) : "";
            bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, MATCHED + message, UserInterfaceType.INCOMING_SMS));
        } else {
            log.info("received a join word but don't know what to do with it");
            reply = "Sorry, we couldn't find a Grassroot group with that join word (" + message + "). Try another?";
            bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, UNMATCHED + message, UserInterfaceType.INCOMING_SMS));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
        return reply;
    }

    @RequestMapping(value = "initiated/campaign", method = RequestMethod.GET)
    @ApiOperation(value = "Send in an incoming SMS, that is newly initiated, on campaign short code",
            notes = "For when we receive an SMS 'out of the blue', on the campaign number")
    public String receiveNewlyInitiatedSms(@RequestParam(value = FROM_PARAMETER_REPLY) String phoneNumber,
                                           @RequestParam(value = MESSAGE_TEXT_PARAM_REPLY) String message) {
        log.info("Inside receiving newly initiated SMS, received {} as message", message);

        User user = userManager.loadOrCreateUser(phoneNumber); // this may be a user we don't know
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        Map<String, String> campaignTags = campaignBroker.getActiveCampaignJoinWords();
        log.info("active campaign tags = {}", campaignTags);

        Set<String> campaignMatches = campaignTags.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().equalsIgnoreCase(message.trim()))
                .map(Map.Entry::getValue).collect(Collectors.toSet());

        final String reply;
        if (!campaignMatches.isEmpty()) {
            // disambiguate somehow ... for the moment, just adding the first
            final String campaignUid = campaignMatches.iterator().next();
            bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, MATCHED + message, UserInterfaceType.INCOMING_SMS));


            // since there are and may be two such messages, we rather initiate them through the back
            campaignTextBroker.checkForAndTriggerCampaignText(campaignUid, user.getUid());
            reply = "";
        } else {
            log.info("received a join word but don't know what to do with it, message: {}", message.trim());
            reply = "Sorry, we couldn't find a campaign with that topic (" + message + "). Try another?";
            bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, UNMATCHED + message, UserInterfaceType.INCOMING_SMS));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
        return reply;
    }

    @RequestMapping(value = "reply", method = RequestMethod.GET)
    @ApiOperation(value = "Send in an incoming SMS, replying to one of our messages", notes = "For when an end-user SMSs a reply to the platform. " +
            "Parameters are phone number and the message sent")
    public void receiveSmsReply(@RequestParam(value = FROM_PARAMETER_REPLY) String phoneNumber,
                                @RequestParam(value = MESSAGE_TEXT_PARAM_REPLY) String msg) {

        log.info("Inside IncomingSMSController - following message was received: ms= {}", msg);
        User user = userManager.findByInputNumber(phoneNumber);
        if (user == null) {
            log.warn("Message {} from unknown user: {}", msg, phoneNumber);
            return;
        }

        final String trimmedMsg = msg.trim();
        Page<Notification> latestToUser = logsAndNotificationsBroker.lastNotificationsSentToUser(user, 1,
                Instant.now().minus(NOTIFICATION_WINDOW));
        if (latestToUser == null || latestToUser.getContent() == null || latestToUser.getContent().isEmpty()) {
            log.warn("Message {} from user, but has never had a notification sent");
            return;
        }

        Notification notification = latestToUser.getContent().get(0);
        if (notification.getCampaignLog() != null) {
            String returnMsg = handleCampaignResponse(user, trimmedMsg, notification);
            log.info("handled campaign reply, responded with: {}", returnMsg);
            return;
        }

        EntityForUserResponse likelyEntity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), false);

        if (likelyEntity == null || !userResponseBroker.checkValidityOfResponse(likelyEntity, trimmedMsg)) {
            log.info("User response does not match any recently sent entities, recording raw text log");
            handleUnknownResponse(user, trimmedMsg);
            return;
        }

        userResponseBroker.recordUserResponse(user.getUid(), likelyEntity.getJpaEntityType(), likelyEntity.getUid(), trimmedMsg);
    }

    private String handleCampaignResponse(User user, String trimmedMsg, Notification sentNotification) {
        final Campaign campaign = sentNotification.getCampaignLog().getCampaign();
        log.info("handling an SMS reply to this campaign: {}", campaign);
        return campaignTextBroker.handleCampaignTextResponse(campaign.getUid(), user.getUid(), trimmedMsg, UserInterfaceType.INCOMING_SMS);
    }

    private void handleUnknownResponse(User user, String trimmedMsg) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        bundle.addLog(new UserLog(user.getUid(), UserLogType.SENT_UNEXPECTED_SMS_MESSAGE,
                trimmedMsg, UserInterfaceType.INCOMING_SMS));

        // get the last day's worth of notifications
        List<Notification> recentNotifications = logsAndNotificationsBroker
                .lastNotificationsSentToUser(user, SEARCH_DEPTH, Instant.now().minus(NOTIFICATION_WINDOW)).getContent();
        log.info("since {}, number of recent notifications: {}", Instant.now().minus(NOTIFICATION_WINDOW), recentNotifications.size());

        // this will store a possible group and each notification that was sent
        Map<Group, String> messagesAndGroups = new HashMap<>();

        recentNotifications.stream()
                .sorted(Comparator.comparing(Notification::getCreatedDateTime)) // important so 'message' is most recent
                .forEach(n -> {
                    Group group = n.getRelevantGroup();
                    if (group != null)
                        messagesAndGroups.put(group, n.getMessage());
                });

        log.info("okay, we have {} distinct groups", messagesAndGroups.size());

        for (Map.Entry<Group, String> entry : messagesAndGroups.entrySet()) {
            String description = MessageFormat.format("From user: {0}; likely responding to: {1}", trimmedMsg, entry.getValue());
            GroupLog groupLog = new GroupLog(entry.getKey(), user, GroupLogType.USER_SENT_UNKNOWN_RESPONSE, user, null, null,
                    description.substring(0, Math.min(255, description.length()))); // since could be very long ...
            bundle.addLog(groupLog);
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

}