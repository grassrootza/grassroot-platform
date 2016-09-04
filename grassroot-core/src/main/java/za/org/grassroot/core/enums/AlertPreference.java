package za.org.grassroot.core.enums;

/**
 * Created by Luke on 2016/08/30.
 */
public enum AlertPreference {

    NOTIFY_EVERYTHING(0),
    NOTIFY_NEW_AND_REMINDERS(1),
    NOTIFY_ONLY_NEW(2),
    NOTIFY_ONLY_URGENT(3);

    private final int priority;

    AlertPreference(final int priority) { this.priority = priority; }

    public int getPriority() { return priority; }

    @Override
    public String toString() { return name(); }

}