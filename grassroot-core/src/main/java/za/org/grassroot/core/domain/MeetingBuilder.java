package za.org.grassroot.core.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.enums.MeetingImportance;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public class MeetingBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MeetingBuilder.class);

    private String name;
    private Instant startDateTime;
    private User user;
    private MeetingContainer parent;
    private String eventLocation;
    private boolean includeSubGroups = false;
    private EventReminderType reminderType;
    private Integer customReminderMinutes = 0;
    private String description;
    private MeetingImportance importance;

    private Set<String> assignedMemberUids;

    public MeetingBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public MeetingBuilder setStartDateTime(Instant startDateTime) {
        this.startDateTime = startDateTime;
        return this;
    }

    public MeetingBuilder setUser(User user) {
        this.user = user;
        return this;
    }

    public MeetingBuilder setParent(MeetingContainer parent) {
        this.parent = parent;
        return this;
    }

    public MeetingBuilder setEventLocation(String eventLocation) {
        this.eventLocation = eventLocation;
        return this;
    }

    public MeetingBuilder setIncludeSubGroups(boolean includeSubGroups) {
        this.includeSubGroups = includeSubGroups;
        return this;
    }

    public MeetingBuilder setReminderType(EventReminderType reminderType) {
        this.reminderType = reminderType;
        return this;
    }

    public MeetingBuilder setCustomReminderMinutes(Integer customReminderMinutes) {
        this.customReminderMinutes = customReminderMinutes;
        return this;
    }

    public MeetingBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public MeetingBuilder setImportance(MeetingImportance importance) {
        this.importance = importance;
        return this;
    }

    public MeetingBuilder setAssignedMemberUids(Set<String> assignedMemberUids) {
        this.assignedMemberUids = assignedMemberUids;
        return this;
    }

    public void validateMeetingFields() {
        Objects.requireNonNull(user);
        Objects.requireNonNull(parent);
    }

    public Meeting createMeeting() {
        logger.debug("creating a meeting in builder, starting ... type = {}", reminderType);
        validateMeetingFields();
        Meeting mtg = new Meeting(name, startDateTime, user, parent, eventLocation);
        mtg.setReminderType(reminderType == null ? EventReminderType.DISABLED : reminderType);
        mtg.setIncludeSubGroups(includeSubGroups);
        mtg.setCustomReminderMinutes(customReminderMinutes == null ? 0 : customReminderMinutes);
        mtg.setDescription(description);
        mtg.setImportance(importance == null ? MeetingImportance.ORDINARY : importance);
        logger.debug("creating meeting from builder, reminder type = {}", reminderType);
        mtg.updateScheduledReminderTime();

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            assignedMemberUids.add(user.getUid()); // enforces creating user in meeting
            mtg.assignMembers(assignedMemberUids);
        }

        return mtg;
    }
}