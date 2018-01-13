package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.user.UserManagementService;

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

    private final UserResponseBroker userResponseBroker;
    private final UserManagementService userManager;

    private final NotificationService notificationService;
    private final UserLogRepository userLogRepository;
    private final GroupLogRepository groupLogRepository;

    private final GroupBroker groupBroker;
    private final CampaignBroker campaignBroker;

    private final MessageAssemblingService messageAssemblingService;
    private final MessagingServiceBroker messagingServiceBroker;

    private static final String FROM_PARAMETER_REPLY ="fn";
    private static final String MESSAGE_TEXT_PARAM_REPLY ="ms";

    private static final String FROM_PARAMETER_NEW = "num";
    private static final String MSG_TEXT_PARAM_NEW = "mesg";

    private static final Duration NOTIFICATION_WINDOW = Duration.of(6, ChronoUnit.HOURS);

    @Autowired
    public IncomingSMSController(UserResponseBroker userResponseBroker, UserManagementService userManager, EventLogBroker eventLogManager,
                                 GroupBroker groupBroker, MessageAssemblingService messageAssemblingService, MessagingServiceBroker messagingServiceBroker,
                                 UserLogRepository userLogRepository, NotificationService notificationService,
                                 GroupLogRepository groupLogRepository, CampaignBroker campaignBroker) {
        this.userResponseBroker = userResponseBroker;

        this.userManager = userManager;
        this.groupBroker = groupBroker;
        this.messageAssemblingService = messageAssemblingService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.userLogRepository = userLogRepository;
        this.notificationService = notificationService;
        this.groupLogRepository = groupLogRepository;
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = "initiated/campaign", method = RequestMethod.GET)
    @ApiOperation(value = "Send in an incoming SMS, that is newly initiated, on campaign short code",
            notes = "For when we receive an SMS 'out of the blue', on the campaign number")
    public void receiveNewlyInitiatedSms(@RequestParam(value = FROM_PARAMETER_REPLY) String phoneNumber,
                                         @RequestParam(value = MESSAGE_TEXT_PARAM_REPLY) String message) {
        log.info("Inside receiving newly initiated SMS, received {} as message", message);
        User user = userManager.loadOrCreateUser(phoneNumber); // this may be a user we don't know
        Set<String> campaignTags = campaignBroker.getCampaignTags();
        log.info("active campaign tags = {}", campaignTags);
        // then: filter them
    }

    @RequestMapping(value = "initiated/group", method = RequestMethod.GET)
    @ApiOperation(value = "Incoming SMS, under the 'group' short code",
            notes = "For when we receive an out of the blue SMS, on the group number")
    public void receiveGroupSms(@RequestParam(value = FROM_PARAMETER_NEW) String phoneNumber,
                                @RequestParam(value = MSG_TEXT_PARAM_NEW) String message) {
        log.info("Inside receiving a message on group list, received {} as message", message);
        Map<String, String> groupJoinWords = groupBroker.getJoinWordsWithGroupIds();
        // todo : iterate hard on this to eg catch parts of words etc
        Set<String> groupMatches = groupJoinWords.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase().equalsIgnoreCase(message))
                .map(Map.Entry::getValue).collect(Collectors.toSet());

        if (groupMatches.size() == 1) {
            User user = userManager.loadOrCreateUser(phoneNumber);
            // as above, work through and strip out various words
            groupBroker.addMemberViaJoinCode(user.getUid(), groupMatches.iterator().next(), message,
                    UserInterfaceType.INCOMING_SMS);
        } else if (groupMatches.size() > 1) {
            // disambiguate somehow
            User user = userManager.loadOrCreateUser(phoneNumber);
            // for the moment, just adding the first
            groupBroker.addMemberViaJoinCode(user.getUid(), groupMatches.iterator().next(), message,
                    UserInterfaceType.INCOMING_SMS);
        } else {
            log.info("received a join word but don't know what to do with it");
        }
    }


    @RequestMapping(value = "reply", method = RequestMethod.GET)
    @ApiOperation(value = "Send in an incoming SMS, replying to one of our messages", notes = "For when an end-user SMSs a reply to the platform. " +
            "Parameters are phone number and the message sent")
    public void receiveSmsReply(@RequestParam(value = FROM_PARAMETER_REPLY) String phoneNumber,
                                @RequestParam(value = MESSAGE_TEXT_PARAM_REPLY) String msg) {

        log.info("Inside IncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);
        User user = userManager.findByInputNumber(phoneNumber);
        if (user == null) {
            log.warn("Message {} from unknown user: {}", msg, phoneNumber);
            return;
        }

        final String trimmedMsg = msg.trim();
        EntityForUserResponse likelyEntity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), false);

        if (likelyEntity == null || !checkValidityOfResponse(likelyEntity, trimmedMsg)) {
            log.info("User response is {}, type {} and cannot find an entity waiting for response ... handling unknown message");
            handleUnknownResponse(user, trimmedMsg);
            return;
        }

        userResponseBroker.recordUserResponse(user.getUid(),
                likelyEntity.getJpaEntityType(),
                likelyEntity.getUid(),
                trimmedMsg);

        // todo : send a response confirming?
    }

    // todo: move this into response broker checking, handle with more sophistication
    private boolean checkValidityOfResponse(EntityForUserResponse entity, String message) {
        switch (entity.getJpaEntityType()) {
            case MEETING:
                return EventRSVPResponse.fromString(message) != EventRSVPResponse.INVALID_RESPONSE;
            case VOTE:
                Vote vote = (Vote) entity;
                return !vote.getVoteOptions().isEmpty() ? vote.hasOption(message) :
                        EventRSVPResponse.fromString(message) != EventRSVPResponse.INVALID_RESPONSE;
            case TODO:
                Todo todo = (Todo) entity;
                return todo.getType().equals(TodoType.INFORMATION_REQUIRED); // todo : look for eg "yes it is done"
            default:
                return false;
        }
    }

    private void handleUnknownResponse(User user, String trimmedMsg) {

        log.info("Handling unexpected user SMS message");
        notifyUnableToProcessReply(user);

        log.info("Recording  unexpected user SMS message user log.");
        UserLog userLog = new UserLog(user.getUid(), UserLogType.SENT_UNEXPECTED_SMS_MESSAGE,
                trimmedMsg,
                UserInterfaceType.INCOMING_SMS);

        userLogRepository.save(userLog);

        List<Notification> recentNotifications = notificationService
                .fetchSentOrBetterSince(user.getUid(), Instant.now().minus(NOTIFICATION_WINDOW), null);

        Map<Group, String> messagesAndGroups = new HashMap<>();

        // todo: not the most elegant thing in the world, but can clean up later
        recentNotifications.stream().sorted(Comparator.comparing(Notification::getCreatedDateTime))
                .forEach(n -> {
                    Group group = n.getRelevantGroup();
                    if (group != null)
                        messagesAndGroups.put(group, n.getMessage());
                });

        log.info("okay, we have {} distinct groups", messagesAndGroups.size());

        for (Map.Entry<Group, String> entry : messagesAndGroups.entrySet()) {
            String description = MessageFormat.format("From user: {0}; likely responding to: {1}",
                    trimmedMsg, entry.getValue());
            GroupLog groupLog = new GroupLog(entry.getKey(), user,
                    GroupLogType.USER_SENT_UNKNOWN_RESPONSE, user, null, null,
                    description.substring(0, Math.min(255, description.length()))); // since could be very long ...
            groupLogRepository.save(groupLog);
        }

    }

    private void notifyUnableToProcessReply(User user) {
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messagingServiceBroker.sendSMS(message, user.getUid(), true);
    }

}