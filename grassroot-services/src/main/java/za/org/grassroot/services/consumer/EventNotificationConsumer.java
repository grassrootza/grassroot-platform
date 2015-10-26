package za.org.grassroot.services.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.event.EventChangeEvent;
import za.org.grassroot.integration.domain.MessageProtocol;
import za.org.grassroot.integration.services.MessageSendingService;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.MeetingNotificationService;

import javax.swing.event.ChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static za.org.grassroot.core.enums.EventChangeType.EVENT_ADDED;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

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

    @JmsListener(destination = "event-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void sendNewEventNotifications(EventDTO event) {

        log.finest("sendNewEventNotifications... <" + event.toString() + ">");

        for (User user : getAllUsersForGroup(event.getEventObject())) {
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
            if (!eventLogManagementService.changeNotificationSentToUser(eventChanged.getEvent().getEventObject(), user, message)
                    && (!eventLogManagementService.userRsvpNoForEvent(eventChanged.getEvent().getEventObject(), user)
                    || eventChanged.isStartTimeChanged())) {
                log.info("sendChangedEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventChange, eventChanged.getEvent().getEventObject(), user, message);
            }
        }

    }

    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(EventDTO event) {
        log.finest("sendCancelledEventNotifications... <" + event.toString() + ">");
        for (User user : getAllUsersForGroup(event.getEventObject())) {
            //generate message based on user language
            String message = meetingNotificationService.createCancelMeetingNotificationMessage(user, event);
            if (!eventLogManagementService.cancelNotificationSentToUser(event.getEventObject(), user)
                    && !eventLogManagementService.userRsvpNoForEvent(event.getEventObject(), user)) {
                log.info("sendCancelledEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventCancelled, event.getEventObject(), user, message);
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

    @JmsListener(destination = "vote-results", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendVoteResults(EventWithTotals event) {
        log.info("sendVoteResults...event.id..." + event.getEventDTO().getId());
        for (User user : getAllUsersForGroup(event.getEventDTO().getEventObject())) {
            sendVoteResultsToUser(user, event);
        }

    }

    private void sendVoteResultsToUser(User user, EventWithTotals eventWithTotals) {
        //generate message based on user language
        EventDTO event = eventWithTotals.getEventDTO();
        RSVPTotalsDTO totalsDTO = eventWithTotals.getRsvpTotalsDTO();
        String message = meetingNotificationService.createVoteResultsMessage(user, event,
                totalsDTO.getYes(),totalsDTO.getNo(),totalsDTO.getInvalid(),totalsDTO.getNumberNoRSVP());
        if (!eventLogManagementService.voteResultSentToUser(event.getEventObject(), user)) {
            log.info("sendVoteResultsToUser...send message..." + message + "...to..." + user.getPhoneNumber());
            messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.EventResult, event.getEventObject(), user, message);
        }

    }

    private List<User> getAllUsersForGroup(Event event) {
        if (event.isIncludeSubGroups()) {
            return groupManagementService.getAllUsersInGroupAndSubGroups(event.getAppliesToGroup());
        } else {
            return event.getAppliesToGroup().getGroupMembers();
        }
    }

    private void sendNewMeetingMessage(User user, EventDTO event) {
        //generate message based on user language
        String message = meetingNotificationService.createMeetingNotificationMessage(user, event);
        if (!eventLogManagementService.notificationSentToUser(event.getEventObject(), user)) {
            log.info("sendNewEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
            messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.EventNotification, event.getEventObject(), user, message);
        }

    }

    private void sendMeetingReminderMessage(User user, EventDTO event) {
        //generate message based on user language
        String message = meetingNotificationService.createMeetingReminderMessage(user, event);
        /*
        Do not send vote reminder if the user already voted (userRsvpForEvent)
         */
        if (event.getEventType() == EventType.Vote) {
            if (!eventLogManagementService.reminderSentToUser(event.getEventObject(), user)
                    && !eventLogManagementService.userRsvpForEvent(event.getEventObject(), user)) {
                sendMeetingReminderMessageAction(user,event,message);
            }

        } else {
        /*
        Do not send meeting reminder if the user already rsvp'ed "no"
         */

            if (!eventLogManagementService.reminderSentToUser(event.getEventObject(), user)
                    && !eventLogManagementService.userRsvpNoForEvent(event.getEventObject(), user)) {
                sendMeetingReminderMessageAction(user,event,message);
            }

        }

    }
    private void sendMeetingReminderMessageAction(User user, EventDTO event, String message) {
        log.info("sendMeetingReminderMessage...send message..." + message + "...to..." + user.getPhoneNumber());
        messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
        eventLogManagementService.createEventLog(EventLogType.EventReminder, event.getEventObject(), user, message);

    }
}

