package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;

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
    protected boolean rsvpRequired;
    protected boolean relayable;

    protected EventReminderType reminderType;
    protected int customReminderMinutes;

    protected Set<String> assignedMembers;

    protected EventWrapper() {

    }

    public EventWrapper(Event event) {
        this.entityUid = event.getUid();

        this.parentEntityType = event.getParent().getJpaEntityType();
        this.parentUid = event.getParent().getUid();

        switch (event.getParent().getJpaEntityType()) {
            case GROUP:
                Group parent = (Group) event.getParent();
                this.parentName = parent.getName("");
                break;
            case MEETING:
                this.parentName = ((Meeting) event.getParent()).getName();
                break;
            case VOTE:
                this.parentName = ((Vote) event.getParent()).getName();
                break;
            case TODO:
                this.parentName = ((Todo) event.getParent()).getMessage();
                break;
        }

        this.title = event.getName();
        this.description = event.getDescription();
        this.eventDateTime = DateTimeUtil.convertToUserTimeZone(event.getEventStartDateTime(), DateTimeUtil.getSAST())
                .toLocalDateTime();
        this.includeSubGroups = event.isIncludeSubGroups();
        this.rsvpRequired = event.isRsvpRequired();
        this.relayable = event.isRelayable();

        this.reminderType = event.getReminderType();
        this.customReminderMinutes = event.getCustomReminderMinutes();

        // todo: assign members ... also, set custom reminder minute (and reminder settings) to be same as parent
    }

    public static EventWrapper makeEmpty(boolean rsvpRequired) {
        EventWrapper eventWrapper = new EventWrapper();
        eventWrapper.rsvpRequired = rsvpRequired;
        return eventWrapper;
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

    public boolean isRsvpRequired() {
        return rsvpRequired;
    }

    public void setRsvpRequired(boolean rsvpRequired) {
        this.rsvpRequired = rsvpRequired;
    }

    public boolean isRelayable() {
        return relayable;
    }

    public void setRelayable(boolean relayable) {
        this.relayable = relayable;
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

    public void setCustomReminderMinutes(int customReminderMinutes) {
        this.customReminderMinutes = customReminderMinutes;
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
