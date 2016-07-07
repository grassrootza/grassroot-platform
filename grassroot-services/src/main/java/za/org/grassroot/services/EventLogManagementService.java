package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface EventLogManagementService {

    EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, String message);

    void rsvpForEvent(Long eventId, Long userId, String strRsvpResponse);

    void rsvpForEvent(Long eventId, Long userId, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Long eventId, String phoneNumber, EventRSVPResponse rsvpResponse);

    void rsvpForEvent(Event event, User user, EventRSVPResponse rsvpResponse);

    boolean userRsvpForEvent(Event event, User user);

    ResponseTotalsDTO getResponseCountForEvent(Event event);

    ResponseTotalsDTO getVoteResultsForEvent(Event event);
}
