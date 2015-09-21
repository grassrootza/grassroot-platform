package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

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

    EventLog rsvpForEvent(Long eventId, Long userId, String strRsvpResponse);

    EventLog rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse);

    EventLog rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse);

    EventLog rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse);

    boolean userRsvpNoForEvent(Event event, User user);

    RSVPTotalsDTO getRSVPTotalsForEvent(Long eventId);

    RSVPTotalsDTO getRSVPTotalsForEvent(Event event);
}
