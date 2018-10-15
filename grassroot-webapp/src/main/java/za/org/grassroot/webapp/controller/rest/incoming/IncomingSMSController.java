package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
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

    @Value("${grassroot.pcm.inbound.secret:1234}")
    private String INBOUND_PCM_TOKEN;

    // some prefixes used in logging inbound
    private static final String RECEIVED = "RECEIVED:";
    private static final String MATCHED = "MATCHED:";
    private static final String UNMATCHED = "UNMATCHED:";

    private final UserResponseBroker userResponseBroker;
    private final UserManagementService userManager;

    private final GroupBroker groupBroker;
    private final AccountFeaturesBroker accountFeaturesBroker;

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
                                 AccountFeaturesBroker accountFeaturesBroker, CampaignBroker campaignBroker,
                                 CampaignTextBroker campaignTextBroker, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userResponseBroker = userResponseBroker;
        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.accountFeaturesBroker = accountFeaturesBroker;
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
        User user = userManager.loadOrCreateUser(phoneNumber, UserInterfaceType.INCOMING_SMS);

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
            reply = membership.getGroup().isPaidFor() && !accountFeaturesBroker.hasGroupWelcomeMessages(groupUid) ?
                    accountFeaturesBroker.generateGroupWelcomeReply(user.getUid(), groupUid) : "";
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

        User user = userManager.loadOrCreateUser(phoneNumber, UserInterfaceType.INCOMING_SMS); // this may be a user we don't know
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
            campaignBroker.recordEngagement(campaignUid, user.getUid(), UserInterfaceType.INCOMING_SMS, message);

            // since there are and may be two such messages, we rather initiate them through the back
            campaignTextBroker.checkForAndTriggerCampaignText(campaignUid, user.getUid(), null, UserInterfaceType.INCOMING_SMS);
            reply = "";
        } else {
            log.info("received a join word but don't know what to do with it, message: {}", message.trim());
            reply = "Sorry, we couldn't find a campaign with that topic (" + message + "). Try another?";
            bundle.addLog(new UserLog(user.getUid(), UserLogType.INBOUND_JOIN_WORD, UNMATCHED + message, UserInterfaceType.INCOMING_SMS));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
        return reply;
    }

    // use a tag - note that this will therefore require a no space tag
    @RequestMapping(value = "initiated/pcm/campaign/tag/{campaignUid}/{tag}", method = RequestMethod.POST)
    @ApiOperation(value = "Receive an incoming please call me, and use it to tag a member of the group")
    public ResponseEntity receiveTaggingPcm(@PathVariable String campaignUid,
                                            @PathVariable String tag,
                                            @RequestParam String secret,
                                            @RequestParam(value = "from_number") String phoneNumber,
                                            @RequestParam(value = "content") String message,
                                            @RequestParam(value = "to_number") String toNumber) {
        ResponseEntity hygieneCheck = checkPcmForSecretAndFormat(secret, message);
        if (hygieneCheck != null)
            return hygieneCheck;

        User user = userManager.loadOrCreateUser(phoneNumber, UserInterfaceType.INCOMING_SMS);

        if (!campaignBroker.isUserInCampaignMasterGroup(campaignUid, user.getUid())) {
            campaignBroker.addUserToCampaignMasterGroup(campaignUid, user.getUid(), UserInterfaceType.PLEASE_CALL_ME);
        }

        Campaign campaign = campaignBroker.load(campaignUid);
        log.info("Adding tag to user with number {}, tag is {}, campaign {}", phoneNumber, tag, campaign);

        groupBroker.assignMembershipTopics(user.getUid(), campaign.getMasterGroup().getUid(),
                false, Collections.singleton(user.getUid()), Collections.singleton(tag), true);

        log.info("Successfully tagged user");

        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "initiated/pcm/campaign/join/{campaignUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Receive an incoming please call me, and send a welcome message, or add to group")
    public ResponseEntity receivePleaseCallMe(@PathVariable String campaignUid,
                                              @RequestParam String secret,
                                              @RequestParam(value = "from_number") String phoneNumber,
                                              @RequestParam(value = "content") String message,
                                              @RequestParam(value = "to_number") String toNumber) {
        ResponseEntity hygieneCheck = checkPcmForSecretAndFormat(secret, message);
        if (hygieneCheck != null)
            return hygieneCheck;

        User user = userManager.loadOrCreateUser(phoneNumber, UserInterfaceType.INCOMING_SMS);
        final String callback = PhoneNumberUtil.formattedNumber(toNumber);
        log.info("PCM received, responding with callback number: {}, incoming to number: {}, from: {}, msg: {}", callback, toNumber, phoneNumber, message);

        if (!campaignBroker.hasUserEngaged(campaignUid, user.getUid())) {
            campaignBroker.recordEngagement(campaignUid, user.getUid(), UserInterfaceType.PLEASE_CALL_ME, message);
            campaignTextBroker.checkForAndTriggerCampaignText(campaignUid, user.getUid(), callback, UserInterfaceType.PLEASE_CALL_ME);
        } else {
            campaignTextBroker.handleCampaignTextResponse(campaignUid, user.getUid(), message, UserInterfaceType.PLEASE_CALL_ME);
        }
        return ResponseEntity.ok().build();
    }

    private ResponseEntity checkPcmForSecretAndFormat(String secret, String message) {
        if (!secret.equals(INBOUND_PCM_TOKEN)) {
            log.error("Invalid inbound token received: {}", secret);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<String> keyStrings = Arrays.asList("please call", "call me", "to call", "PCM");
        boolean likelyPlsCallMe = keyStrings.stream().anyMatch(string ->
                StringUtils.containsIgnoreCase(message, string));

        if (!likelyPlsCallMe) {
            log.info("not a please call me, must be different message: ", message);
            return ResponseEntity.ok().build();
        }

        return null;
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