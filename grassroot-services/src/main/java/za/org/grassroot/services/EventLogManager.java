package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.repository.EventLogRepository;

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

    @Override
    public EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message) {
        return eventLogRepository.save(new EventLog(user,event,eventLogType,message));
    }

    @Override
    public boolean notificationSentToUser(Event event, User user) {
        return eventLogRepository.notificationSent(event,user);
    }

    @Override
    public boolean reminderSentToUser(Event event, User user) {
        return eventLogRepository.reminderSent(event,user);
    }

    @Override
    public List<EventLog> getMinutesForEvent(Event event) {
        return eventLogRepository.findByEventLogTypeAndEventOrderByIdAsc(EventLogType.EventMinutes, event);
    }
}
