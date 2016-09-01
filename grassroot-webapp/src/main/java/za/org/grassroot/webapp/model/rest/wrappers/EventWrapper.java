package za.org.grassroot.webapp.model.rest.wrappers;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.repository.EventLogRepository;

import java.time.Instant;

/**
 * Created by paballo.
 */
public class EventWrapper extends TaskDTO {

    private boolean isCancelled;
    private boolean canEdit;
    private ResponseTotalsDTO totals;

    public EventWrapper(Event event, User user, ResponseTotalsDTO totals, EventLogRepository eventLogRepository) {
        super(event, user, eventLogRepository);
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
