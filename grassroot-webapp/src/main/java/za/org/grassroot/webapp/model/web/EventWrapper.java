package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.JpaEntityType;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Created by luke on 2016/04/08.
 */
public class EventWrapper {

    protected String entityUid;
    protected String parentUid;
    protected JpaEntityType parentEntityType;
    protected String parentName;

    protected String title;
    protected String description;
    protected LocalDateTime eventDateTime;

    protected boolean includeSubGroups;

    protected EventReminderType reminderType;
    protected int customReminderMinutes;
    protected int defaultReminderMinutes;

    private String assignmentType;
    private MemberPicker memberPicker;

    protected Set<String> assignedMembers;

    protected EventWrapper() {
    }

    public static EventWrapper makeEmpty() {
        return new EventWrapper();
    }

    public String getEntityUid() {
        return entityUid;
    }

    public void setEntityUid(String entityUid) {
        this.entityUid = entityUid;
    }

    public String getParentUid() {
        return parentUid;
    }

    public void setParentUid(String parentUid) {
        this.parentUid = parentUid;
    }

    public JpaEntityType getParentEntityType() {
        return parentEntityType;
    }

    public void setParentEntityType(JpaEntityType parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public String getParentName() { return parentName; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(LocalDateTime eventDateTime) {
        this.eventDateTime = eventDateTime;
    }

    public boolean isIncludeSubGroups() {
        return includeSubGroups;
    }

    public void setIncludeSubGroups(boolean includeSubGroups) {
        this.includeSubGroups = includeSubGroups;
    }

    public EventReminderType getReminderType() {
        return reminderType;
    }

    public void setReminderType(EventReminderType reminderType) {
        this.reminderType = reminderType;
    }

    public int getCustomReminderMinutes() {
        return customReminderMinutes;
    }

    public int getDefaultReminderMinutes() { return defaultReminderMinutes; }

    public void setCustomReminderMinutes(int customReminderMinutes) {
        this.customReminderMinutes = customReminderMinutes;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    public MemberPicker getMemberPicker() {
        return memberPicker;
    }

    public void setMemberPicker(MemberPicker memberPicker) {
        this.memberPicker = memberPicker;
    }

    public Set<String> getAssignedMembers() {
        return assignedMembers;
    }

    public void setAssignedMembers(Set<String> assignedMembers) {
        this.assignedMembers = assignedMembers;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MeetingWrapper{");
        sb.append("entityUid='").append(entityUid).append('\'');
        sb.append("parentUid='").append(parentUid).append('\'');
        sb.append("parentType='").append(parentEntityType).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("description='").append(description).append('\'');
        sb.append("datetime='").append(eventDateTime).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
