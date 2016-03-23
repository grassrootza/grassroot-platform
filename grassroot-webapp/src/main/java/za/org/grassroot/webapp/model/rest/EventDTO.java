package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.enums.EventType;

import java.util.Map;

/**
 * Created by paballo.
 */
public class EventDTO extends TaskDTO {

    private boolean isCancelled;
    private boolean canEdit;
    private Map<String,Integer> totals;

    public EventDTO(Event event, EventLog eventLog, User user, boolean hasResponded, Map<String,Integer> totals) {
        super(event, eventLog, user, hasResponded);
        this.isCancelled = event.isCanceled();
        this.canEdit = getCanEdit(event,user);
        this.totals = totals;
    }

    public boolean isCancelled() { return isCancelled; }

    public boolean isCanEdit() {
        return canEdit;
    }

    private boolean getCanEdit(Event event, User user) {
        Role role = event.getAppliesToGroup().getMembership(user).getRole();
        if (event.getEventType().equals(EventType.MEETING)) {
            return role.getPermissions().contains(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        }
        return role.getPermissions().contains(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
    }

    public Map<String, Integer> getTotals() {
        return totals;
    }
}
