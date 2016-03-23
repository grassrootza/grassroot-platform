package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.webapp.model.rest.TaskDTO;

import java.time.Instant;
import java.util.Map;

/**
 * Created by paballo.
 */
public class EventWrapper extends TaskDTO {

    private boolean isCancelled;
    private boolean canEdit;
    private RSVPTotalsDTO totals;

    public EventWrapper(Event event, EventLog eventLog, User user, boolean hasResponded, RSVPTotalsDTO totals) {
        super(event, eventLog, user, hasResponded);
        this.isCancelled = event.isCanceled();
        this.canEdit = getCanEdit(event,user);
        this.totals = totals;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    private boolean getCanEdit(Event event, User user) {
        Role role = event.getAppliesToGroup().getMembership(user).getRole();
        if (event.getEventType().equals(EventType.MEETING)
                && event.getEventStartDateTime().toInstant().isAfter(Instant.now())) {
            return role.getPermissions().contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        }
        return (role.getPermissions().contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)
                && event.getEventStartDateTime().toInstant().isAfter(Instant.now()));
    }

    public RSVPTotalsDTO getTotals() {
        return totals;
    }
}
