package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.dto.TaskDTO;

import java.time.Instant;

/**
 * Created by paballo.
 */
public class EventWrapper extends TaskDTO {

    private boolean isCancelled;
    private boolean canEdit;
    private ResponseTotalsDTO totals;

    public EventWrapper(Event event, EventLog eventLog, User user, boolean hasResponded, ResponseTotalsDTO totals) {
        super(event, eventLog, user, hasResponded);
        this.isCancelled = event.isCanceled();
        this.canEdit = getCanEdit(user,event);
        this.totals = totals;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    private boolean getCanEdit(User user, Event event){
        boolean canEdit = false;
            boolean isOpen = event.getEventStartDateTime().isAfter(Instant.now());
            if (event.getCreatedByUser().equals(user) && isOpen){
                canEdit = true;
            }
        return canEdit;
    }

    public ResponseTotalsDTO getTotals() {
        return totals;
    }
}
