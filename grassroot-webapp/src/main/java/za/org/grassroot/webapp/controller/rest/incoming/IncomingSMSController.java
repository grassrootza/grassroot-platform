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
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by paballo on 2016/02/17.
 */
@Api("/api/inbound/sms/")
@RestController @Slf4j
@RequestMapping("/api/inbound/sms/")
public class IncomingSMSController {

    private final UserResponseBroker userResponseBroker;

    private final UserLogRepository userLogRepository;
    private final NotificationService notificationService;
    private final GroupLogRepository groupLogRepository;
    private final UserManagementService userManager;

    private final MessageAssemblingService messageAssemblingService;
    private final MessagingServiceBroker messagingServiceBroker;

    private static final String FROM_PARAMETER ="fn";
    private static final String MESSAGE_TEXT_PARAM ="ms";

    private static final Duration NOTIFICATION_WINDOW = Duration.of(6, ChronoUnit.HOURS);

    @Autowired
    public IncomingSMSController(UserResponseBroker userResponseBroker, UserManagementService userManager, EventLogBroker eventLogManager,
                                 MessageAssemblingService messageAssemblingService, MessagingServiceBroker messagingServiceBroker,
                                 UserLogRepository userLogRepository, NotificationService notificationService,
                                 GroupLogRepository groupLogRepository) {
        this.userResponseBroker = userResponseBroker;

        this.userManager = userManager;
        this.messageAssemblingService = messageAssemblingService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.userLogRepository = userLogRepository;
        this.notificationService = notificationService;
        this.groupLogRepository = groupLogRepository;
    }


    @RequestMapping(value = "incoming", method = RequestMethod.GET)
    @ApiOperation(value = "Send in an incoming SMS", notes = "For when an end-user SMSs a reply to the platform. " +
            "Parameters are phone number and the message sent")
    public void receiveSms(@RequestParam(value = FROM_PARAMETER) String phoneNumber,
                           @RequestParam(value = MESSAGE_TEXT_PARAM) String msg) {

        log.info("Inside IncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);
        User user = userManager.findByInputNumber(phoneNumber);
        if (user == null) {
            log.warn("Message {} from unknown user: {}", msg, phoneNumber);
            return;
        }

        final String trimmedMsg = msg.trim();
        EntityForUserResponse likelyEntity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), false);

        if (likelyEntity == null) {
            log.info("User response is {}, type {} and cannot find an entity waiting for response ... handling unknown message");
            handleUnknownResponse(user, trimmedMsg);
            return;
        }

        userResponseBroker.recordUserResponse(user.getUid(),
                likelyEntity.getJpaEntityType(),
                likelyEntity.getUid(),
                trimmedMsg);

        // note : send a response confirming?
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
            GroupLog groupLog = new GroupLog(entry.getKey(), user, GroupLogType.USER_SENT_UNKNOWN_RESPONSE, user.getId(),
                    description.substring(0, Math.min(255, description.length()))); // since could be very long ...
            groupLogRepository.save(groupLog);
        }

    }

    private void notifyUnableToProcessReply(User user) {
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messagingServiceBroker.sendSMS(message, user.getPhoneNumber(), true);
    }

}