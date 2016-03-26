package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.JpaEntityType;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Created by luke on 2016/03/26.
 */
public class LogBookWrapper {

    private JpaEntityType parentEntityType;
    private String parentUid;
    private String parentName;

    private String message;
    private String description;

    private Timestamp actionByDate;

    private int reminderMinutes;
    private Timestamp scheduledReminderTime;

    private String assignmentType;
    private MemberPicker memberPicker;

    private boolean replicateToSubGroups;

    public LogBookWrapper() {

    }

    public LogBookWrapper(JpaEntityType parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public LogBookWrapper(JpaEntityType parentEntityType, String parentUid, String parentName) {
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

    public Timestamp getActionByDate() {
        return actionByDate;
    }

    public void setActionByDate(Timestamp actionByDate) {
        this.actionByDate = actionByDate;
    }

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
        if (actionByDate != null) calculateScheduledReminderTime();
    }

    public Timestamp getScheduledReminderTime() {
        if (scheduledReminderTime == null) calculateScheduledReminderTime();
        return scheduledReminderTime;
    }

    public void calculateScheduledReminderTime() {
        Objects.requireNonNull(actionByDate);
        scheduledReminderTime = Timestamp.valueOf(actionByDate.toLocalDateTime().minusMinutes(reminderMinutes));
    }

    public void setScheduledReminderTime(Timestamp scheduledReminderTime) {
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
}
