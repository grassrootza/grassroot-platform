package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface EventLogManagementService {

    public EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message);

    public boolean notificationSentToUser(Event event, User user);

    public boolean changeNotificationSentToUser(Event event, User user, String message);

    public boolean cancelNotificationSentToUser(Event event, User user);

    public boolean reminderSentToUser(Event event, User user);

    public List<EventLog> getMinutesForEvent(Event event);

}
