package za.org.grassroot.services.task;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.MeetingContainer;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.exception.TaskNameTooLongException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

public class MeetingBuilderHelper {

    private static final Logger logger = LoggerFactory.getLogger(MeetingBuilderHelper.class);

    @Getter private String userUid;
    @Getter private String parentUid;
    @Getter private JpaEntityType parentType;

    @Getter private String name;
    private LocalDateTime startDateTime;
    private Instant startDateTimeInstant;

    private String eventLocation;
    private boolean includeSubGroups = false;
    private EventReminderType reminderType;
    private Integer customReminderMinutes = 0;
    private String description;
    private MeetingImportance importance;

    private Set<String> assignedMemberUids;
    @Getter private String taskImageKey;

    private boolean isPublic = false;
    private GeoLocation userLocation;

    public MeetingBuilderHelper name(String name) {
        this.name = name;
        return this;
    }

    public MeetingBuilderHelper startDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
        return this;
    }

    public MeetingBuilderHelper startDateTimeInstant(Instant startDateTimeInstant) {
        this.startDateTimeInstant = startDateTimeInstant;
        return this;
    }

    public MeetingBuilderHelper userUid(String userUid) {
        this.userUid = userUid;
        return this;
    }

    public MeetingBuilderHelper parentUid(String parentUid) {
        this.parentUid = parentUid;
        return this;
    }

    public MeetingBuilderHelper parentType(JpaEntityType parentType) {
        this.parentType = parentType;
        return this;
    }

    public MeetingBuilderHelper location(String eventLocation) {
        this.eventLocation = eventLocation;
        return this;
    }

    public MeetingBuilderHelper includeSubGroups(boolean includeSubGroups) {
        this.includeSubGroups = includeSubGroups;
        return this;
    }

    public MeetingBuilderHelper reminderType(EventReminderType reminderType) {
        this.reminderType = reminderType;
        return this;
    }

    public MeetingBuilderHelper customReminderMinutes(Integer customReminderMinutes) {
        this.customReminderMinutes = customReminderMinutes;
        return this;
    }

    public MeetingBuilderHelper description(String description) {
        this.description = description;
        return this;
    }

    public MeetingBuilderHelper importance(MeetingImportance importance) {
        this.importance = importance;
        return this;
    }

    public MeetingBuilderHelper assignedMemberUids(Set<String> assignedMemberUids) {
        this.assignedMemberUids = assignedMemberUids;
        return this;
    }

    public MeetingBuilderHelper taskImageKey(String taskImageUrl) {
        this.taskImageKey = taskImageUrl;
        return this;
    }

    public MeetingBuilderHelper isPublic(boolean isPublic) {
        this.isPublic = isPublic;
        return this;
    }

    public MeetingBuilderHelper userLocation(GeoLocation userLocation) {
        this.userLocation = userLocation;
        return this;
    }

    /*
    Couple of custom getters
     */

    public EventReminderType getReminderType() {
        return reminderType == null ? EventReminderType.DISABLED : reminderType;
    }

    public Instant getStartInstant() {
        if (startDateTimeInstant == null) {
            startDateTimeInstant = convertToSystemTime(startDateTime, getSAST());
        }
        return startDateTimeInstant;
    }

    public void validateMeetingFields() {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(parentUid);
        Objects.requireNonNull(parentType);

        Instant eventStartDateTimeInSystem = startDateTimeInstant != null ? startDateTimeInstant :
                convertToSystemTime(startDateTime, getSAST());

        if (!eventStartDateTimeInSystem.isAfter(Instant.now())) {
            throw new EventStartTimeNotInFutureException("Event start time " + eventStartDateTimeInSystem.toString() +
                        " is not in the future");
        }

        if (name.length() > 40) {
            throw new TaskNameTooLongException();
        }
    }

    public MeetingBuilder convertToBuilder(User user, MeetingContainer parent) {
        logger.debug("meeting helper, reminder type = {}", reminderType);
        MeetingBuilder meetingBuilder = new MeetingBuilder()
                .setUser(user)
                .setParent(parent)
                .setName(name)
                .setStartDateTime(getStartInstant())
                .setEventLocation(eventLocation)
                .setIncludeSubGroups(includeSubGroups)
                .setReminderType(reminderType)
                .setCustomReminderMinutes(customReminderMinutes)
                .setDescription(description)
                .setImportance(importance);

        if (assignedMemberUids != null && !assignedMemberUids.isEmpty()) {
            assignedMemberUids.add(user.getUid()); // enforces creating user in meeting
            meetingBuilder.setAssignedMemberUids(assignedMemberUids);
        }

        return meetingBuilder;
    }

    // for testing


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MeetingBuilderHelper helper = (MeetingBuilderHelper) o;

        if (includeSubGroups != helper.includeSubGroups) return false;
        if (userUid != null ? !userUid.equals(helper.userUid) : helper.userUid != null) return false;
        if (parentUid != null ? !parentUid.equals(helper.parentUid) : helper.parentUid != null) return false;
        if (parentType != helper.parentType) return false;
        if (name != null ? !name.equals(helper.name) : helper.name != null) return false;
        if (startDateTime != null ? !startDateTime.equals(helper.startDateTime) : helper.startDateTime != null)
            return false;
        if (eventLocation != null ? !eventLocation.equals(helper.eventLocation) : helper.eventLocation != null)
            return false;
        if (reminderType != helper.reminderType) return false;
        if (customReminderMinutes != null ? !customReminderMinutes.equals(helper.customReminderMinutes) : helper.customReminderMinutes != null)
            return false;
        if (description != null ? !description.equals(helper.description) : helper.description != null) return false;
        if (importance != helper.importance) return false;
        return assignedMemberUids != null ? assignedMemberUids.equals(helper.assignedMemberUids) : helper.assignedMemberUids == null;
    }

    @Override
    public int hashCode() {
        int result = userUid != null ? userUid.hashCode() : 0;
        result = 31 * result + (parentUid != null ? parentUid.hashCode() : 0);
        result = 31 * result + (parentType != null ? parentType.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (startDateTime != null ? startDateTime.hashCode() : 0);
        result = 31 * result + (eventLocation != null ? eventLocation.hashCode() : 0);
        result = 31 * result + (includeSubGroups ? 1 : 0);
        result = 31 * result + (reminderType != null ? reminderType.hashCode() : 0);
        result = 31 * result + (customReminderMinutes != null ? customReminderMinutes.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (importance != null ? importance.hashCode() : 0);
        result = 31 * result + (assignedMemberUids != null ? assignedMemberUids.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MeetingBuilderHelper{" +
                "userUid='" + userUid + '\'' +
                ", parentUid='" + parentUid + '\'' +
                ", parentType=" + parentType +
                ", name='" + name + '\'' +
                ", startDateTime=" + startDateTime +
                ", eventLocation='" + eventLocation + '\'' +
                ", includeSubGroups=" + includeSubGroups +
                ", reminderType=" + reminderType +
                ", customReminderMinutes=" + customReminderMinutes +
                ", description='" + description + '\'' +
                ", importance=" + importance +
                ", assignedMemberUids=" + assignedMemberUids +
                ", taskImageKey='" + taskImageKey + '\'' +
                '}';
    }
}