package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.repository.LogBookLogRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.domain.MessageProtocol;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.services.*;
import za.org.grassroot.services.util.CacheUtilService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = LoggerFactory.getLogger(EventNotificationConsumer.class);

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    MeetingNotificationService meetingNotificationService;

    @Autowired
    MessageSendingService messageSendingService;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    EventBroker eventBroker;

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

    @Autowired
    GcmService gcmService;

    @Autowired
    MessageChannel requestChannel;

    @Autowired
    NotificationService notificationService;

    /*
    change this to add messages or stop messages altogether by leaving it empty.
     */
    private final String[] welcomeMessages = new String[] {
            "sms.welcome.1",
            "sms.welcome.2",
            "sms.welcome.3"
    };

    @Transactional
    @JmsListener(destination = "event-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void sendNewEventNotifications(String eventUid) {

        Event event = eventBroker.load(eventUid);
        EventDTO eventDTO = new EventDTO(event);

        log.info("sendNewEventNotifications... <" + event.toString() + ">");
        List<User> usersToNotify = getUsersForEvent(event);
        log.info("sendNewEventNotifications, sending to: {} users", usersToNotify.size());

        for (User user : getUsersForEvent(event)) {
            cacheUtilService.clearRsvpCacheForUser(user,event.getEventType());
            sendNewEventMessage(user, eventDTO);
        }
    }

    @Transactional
    @JmsListener(destination = "event-changed", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendChangedEventNotifications(EventChanged eventChanged) {
        // note: have to reload entity instead of get from DTO, else get group members is going to throw errors
        // but on other hand, the EventChanged entity contains important information, so not changing to UID as elsewhere
        log.info("sendChangedEventNotifications... <" + eventChanged.toString() + ">");
        Event event = eventBroker.load(eventChanged.getEvent().getEventUid());
        for (User user : getUsersForEvent(event)) {
            //generate message based on user language
            log.info("sendChangedEventNotifications...user..." + user.getPhoneNumber() + "...event..." + eventChanged.getEvent().getId() + "...version..." + eventChanged.getEvent().getVersion() + "...start time changed..." + eventChanged.isStartTimeChanged() + "...starttime..." + eventChanged.getEvent().getEventStartDateTime());
            String message = meetingNotificationService.createChangeMeetingNotificationMessage(user, eventChanged.getEvent());
            if (!eventLogManagementService.changeNotificationSentToUser(eventChanged.getEvent().getEventUid(), user.getUid(), message)
                    && (!eventLogManagementService.userRsvpNoForEvent(eventChanged.getEvent().getEventUid(), user.getUid())
                    || eventChanged.isStartTimeChanged())) {
                log.info("sendChangedEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
               EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventChange, eventChanged.getEvent().getEventUid(),
                        user.getUid(), message);
               // messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                Notification notification = notificationService.createNotification(user,eventLog,NotificationType.EVENT);
                messageSendingService.sendMessage(notification);

            }
        }

    }

    @Transactional
    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(String eventUid) {
        Event event = eventBroker.load(eventUid);
        EventDTO eventDTO = (event.getEventType().equals(EventType.MEETING)) ? new EventDTO((Meeting) event) : new EventDTO(event);
        log.trace("sendCancelledEventNotifications... <" + event.toString() + ">");
        for (User user : getUsersForEvent(event)) {
            //generate message based on user language
            String message = meetingNotificationService.createCancelMeetingNotificationMessage(user, eventDTO);
            if (!eventLogManagementService.cancelNotificationSentToUser(event.getUid(), user.getUid())
                    && !eventLogManagementService.userRsvpNoForEvent(event.getUid(), user.getUid())) {
                log.info("sendCancelledEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
               EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventCancelled, event.getUid(), user.getUid(), message);
                Notification notification= notificationService.createNotification(user,eventLog,NotificationType.EVENT);
                messageSendingService.sendMessage(notification);
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
                sendNewEventMessage(newGroupMember.getNewMember(), new EventDTO(upComingEvent));
            }
        }

    }

    @Transactional
    @JmsListener(destination = "event-reminder", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendEventReminder(String eventUid) {
        Event event = eventBroker.load(eventUid);
        log.info("sendEventReminder...event.id..." + event.getId());
        for (User user : getUsersForEvent(event)) {
            sendMeetingReminderMessage(user, event);
        }

    }

    @Transactional
    @JmsListener(destination = "meeting-responses", containerFactory = "messagingJmsContainerFactory")
    public void sendMeetingRsvpTotals(String meetingUid) {
        Meeting meeting = eventBroker.loadMeeting(meetingUid);
        log.info("Sending meeting RSVP totals for meeting={}", meeting.toString());
        User user = meeting.getCreatedByUser();
        ResponseTotalsDTO totals = eventLogManagementService.getResponseCountForEvent(meeting);
        String message = meetingNotificationService.createMeetingRsvpTotalMessage(user, new EventDTO(meeting), totals);
        EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventRsvpTotalMessage, meetingUid,
                                                                     user.getUid(), message);
        Notification notification = notificationService.createNotification(user, eventLog, NotificationType.EVENT);
        messageSendingService.sendMessage(notification);
    }

    /*
    N.B.
    If logic changes here also see method sendNewLogbookNotification if it must change there as well
     */
    @Transactional
    @JmsListener(destination = "logbook-reminders", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendLogBookReminder(LogBookDTO logBookDTO) {
        log.info("sendLogBookReminder...logBook..." + logBookDTO);

        LogBook logBook = logBookRepository.findOne(logBookDTO.getId());
        Group group = logBook.resolveGroup();

        if (logBook.isAllGroupMembersAssigned()) {
            for (User user : group.getMembers()) {
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

    @Transactional
    @JmsListener(destination = "manual-reminder", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendManualEventReminder(EventDTO event) {
        log.info("sendManualEventReminder...event.id..." + event.getId());
        for (User user : getUsersForEvent(event.getEventObject())) {
            sendManualReminderMessage(user, event);
        }
    }

    @Transactional
    @JmsListener(destination = "vote-results", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendVoteResults(String voteUid) {
        log.info("sendVoteResults...vote.id..." + voteUid);
        Vote vote = (Vote) eventBroker.load(voteUid);
        ResponseTotalsDTO rsvpTotalsDTO = eventLogManagementService.getVoteResultsForEvent(vote);
        EventWithTotals eventWithTotals = new EventWithTotals(new EventDTO(vote), rsvpTotalsDTO);

        for (User user : getUsersForEvent(vote)) {
            sendVoteResultsToUser(user, eventWithTotals);
        }
    }

    @Transactional
    @JmsListener(destination = "free-form", containerFactory = "messagingJmsContainerFactory", concurrency = "1")
    public void sendFreeFormMessage(Map<String, String> message) {
        log.info("sendFreeFormMessage... groupUid={}, message={}", message.get("group-uid"), message.get("message"));
        Set<User> members = groupBroker.load(message.get("group-uid")).getMembers();
        String messageText = message.get("message");
        for (User user : members) {
            // todo: record this for paid group / account ...
            messageSendingService.sendMessage(messageText, user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.FreeFormMessage, null, user.getUid(), messageText);

        }

    }

    /*
    As far as I can tell, this isn't used anywhere (instead the methods loop in here and clear the cache themselves.
    Since it's the only reason for cache manager wiring user manager, am going to comment out until / unless needed again.
     */
    /*@JmsListener(destination = "clear-groupcache", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void clearGroupCache(EventDTO event) {
        log.info("clearGroupCache...event.id..." + event.getId());
        cacheUtilService.clearCacheForAllUsersInGroup(event);


    }*/

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
        LogBook logBook = logBookRepository.findOne(logBookDTO.getId());
        Group  group = (Group) logBook.getParent();
        Account account = accountManagementService.findAccountForGroup(group);

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
        ResponseTotalsDTO totalsDTO = eventWithTotals.getResponseTotalsDTO();
        String message = meetingNotificationService.createVoteResultsMessage(user, event,
                totalsDTO.getYes(),totalsDTO.getNo(),totalsDTO.getMaybe(),totalsDTO.getNumberNoRSVP());
        if (!eventLogManagementService.voteResultSentToUser(event.getEventUid(), user.getUid())) {
            log.info("sendVoteResultsToUser...send message..." + message + "...to..." + user.getPhoneNumber());
           // messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            EventLog eventLog =eventLogManagementService.createEventLog(EventLogType.EventResult, event.getEventUid(), user.getUid(), message);
            Notification notification = notificationService.createNotification(user,eventLog,NotificationType.EVENT);
            messageSendingService.sendMessage(notification);
        }

    }

    private List<User> getUsersForEvent(Event event) {
        // todo: replace this with calling the parent and/or just using assigned members
        if (event.isIncludeSubGroups()) {
            return userManager.fetchByGroup(event.resolveGroup().getUid(), true);
        } else if(event.isAllGroupMembersAssigned()) {
            return new ArrayList<>(event.resolveGroup().getMembers());
        } else {
            return new ArrayList<>(event.getAssignedMembers());
        }
    }

    private void sendNewEventMessage(User user, EventDTO event) {
        //generate message based on user language
        String message = meetingNotificationService.createMeetingNotificationMessage(user, event);
        Event eventEntity = eventBroker.load(event.getEventUid());
        if (!eventLogManagementService.notificationSentToUser(eventEntity, user)) {
            log.info("sendNewEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
            EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventNotification,
                                                                         event.getEventUid(), user.getUid(), message);

            Notification notification = notificationService.createNotification(user,eventLog, NotificationType.EVENT);
            messageSendingService.sendMessage(notification);
            // messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            }
        }



    private void sendMeetingReminderMessage(User target, Event event) {
        EventDTO eventDTO = (event.getEventType().equals(EventType.MEETING)) ?
                new EventDTO((Meeting) event) :
                new EventDTO(event);
        String message = meetingNotificationService.createMeetingReminderMessage(target, eventDTO);
        if (event.getEventType() == EventType.VOTE) {
            // Do not send vote reminder if the target already voted (userRsvpForEvent)
            if (!eventLogManagementService.reminderSentToUser(event, target)
                    && !eventLogManagementService.userRsvpForEvent(event, target)) {
                sendMeetingReminderMessageAction(target,eventDTO,message);
            }

        } else {
            if (!eventLogManagementService.reminderSentToUser(event, target)
                    && !eventLogManagementService.userRsvpNoForEvent(event.getUid(), target.getUid())) {
                sendMeetingReminderMessageAction(target,eventDTO,message);
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
      //  messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventReminder, event.getEventUid(), user.getUid(), message);
        Notification notification = notificationService.createNotification(user,eventLog,NotificationType.EVENT);
        messageSendingService.sendMessage(notification);

    }

    private void sendManualMessageAction(User user, EventDTO event, String message) {
        log.info("sendManualMessageAction...send message..." + message + "...to..." + user.getPhoneNumber());
      //  messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        EventLog eventLog = eventLogManagementService.createEventLog(EventLogType.EventManualReminder, event.getEventUid(), user.getUid(), message);
        Notification notification = notificationService.createNotification(user,eventLog,NotificationType.EVENT);
        messageSendingService.sendMessage(notification);


    }

    private void sendLogBookReminderMessage(User user,Group group, LogBook logBook) {
        log.info("sendLogBookReminderMessage...user..." + user.getPhoneNumber() + "...logbook..." + logBook.getMessage());
        String message = meetingNotificationService.createLogBookReminderMessage(user,group,logBook);
        LogBookLog  logBookLog = logBookLogRepository.save(new LogBookLog(logBook.getId(),message,user.getId(),group.getId(),user.getPhoneNumber()));
        Notification notification = notificationService.createNotification(user, logBookLog,NotificationType.LOGBOOK);
        messageSendingService.sendMessage(notification);
    }

    private void sendNewLogbookNotificationMessage(User user,Group group, LogBook logBook, boolean assigned) {
        log.info("sendNewLogbookNotificationMessage...user..." + user.getPhoneNumber() + "...logbook..." + logBook.getMessage());
        String message = meetingNotificationService.createNewLogBookNotificationMessage(user,group,logBook, assigned);
        LogBookLog logBookLog = logBookLogRepository.save(new LogBookLog(logBook.getId(),message,user.getId(),group.getId(),user.getPhoneNumber()));
        Notification notification = notificationService.createNotification(user,logBookLog,NotificationType.LOGBOOK);
        messageSendingService.sendMessage(notification);
    }

    public void sendCouldNotProcessReply(User user){
        String message = meetingNotificationService.createReplyFailureMessage(user);
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
    }

    private String getRegistrationId(User user){
        return gcmService.getGcmKey(user);
    }


}

