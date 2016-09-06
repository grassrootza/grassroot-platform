package za.org.grassroot.core.enums;

/**
 * Created by paballo on 2016/08/12.
 */
public enum MeetingImportance {

    ORDINARY(1),
    SPECIAL(2);

    private final int priority;

    MeetingImportance(final int priority) { this.priority = priority; }

	public int getPriority() { return priority; }

	@Override
	public String toString() { return name(); }

}
