package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

public interface EventLogBroker {

    EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, EventRSVPResponse message);

    void rsvpForEvent(String eventUid, String userUid, EventRSVPResponse rsvpResponse);

    boolean hasUserRespondedToEvent(Event event, User user);

    ResponseTotalsDTO getResponseCountForEvent(Event event);
}
