package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.RSVPTotalsDTO;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by luke on 2016/01/20.
 */
public class EventWrapper {

    private static final Logger log = LoggerFactory.getLogger(EventWrapper.class);

    Event event;
    Group group;

    RSVPTotalsDTO eventResponses;
    List<EventLog> eventLogs;

    public EventWrapper(Event event, RSVPTotalsDTO eventResponses, List<EventLog> eventLogs) {
        this.event = event;
        this.group = event.getAppliesToGroup();
        this.eventResponses = eventResponses;
        this.eventLogs = eventLogs;
    }

    public Event getEvent() {
        return event;
    }

    public Group getGroup() {
        return group;
    }

    public RSVPTotalsDTO getEventResponses() {
        return eventResponses;
    }

    public List<EventLog> getEventLogs() {
        return eventLogs;
    }

    public String getEventDescription() { return event.getName(); }

    public Timestamp getEventDateTime() { return event.getEventStartDateTime(); }

    public int getNumberYesResponses() { return eventResponses.getYes(); }

    // todo: possibly change this to a count of past votes
    public double getPercentageYesResponses() { return (100 * (double) eventResponses.getYes() / (double) eventResponses.getNumberOfUsers()); }

    public String getCreatingUserName() { return event.getCreatedByUser().nameToDisplay(); }

    public int getNumberMessages() { return eventLogs.size(); }

}
