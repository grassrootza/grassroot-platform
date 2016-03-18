package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.dto.RSVPTotalsPerGroupDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface EventLogManagementService {

    EventLog createEventLog(EventLogType eventLogType, Event event, User user, String message);

    boolean notificationSentToUser(Event event, User user);

    boolean voteResultSentToUser(Event event, User user);

    boolean changeNotificationSentToUser(Event event, User user, String message);

    boolean cancelNotificationSentToUser(Event event, User user);

    boolean reminderSentToUser(Event event, User user);

    void rsvpForEvent(Long eventId, Long userId, String strRsvpResponse);

    void rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse);

    EventLog getEventLogOfUser(Event event, User user);

    boolean userRsvpNoForEvent(Event event, User user);

    boolean userRsvpForEvent(Event event, User user);

    RSVPTotalsDTO getRSVPTotalsForEvent(Long eventId);

    RSVPTotalsDTO getRSVPTotalsForEvent(Event event);

    RSVPTotalsDTO getVoteResultsForEvent(Event event);

    List<RSVPTotalsPerGroupDTO> getVoteTotalsPerGroup(Long startingGroup, Long event);

    public List<EventLog> getNonRSVPEventLogsForEvent(Event event);

    int countNonRSVPEventLogsForEvent(Event event);
}
