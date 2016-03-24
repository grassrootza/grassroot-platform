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

    EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, String message);

    boolean notificationSentToUser(Event event, User user);

    boolean voteResultSentToUser(String voteUid, String userUid);

    boolean changeNotificationSentToUser(String eventUid, String userUid, String message);

    boolean cancelNotificationSentToUser(String eventUid, String userUid);

    boolean reminderSentToUser(Event event, User user);

    void rsvpForEvent(Long eventId, Long userId, String strRsvpResponse);

    void rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse);

    EventLog getEventLogOfUser(Event event, User user,EventLogType eventLogType);

    boolean userRsvpNoForEvent(String eventUid, String userUid);

    boolean userRsvpForEvent(Event event, User user);

    RSVPTotalsDTO getRSVPTotalsForEvent(Long eventId);

    RSVPTotalsDTO getRSVPTotalsForEvent(Event event);

    RSVPTotalsDTO getVoteResultsForEvent(Event event);

    List<RSVPTotalsPerGroupDTO> getVoteTotalsPerGroup(Long startingGroup, Long event);

    public List<EventLog> getNonRSVPEventLogsForEvent(Event event);

    int countNonRSVPEventLogsForEvent(Event event);
}
