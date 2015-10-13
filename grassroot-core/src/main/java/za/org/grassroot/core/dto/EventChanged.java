package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Event;

import java.io.Serializable;

/**
 * Created by aakilomar on 10/7/15.
 */
public class EventChanged implements Serializable {

    private Event event;
    private boolean startTimeChanged;

    public EventChanged(Event event, boolean startTimeChanged) {
        this.event = event;
        this.startTimeChanged = startTimeChanged;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public boolean isStartTimeChanged() {
        return startTimeChanged;
    }

    public void setStartTimeChanged(boolean startTimeChanged) {
        this.startTimeChanged = startTimeChanged;
    }

    @Override
    public String toString() {
        return "EventChanged{" +
                "event=" + event +
                ", startTimeChanged=" + startTimeChanged +
                '}';
    }
}
