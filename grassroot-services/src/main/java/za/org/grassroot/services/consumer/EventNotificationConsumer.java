package za.org.grassroot.services.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.NewGroupMember;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.enums.EventLogType;
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
            sendNewMeetingMessage(user,event);
        }
    }


    @JmsListener(destination = "event-changed", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendChangedEventNotifications(EventChanged eventChanged) {
        log.info("sendChangedEventNotifications... <" + eventChanged.toString() + ">");
        for (User user : getAllUsersForGroup(eventChanged.getEvent().getEventObject())) {
            //generate message based on user language
            log.info("sendChangedEventNotifications...user..." + user.getPhoneNumber() + "...event..." + eventChanged.getEvent().getId() + "...version..." + eventChanged.getEvent().getVersion() + "...start time changed..." + eventChanged.isStartTimeChanged() + "...starttime..." + eventChanged.getEvent().getEventStartDateTime());
            String message = meetingNotificationService.createChangeMeetingNotificationMessage(user,eventChanged.getEvent());
            if (!eventLogManagementService.changeNotificationSentToUser(eventChanged.getEvent().getEventObject(), user, message)
                    && (!eventLogManagementService.userRsvpNoForEvent(eventChanged.getEvent().getEventObject(),user)
                    || eventChanged.isStartTimeChanged())) {
                log.info("sendChangedEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventChange,eventChanged.getEvent().getEventObject(),user,message);
            }
        }

    }

    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(EventDTO event) {
        log.finest("sendCancelledEventNotifications... <" + event.toString() + ">");
        for (User user : getAllUsersForGroup(event.getEventObject())) {
            //generate message based on user language
            String message = meetingNotificationService.createCancelMeetingNotificationMessage(user,event);
            if (!eventLogManagementService.cancelNotificationSentToUser(event.getEventObject(),user)
                    && !eventLogManagementService.userRsvpNoForEvent(event.getEventObject(),user)) {
                log.info("sendCancelledEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
                messageSendingService.sendMessage(message, user.getPhoneNumber(), MessageProtocol.SMS);
                eventLogManagementService.createEventLog(EventLogType.EventCancelled,event.getEventObject(),user,message);
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
                sendNewMeetingMessage(newGroupMember.getNewMember(),new EventDTO(upComingEvent));
            }
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
        String message = meetingNotificationService.createMeetingNotificationMessage(user,event);
        if (!eventLogManagementService.notificationSentToUser(event.getEventObject(),user)) {
            log.info("sendNewEventNotifications...send message..." + message + "...to..." + user.getPhoneNumber());
            messageSendingService.sendMessage(message,user.getPhoneNumber(), MessageProtocol.SMS);
            eventLogManagementService.createEventLog(EventLogType.EventNotification,event.getEventObject(),user,message);
        }

    }


}

