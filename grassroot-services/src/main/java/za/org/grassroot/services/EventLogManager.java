package za.org.grassroot.services;

import org.apache.commons.lang.enums.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;

import javax.jws.soap.SOAPBinding;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/26/15.
 */
@Component
public class EventLogManager implements EventLogManagementService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    EventLogRepository eventLogRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;


    @Override
    public EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message) {
        return eventLogRepository.save(new EventLog(user,event,eventLogType,message));
    }

    @Override
    public boolean notificationSentToUser(Event event, User user) {
        return eventLogRepository.notificationSent(event,user);
    }

    @Override
    public boolean changeNotificationSentToUser(Event event, User user, String message) {
        return eventLogRepository.changeNotificationSent(event,user,message);
    }

    @Override
    public boolean cancelNotificationSentToUser(Event event, User user) {
        return eventLogRepository.cancelNotificationSent(event,user);
    }

    @Override
    public boolean reminderSentToUser(Event event, User user) {
        return eventLogRepository.reminderSent(event,user);
    }

    @Override
    public List<EventLog> getMinutesForEvent(Event event) {
        return eventLogRepository.findByEventLogTypeAndEventOrderByIdAsc(EventLogType.EventMinutes, event);
    }

    @Override
    public EventLog rsvpForEvent(Long eventId, Long userId, String strRsvpResponse) {
        return rsvpForEvent(eventId, userId, EventRSVPResponse.fromString(strRsvpResponse));
    }

    @Override
    public EventLog rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse) {
        return rsvpForEvent(eventRepository.findOne(eventId), userRepository.findOne(userId), rsvpResponse);
    }
    @Override
    public EventLog rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse) {
        return rsvpForEvent(eventRepository.findOne(eventId), userRepository.findByPhoneNumber(phoneNumber).get(0), rsvpResponse);
    }

    @Override
    public EventLog rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse) {
        return createEventLog(EventLogType.EventRSVP, event, user, rsvpResponse.toString());
    }

    @Override
    public boolean userRsvpNoForEvent(Event event, User user) {
        return eventLogRepository.rsvpNoForEvent(event,user);
    }

}
