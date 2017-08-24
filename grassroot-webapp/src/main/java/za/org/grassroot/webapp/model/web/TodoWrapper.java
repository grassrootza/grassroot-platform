package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.JpaEntityType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Created by luke on 2016/03/26.
 */
public class TodoWrapper {

    private JpaEntityType parentEntityType;
    private String parentUid;
    private String parentName;

    private String message;
    private String description;

    private LocalDateTime actionByDate;

    private EventReminderType reminderType;
    private int reminderMinutes;
    private LocalDateTime scheduledReminderTime;

    private String assignmentType;
    private MemberPicker memberPicker;

    private boolean replicateToSubGroups;

    public TodoWrapper() {

    }

    public TodoWrapper(JpaEntityType parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public TodoWrapper(JpaEntityType parentEntityType, String parentUid, String parentName) {
        this.parentEntityType = parentEntityType;
        this.parentUid = parentUid;
        this.parentName = parentName;
    }

    public JpaEntityType getParentEntityType() {
        return parentEntityType;
    }

    public void setParentEntityType(JpaEntityType parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(String parentUid) {
        this.parentUid = parentUid;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getActionByDate() {
        return actionByDate;
    }

    public void setActionByDate(LocalDateTime actionByDate) {
        this.actionByDate = actionByDate;
    }

    public EventReminderType getReminderType() { return reminderType; }

    public void setReminderType(EventReminderType reminderType) { this.reminderType = reminderType; }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
        if (actionByDate != null) calculateScheduledReminderTime();
    }

    public LocalDateTime getScheduledReminderTime() {
        if (scheduledReminderTime == null) calculateScheduledReminderTime();
        return scheduledReminderTime;
    }

    public void calculateScheduledReminderTime() {
        Objects.requireNonNull(actionByDate);
        scheduledReminderTime = actionByDate.plusMinutes(reminderMinutes);
    }

    public void setScheduledReminderTime(LocalDateTime scheduledReminderTime) {
        this.scheduledReminderTime = scheduledReminderTime;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    public boolean isReplicateToSubGroups() {
        return replicateToSubGroups;
    }

    public void setReplicateToSubGroups(boolean replicateToSubGroups) {
        this.replicateToSubGroups = replicateToSubGroups;
    }

    public MemberPicker getMemberPicker() {
        return memberPicker;
    }

    public void setMemberPicker(MemberPicker memberPicker) {
        this.memberPicker = memberPicker;
    }

    @Override
    public String toString() {
        return "TodoWrapper{" +
                "parentEntityType=" + parentEntityType +
                ", parentName='" + parentName + '\'' +
                ", message='" + message + '\'' +
                ", reminderType=" + reminderType +
                ", memberPickerUids=" + memberPicker.getSelectedUids() +
                '}';
    }
}
