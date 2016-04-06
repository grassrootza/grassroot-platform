package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by luke on 2016/04/06.
 */
public class MeetingWrapper {

    private String eventUid;
    private String parentUid;
    private JpaEntityType parentEntityType;

    private String title;
    private String description;
    private LocalDateTime meetingDateTime;

    private String location;
    private boolean includeSubgroups;
    private boolean rsvpRequired;
    private boolean relayable;

    private EventReminderType reminderType;
    private int customReminderMinutes;

    private Set<String> assignedMembers;

    private MeetingWrapper() {

    }

    public MeetingWrapper(Meeting meeting) {
        this.eventUid = meeting.getUid();
        this.parentEntityType = meeting.getParent().getJpaEntityType();
        this.parentUid = meeting.getParent().getUid();
        this.title = meeting.getName();
        this.description = meeting.getDescription();
        this.meetingDateTime = DateTimeUtil.convertToUserTimeZone(meeting.getEventStartDateTime(), DateTimeUtil.getSAST())
                .toLocalDateTime();
        this.location = meeting.getEventLocation();
        this.includeSubgroups = meeting.isIncludeSubGroups();
        this.rsvpRequired = meeting.isRsvpRequired();
        this.relayable = meeting.isRelayable();

        this.reminderType = meeting.getReminderType();
        this.customReminderMinutes = meeting.getCustomReminderMinutes();

        // todo: assign members ...

    }

    public static MeetingWrapper makeEmpty(EventReminderType reminderType, int customReminderMinutes, boolean rsvpRequired) {
        MeetingWrapper meetingWrapper = new MeetingWrapper();
        meetingWrapper.reminderType = reminderType;
        meetingWrapper.customReminderMinutes = customReminderMinutes;
        meetingWrapper.rsvpRequired = rsvpRequired;
        return meetingWrapper;
    }

    public String getEventUid() {
        return eventUid;
    }

    public void setEventUid(String eventUid) {
        this.eventUid = eventUid;
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

    public LocalDateTime getMeetingDateTime() {
        return meetingDateTime;
    }

    public void setMeetingDateTime(LocalDateTime meetingDateTime) {
        this.meetingDateTime = meetingDateTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isIncludeSubgroups() {
        return includeSubgroups;
    }

    public void setIncludeSubgroups(boolean includeSubgroups) {
        this.includeSubgroups = includeSubgroups;
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
        sb.append("eventUid='").append(eventUid).append('\'');
        sb.append("parentUid='").append(parentUid).append('\'');
        sb.append("parentType='").append(parentEntityType).append('\'');
        sb.append("title='").append(title).append('\'');
        sb.append("description='").append(description).append('\'');
        sb.append("datetime='").append(meetingDateTime).append('\'');
        sb.append("location='").append(location).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
