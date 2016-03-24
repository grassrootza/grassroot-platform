package za.org.grassroot.services.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.LogBookLogRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.domain.MessageProtocol;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.services.*;
import za.org.grassroot.services.util.CacheUtilService;

import java.util.List;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = LoggerFactory.getLogger(EventNotificationConsumer.class);

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    MeetingNotificationService meetingNotificationService;

    @Autowired
    MessageSendingService messageSendingService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    CacheUtilService cacheUtilService;

    @Autowired
    LogBookRepository logBookRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    LogBookLogRepository logBookLogRepository;

    @Autowired
    AccountManagementService accountManagementService;

    @Autowired
    PasswordTokenService passwordTokenService;


    /*
    change this to add messages or stop messages altogether by leaving it empty.
     */
    private final String[] welcomeMessages = new String[] {
            "sms.welcome.1",
            "sms.welcome.2",
            "sms.welcome.3"
    };

    @JmsListener(destination = "event-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void sendNewEventNotifications(EventDTO event) {

        log.trace("sendNewEventNotifications... <" + event.toString() + ">");

        for (User user : getAllUsersForGroup(event.getEventObject())) {
            cacheUtilService.clearRsvpCacheForUser(user,event.getEventType());
            sendNewMeetingMessage(user, event);
        }
    }


    @JmsListener(destination = "event-changed", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendChangedEventNotifications(EventChanged eventChanged) {
        log.info("sendChangedEventNotifications... <" + eventChanged.toString() + ">");
        for (User user : getAllUsersForGroup(eventChanged.getEvent().getEventObject())) {
            //generate message based on user language
            log.info("sendChangedEventNotifications...user..." + user.getPhoneNumber() + "...event..." + eventChanged.getEvent().getId() + "...version..." + eventChanged.getEvent().getVersion() + "...start time changed..." + eventChanged.isStartTimeChanged() + "...starttime..." + eventChanged.getEvent().getEventStartDateTime());
            String message = meetingNotificationService.createChangeMeetingNotificationMessage(user, eventChanged.getEvent());
            if (!eventLogManagementService.changeNotificationSentToUser(eventChanged.getEvent().getEventUid(), user.getUid(), message)
                    && (!eventLogManagementService.userRsvpNoForEvent(eventChanged.getEvent().getEventUid(), user.getUid())
                    || eventChanged.isStartTimeChanged())) {
                log.info("sendChangedEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventChange, eventChanged.getEvent().getEventUid(),
                                                         user.getUid(), message);
            }
        }

    }

    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(EventDTO event) {
        log.trace("sendCancelledEventNotifications... <" + event.toString() + ">");
        for (User user : getAllUsersForGroup(event.getEventObject())) {
            //generate message based on user language
            String message = meetingNotificationService.createCancelMeetingNotificationMessage(user, event);
            if (!eventLogManagementService.cancelNotificationSentToUser(event.getEventUid(), user.getUid())
                    && !eventLogManagementService.userRsvpNoForEvent(event.getEventUid(), user.getUid())) {
                log.info("sendCancelledEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventCancelled, event.getEventUid(), user.getUid(), message);
            }
        }

    }

    @JmsListener(destination = "user-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void groupUserAdded(NewGroupMember newGroupMember) {
        log.info("groupUserAdded...<" + newGroupMember.toString());

        // check for events on this group level
        List<Event> upComingEvents = eventManagementService.getUpcomingEventsForGroupAndParentGroups(newGroupMember.getGroup());
        if (upComingEvents != null) {
            for (Event upComingEvent : upComingEvents) {
                sendNewMeetingMessage(newGroupMember.getNewMember(), new EventDTO(upComingEvent));
            }
        }

    }

    @JmsListener(destination = "event-reminder", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendEventReminder(EventDTO event) {
        log.info("sendEventReminder...event.id..." + event.getId());
        for (User user : getAllUsersForGroup(event.getEventObject())) {
            sendMeetingReminderMessage(user, event);
        }

    }

    /*
    N.B.
    If logic changes here also see method sendNewLogbookNotification if it must change there as well
     */
    @JmsListener(destination = "logbook-reminders", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    @Transactional
    public void sendLogBookReminder(LogBookDTO logBookDTO) {
        log.info("sendLogBookReminder...logBook..." + logBookDTO);

        Group  group = groupManagementService.loadGroup(logBookDTO.getGroupId());
        LogBook logBook = logBookRepository.findOne(logBookDTO.getGroupId());

        if (logBook.isAllGroupMembersAssigned()) {
            for (User user : groupManagementService.getUsersInGroupNotSubGroups(group.getId())) {
                sendLogBookReminderMessage(user, group, logBook);
            }

        } else {
            for (User user : logBook.getAssignedMembers()) {
                sendLogBookReminderMessage(user, group, logBook);
            }
        }
        // reduce number of reminders to send and calculate new reminder minutes
        logBook.setNumberOfRemindersLeftToSend(logBook.getNumberOfRemindersLeftToSend() - 1);
        if (logBook.getReminderMinutes() < 0) {
            logBook.setReminderMinutes(DateTimeUtil.numberOfMinutesForDays(7));
        } else {
            logBook.setReminderMinutes(logBook.getReminderMinutes() + DateTimeUtil.numberOfMinutesForDays(7));
        }
    }

    @JmsListener(destination = "manual-reminder", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendManualEventReminder(EventDTO event) {
        log.info("sendManualEventReminder...event.id..." + event.getId());
        for (User user : getAllUsersForGroup(event.getEventObject())) {
            sendManualReminderMessage(user, event);
        }

    }

    @JmsListener(destination = "vote-results", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendVoteResults(EventWithTotals event) {
        log.info("sendVoteResults...event.id..." + event.getEventDTO().getId());
        for (User user : getAllUsersForGroup(event.getEventDTO().getEventObject())) {
            sendVoteResultsToUser(user, event);
        }

    }

    @JmsListener(destination = "clear-groupcache", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void clearGroupCache(EventDTO event) {
        log.info("clearGroupCache...event.id..." + event.getId());
        cacheUtilService.clearCacheForAllUsersInGroup(event);


    }
    @JmsListener(destination = "generic-async", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void genericAsyncProcessor(GenericAsyncDTO genericAsyncDTO) {
        log.info("genericAsyncProcessor...identifier..." + genericAsyncDTO.getIdentifier() + "...object..." + genericAsyncDTO.getObject().toString());
        switch (genericAsyncDTO.getIdentifier()) {
            case "welcome-messages":
                sendWelcomeMessages((UserDTO) genericAsyncDTO.getObject());
                break;
            default:
                log.info("genericAsyncProcessor NO implementation for..." + genericAsyncDTO.getIdentifier());
        }

    }

    @JmsListener(destination = "new-logbook", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    @Transactional
    public void sendNewLogbookNotification(LogBookDTO logBookDTO) {
        log.info("sendNewLogbookNotification...id..." + logBookDTO.getId());
        Group  group = groupManagementService.loadGroup(logBookDTO.getGroupId());
        Account account = accountManagementService.findAccountForGroup(group);
        LogBook logBook = logBookRepository.findOne(logBookDTO.getId());

        log.info("Found this account for the group ..." + (account == null ? " none" : account.getAccountName()));

        if (account != null && account.isLogbookExtraMessages()) {
            //send messages to paid for groups using the same logic as the reminders - sendLogBookReminder method
            //so if you make changes here also make the changes there
            if (logBook.isAllGroupMembersAssigned()) {
                for (User user : group.getMembers()) {
                    sendNewLogbookNotificationMessage(user, group, logBook, false);
                }

            } else {
                for (User user : logBook.getAssignedMembers()) {
                    sendNewLogbookNotificationMessage(user, group, logBook, true);
                }
            }

        } else {
            log.info("sendNewLogbookNotification...id..." + logBookDTO.getId() + "...NOT a paid for group..." + group.getId());
        }

    }

    @JmsListener(destination = "processing-failure", containerFactory = "messagingJmsContainerFactory", concurrency = "1")
    public void sendReplyProcessingFailureNotification(User user){
        sendCouldNotProcessReply(user);
    }

    @JmsListener(destination = "send-verification", containerFactory = "messagingJmsContainerFactory", concurrency = "3")
    public void sendPhoneNumberVerificationCode(String phoneNumber){
        VerificationTokenCode token =  passwordTokenService.generateAndroidVerificationCode(phoneNumber);
        sendVerificationToken(token);

    }

    private void sendWelcomeMessages(UserDTO userDTO) {
        log.info("sendWelcomeMessages..." + userDTO + "...messages..." + welcomeMessages);
        for (String messageId : welcomeMessages) {
            String message = meetingNotificationService.createWelcomeMessage(messageId,userDTO);
            messageSendingService.sendMessage(message,userDTO.getPhoneNumber(),MessageProtocol.SMS);
        }
    }

    private void sendVerificationToken(VerificationTokenCode verificationTokenCode){
        log.info("sendingVerficationToken..."+verificationTokenCode.getUsername()+ "...token "+verificationTokenCode.getCode());
        String message =verificationTokenCode.getCode();

        messageSendingService.sendMessage(message,verificationTokenCode.getUsername(),MessageProtocol.SMS);
    }

    private void sendVoteResultsToUser(User user, EventWithTotals eventWithTotals) {
        //generate message based on user language
        EventDTO event = eventWithTotals.getEventDTO();
        RSVPTotalsDTO totalsDTO = eventWithTotals.getRsvpTotalsDTO();
        String message = meetingNotificationService.createVoteResultsMessage(user, event,
                totalsDTO.getYes(),totalsDTO.getNo(),totalsDTO.getMaybe(),totalsDTO.getNumberNoRSVP());
        if (!eventLogManagementService.voteResultSentToUser(event.getEventObject(), user)) {
            log.info("sendVoteResultsToUser...send message..." + message + "...to..." + user.getPhoneNumber());
            messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.EventResult, event.getEventUid(), user.getUid(), message);
        }

    }

    private List<User> getAllUsersForGroup(Event event) {
        if (event.isIncludeSubGroups()) {
            return groupManagementService.getAllUsersInGroupAndSubGroups(event.getAppliesToGroup().getId());
        } else {
            return groupManagementService.getUsersInGroupNotSubGroups(event.getAppliesToGroup().getId());
        }
    }

    private void sendNewMeetingMessage(User user, EventDTO event) {
        //generate message based on user language
        String message = meetingNotificationService.createMeetingNotificationMessage(user, event);
        Event meeting = eventManagementService.loadEvent(event.getId()); // todo: switch to use Uids, soon
        if (!eventLogManagementService.notificationSentToUser(meeting,user)) {
            log.info("sendNewEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
            messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.EventNotification, event.getEventObject().getUid(),
                                                     user.getUid(), message);
        }

    }

    private void sendMeetingReminderMessage(User target, EventDTO event) {
        //generate message based on target language
        String message = meetingNotificationService.createMeetingReminderMessage(target, event);
        /*
        Do not send vote reminder if the target already voted (userRsvpForEvent)
         */
        if (event.getEventType() == EventType.VOTE) {
            if (!eventLogManagementService.reminderSentToUser(event.getEventObject(), target)
                    && !eventLogManagementService.userRsvpForEvent(event.getEventObject(), target)) {
                sendMeetingReminderMessageAction(target,event,message);
            }

        } else {
        /*
        Do not send meeting reminder if the target already rsvp'ed "no"
         */

            if (!eventLogManagementService.reminderSentToUser(event.getEventObject(), target)
                    && !eventLogManagementService.userRsvpNoForEvent(event.getEventUid(), target.getUid())) {
                sendMeetingReminderMessageAction(target,event,message);
            }
        }
    }

    private void sendManualReminderMessage(User user, EventDTO event) {
        //generate message based on user language if message not captured by the user
        String message = event.getMessage();
        if (message == null || message.trim().equals("")) {
            message = meetingNotificationService.createMeetingReminderMessage(user, event);
        }
        /*
        Do not send vote reminder if the user already voted (userRsvpForEvent)
         */
        if (event.getEventType() == EventType.VOTE) {
            if (!eventLogManagementService.userRsvpForEvent(event.getEventObject(), user)) {
                sendManualMessageAction(user,event,message);
            }

        } else {
        /*
        Do not send meeting reminder if the user already rsvp'ed "no"
         */

            if (!eventLogManagementService.userRsvpNoForEvent(event.getEventUid(), user.getUid())) {
                sendManualMessageAction(user,event,message);
            }

        }

    }

    private void sendMeetingReminderMessageAction(User user, EventDTO event, String message) {
        log.info("sendMeetingReminderMessage...send message..." + message + "...to..." + user.getPhoneNumber());
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        eventLogManagementService.createEventLog(EventLogType.EventReminder, event.getEventUid(), user.getUid(), message);

    }

    private void sendManualMessageAction(User user, EventDTO event, String message) {
        log.info("sendManualMessageAction...send message..." + message + "...to..." + user.getPhoneNumber());
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        eventLogManagementService.createEventLog(EventLogType.EventManualReminder, event.getEventUid(), user.getUid(), message);

    }

    private void sendLogBookReminderMessage(User user,Group group, LogBook logBook) {
        log.info("sendLogBookReminderMessage...user..." + user.getPhoneNumber() + "...logbook..." + logBook.getMessage());
        String message = meetingNotificationService.createLogBookReminderMessage(user,group,logBook);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        logBookLogRepository.save(new LogBookLog(logBook.getId(),message,user.getId(),group.getId(),user.getPhoneNumber()));

    }
    private void sendNewLogbookNotificationMessage(User user,Group group, LogBook logBook, boolean assigned) {
        log.info("sendNewLogbookNotificationMessage...user..." + user.getPhoneNumber() + "...logbook..." + logBook.getMessage());
        String message = meetingNotificationService.createNewLogBookNotificationMessage(user,group,logBook, assigned);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        logBookLogRepository.save(new LogBookLog(logBook.getId(),message,user.getId(),group.getId(),user.getPhoneNumber()));

    }

    public void sendCouldNotProcessReply(User user){
        String message = meetingNotificationService.createReplyFailureMessage(user);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
    }

}

