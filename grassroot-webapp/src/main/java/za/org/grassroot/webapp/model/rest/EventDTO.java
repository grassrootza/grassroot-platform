package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Set;

/**
 * Created by aakilomar on 9/6/15.
 */
public class EventDTO extends TaskDTO {

    private String long_description;
    private boolean isCancelled;
    private boolean notify;
    private Integer reminder;
    private Set<Permission> permissions;
    private static final String filterString = "CREATE";

    public EventDTO(Event event, EventLog eventLog, User user, boolean hasResponded) {
        super(event, eventLog, user, hasResponded);
        this.isCancelled = event.isCanceled();
        this.notify = event.isRelayable();
        this.reminder = event.getReminderMinutes();
        this.permissions = RestUtil.filterPermissions(event.getAppliesToGroup().
                getMembership(user).getRole().getPermissions(), filterString);
    }


    public String getLong_description() {
        return long_description;
    }

    public void setLong_description(String long_description) {
        this.long_description = long_description;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public boolean isNotify() {
        return notify;
    }

    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    public Integer getReminder() {
        return reminder;
    }

    public void setReminder(Integer reminder) {
        this.reminder = reminder;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
