package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.Date;

/**
 * Created by aakilomar on 9/6/15.
 */
public class EventDTO {

    private Long id;
    private String name;
    private String location;
    private Date date;
    private boolean cancelled;
    private String rsvp;

    public EventDTO(Event event) {
        this.id = event.getId();
        this.name = event.getName();
        this.location = event instanceof Meeting ? ((Meeting) event).getEventLocation() : null;
        this.date = event.getEventStartDateTime();
        this.cancelled = event.isCanceled();
    }

    public EventDTO(EventLog eventLog) {
        this.id = eventLog.getEvent().getId();
        this.name = eventLog.getEvent().getName();
        this.location = eventLog.getEvent() instanceof Meeting ? ((Meeting) eventLog.getEvent()).getEventLocation() : null;
        this.date = eventLog.getEvent().getEventStartDateTime();
        this.cancelled = eventLog.getEvent().isCanceled();
        if (eventLog.getEventLogType() == EventLogType.EventRSVP) {
            this.rsvp = eventLog.getMessage();
        } else {
            this.rsvp = EventRSVPResponse.NO_RESPONSE.toString();
        }
    }
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String toString() {
        return "EventDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", location='" + location + '\'' +
                ", date=" + date +
                ", cancelled=" + cancelled +
                '}';
    }
}
