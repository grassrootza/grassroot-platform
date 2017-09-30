package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.geo.AddressLog;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/02/17.
 */

@RestController
@RequestMapping("/sms/")
public class AATIncomingSMSController {

    private static final Logger log = LoggerFactory.getLogger(AATIncomingSMSController.class);
    private static final String patternToMatch = "\\b(?:yes|no|abstain|maybe)\\b";

    private final EventBroker eventBroker;
    private final VoteBroker voteBroker;
    private final UserLogRepository userLogRepository;
    private final NotificationService notificationService;
    private GroupLogRepository groupLogRepository;
    private final UserManagementService userManager;
    private final EventLogBroker eventLogManager;
    private final MessageAssemblingService messageAssemblingService;
    private final MessagingServiceBroker messagingServiceBroker;

    private static final String fromNumber ="fn";
    private static final String message ="ms";

    @Autowired
    public AATIncomingSMSController(EventBroker eventBroker, UserManagementService userManager, EventLogBroker eventLogManager,
                                    MessageAssemblingService messageAssemblingService, MessagingServiceBroker messagingServiceBroker,
                                    VoteBroker voteBroker, UserLogRepository userLogRepository, NotificationService notificationService,
                                    GroupLogRepository groupLogRepository) {
        this.eventBroker = eventBroker;
        this.userManager = userManager;
        this.eventLogManager = eventLogManager;
        this.messageAssemblingService = messageAssemblingService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.voteBroker = voteBroker;
        this.userLogRepository = userLogRepository;
        this.notificationService = notificationService;
        this.groupLogRepository = groupLogRepository;
    }


    @RequestMapping(value = "incoming", method = RequestMethod.GET)
    public void receiveSms(@RequestParam(value = fromNumber) String phoneNumber,
                           @RequestParam(value = message) String msg) {


        log.info("Inside AATIncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);

        User user = userManager.findByInputNumber(phoneNumber);
        String trimmedMsg =  msg.toLowerCase().trim();

        if (user == null || !isValidInput(trimmedMsg)) {
            if (user != null) {
                notifyUnableToProcessReply(user);
            }
            return;
        }

        EventRSVPResponse response = EventRSVPResponse.fromString(message);
        boolean isYesNoResponse = response == EventRSVPResponse.YES || response == EventRSVPResponse.NO || response == EventRSVPResponse.MAYBE;

        List<Event> outstandingVotes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
        List<Event> outstandingYesNoVotes = outstandingVotes.stream()
                .filter(vote -> vote.getTags().length == 0)
                .collect(Collectors.toList());

        List<Event> outstandingOptionsVotes = outstandingVotes.stream()
                .filter(vote -> hasVoteOption(trimmedMsg, vote))
                .collect(Collectors.toList());

        List<Event> outstandingMeetings = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING);


        if (isYesNoResponse && !outstandingMeetings.isEmpty())  // user sent yes-no response and there is a meeting awaiting yes-no response
            eventLogManager.rsvpForEvent(outstandingMeetings.get(0).getUid(), user.getUid(), response); // recording rsvp for meeting

        else if (isYesNoResponse && !outstandingYesNoVotes.isEmpty()) // user sent yes-no response and there is a vote awaiting yes-no response
            voteBroker.recordUserVote(user.getUid(), outstandingYesNoVotes.get(0).getUid(), trimmedMsg); // recording user vote

        else if (!outstandingOptionsVotes.isEmpty()) // user sent something other then yes-no, and there is a vote that has this option (tag)
            voteBroker.recordUserVote(user.getUid(), outstandingOptionsVotes.get(0).getUid(), trimmedMsg); // recording user vote

        else // we have not found any meetings or votes that this could be response to
            handleUnknownResponse(user, trimmedMsg);

    }

