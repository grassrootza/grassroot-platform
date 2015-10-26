package za.org.grassroot.core.enums;

/**
 * Created by aakilomar on 8/31/15.
 */
public enum EventChangeType {

    EVENT_ADDED("event-added"),
    EVENT_CHANGED("event-changed"),
    EVENT_CANCELLED("event-cancelled"),
    USER_ADDED("user-added"),
    EVENT_REMINDER("event-reminder"),
    VOTE_RESULT("vote-results");

    private final String text;

    private EventChangeType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
