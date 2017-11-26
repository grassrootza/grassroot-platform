package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

public interface EventLogBroker {

    EventLog createEventLog(EventLogType eventLogType, String eventUid, String userUid, EventRSVPResponse message);

    void rsvpForEvent(String eventUid, String userUid, EventRSVPResponse rsvpResponse);

    boolean hasUserRespondedToEvent(Event event, User user);

    EventLog findUserResponseIfExists(String userUid, String eventUid);

    ResponseTotalsDTO getResponseCountForEvent(Event event);
}