    private void handleUnknownResponse(User user, String trimmedMsg) {

        notifyUnableToProcessReply(user);

        //todo(beegor), what interface type should be used here
        UserLog userLog = new UserLog(user.getUid(), UserLogType.SENT_INVALID_SMS_MESSAGE, trimmedMsg, UserInterfaceType.ANDROID);
        userLogRepository.save(userLog);


        List<Notification> recentNotifications = notificationService.fetchAndroidNotificationsSince(user.getUid(), Instant.now().minus(6, ChronoUnit.HOURS));

        for (Notification notification : recentNotifications) {

            Map<ActionLog, Group> logs = getNotificationLog(notification);

            for (Map.Entry<ActionLog, Group> entry : logs.entrySet()) {
                ActionLog aLog = entry.getKey();
                Group group = entry.getValue();

                if (user.getGroups().contains(group)) { // todo(beegor) check with Luke: this check might not be necessary, if user got notification regarding this group, he must be a member of it, right ?
                    String notificationType = getNotificationType(aLog);
                    String description = MessageFormat.format("User {0} sent response we can't understand after being sent a notification of type: {} in this group", user.getName(), notificationType);
                    GroupLog groupLog = new GroupLog(group, user, GroupLogType.USER_SENT_UNKNOWN_RESPONSE, user.getId(), description);
                    groupLogRepository.save(groupLog);
                }
            }
        }
    }

    private String getNotificationType(ActionLog aLog) {
        if (aLog instanceof EventLog)
            return "Event log: " + ((EventLog) aLog).getEventLogType().name();
        else if (aLog instanceof TodoLog)
            return "ToDo log: " + ((TodoLog) aLog).getType().name();
        else if (aLog instanceof GroupLog)
            return "Group log: " + ((GroupLog) aLog).getGroupLogType().name();
        else if (aLog instanceof UserLog)
            return "User log: " + ((UserLog) aLog).getUserLogType().name();
        else if (aLog instanceof AccountLog)
            return "Account log: " + ((AccountLog) aLog).getAccountLogType().name();
        else if (aLog instanceof AddressLog)
            return "Address log: " + ((AddressLog) aLog).getType().name();
        else if (aLog instanceof LiveWireLog)
            return "LiveWire log: " + ((LiveWireLog) aLog).getType().name();
        else return "Unknown notification type";
    }

    private Map<ActionLog, Group> getNotificationLog(Notification notification) {
        Map<ActionLog, Group> logGroupMap = new HashMap<>();

        if (notification.getEventLog() != null)
            logGroupMap.put(notification.getEventLog(), notification.getEventLog().getEvent().getAncestorGroup());

        else if (notification.getTodoLog() != null)
            logGroupMap.put(notification.getTodoLog(), notification.getTodoLog().getTodo().getAncestorGroup());

            //todo(bigor) check with Luke: are groupLog and liveWire relevant for this? Are there notifications sent for those logs?
        else if (notification.getGroupLog() != null)
            logGroupMap.put(notification.getGroupLog(), notification.getGroupLog().getGroup());

        else if (notification.getLiveWireLog() != null)
            logGroupMap.put(notification.getLiveWireLog(), notification.getLiveWireLog().getAlert().getGroup());

        //todo(bigor) check with Luke: what about UserLog, AccountLog, AddressLog, they seem to not be related with any group, so we are probably not interested in those here

        return logGroupMap;
    }

    private boolean hasVoteOption(String responseMsg, Event vote) {
        if (vote.getTags() != null) {
            for (String tag : vote.getTags()) {
                if (tag.equalsIgnoreCase(responseMsg))
                    return true;
            }
        }
        return false;
    }


    private void notifyUnableToProcessReply(User user) {
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messagingServiceBroker.sendSMS(message, user.getPhoneNumber(), true);
    }

    private boolean isValidInput(String message){
        Pattern regex = Pattern.compile(patternToMatch);
        Matcher regexMatcher = regex.matcher(message);
        return  regexMatcher.find();
    }


}
