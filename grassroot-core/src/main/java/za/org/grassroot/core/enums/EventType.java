package za.org.grassroot.core.enums;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.Vote;

/**
 * Created by luke on 11/9/2015.
 * Enum that will help us distinguish among event types
 */
public enum EventType {
    MEETING(Meeting.class),
    VOTE(Vote.class);

    private final Class<? extends Event> eventClass;

    EventType(Class<? extends Event> eventClass) {
        this.eventClass = eventClass;
    }

    public Class<? extends Event> getEventClass() {
        return eventClass;
    }
}
